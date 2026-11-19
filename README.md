# Invoice Agent

A small demo of an e-invoicing pipeline in Spring Boot + Gradle + MariaDB + Spring AI:

```
PDF invoice ──► AI reads it ──► plain Java rules decide ──┬──► VALID ──► XRechnung (UBL) XML
 (text or scan)   (Claude or        (sums, VAT, VAT ID,    │
                  Gemini via        IBAN, required fields) └──► NEEDS_REVIEW ──► human approves / rejects
                  Spring AI)
```

**The idea in one line:** the AI only *reads* the messy PDF; deterministic rules decide what is valid,
so nothing wrong reaches a customer's ERP.

## Run it (about 3 minutes)

Needs Java 21 and Docker (Gradle comes with the wrapper).

```bash
docker compose -f docker/compose/dependencies.yml up -d   # MariaDB 11.4 on localhost:3306
./gradlew bootRun                     # http://localhost:8080
```

Upload the 6 PDFs from `samples/` (multi-select works), then open the ones marked NEEDS_REVIEW.

### Choosing the model

Both models come from the **Spring AI starters** and are configured with the standard `spring.ai.*`
properties in `application.yml`. The extraction code is the same for each; only the `ChatModel` differs.
One model is active per run, picked by `ops.extractor` (env `OPS_EXTRACTOR`, default `replay`):

| `OPS_EXTRACTOR` | Also set before `./gradlew bootRun` |
|---|---|
| `claude` (`claude-opus-5-5`) | `export ANTHROPIC_API_KEY=...` |
| `gemini` via API key (AI Studio) | `export GOOGLE_API_KEY=...` |
| `gemini` via Vertex AI | `export GOOGLE_CLOUD_PROJECT=my-project` and `gcloud auth application-default login` (optional `GOOGLE_CLOUD_LOCATION`, default `global`) |
| `replay` (no AI) | nothing: values from `samples/ground_truth.json`, so the demo works offline |

The Gemini model defaults to `gemini-2.5-flash`; use `export GEMINI_MODEL=...` for another one you have access to.
The page header shows which model is reading.

### Tests

```bash
./gradlew test    # H2 instead of MariaDB, replay extractor, no network
```

- `InvoiceValidatorTest` runs every sample through the rules and expects exactly the errors listed in `ground_truth.json`.
- `PipelineTest` runs all 6 PDFs end to end and checks the routing (3 valid, 3 review) and that the XML parses.
- `OpsConfigTest` boots the app with `ops.extractor` set to replay, claude and gemini, and checks that exactly one
  `InvoiceExtractor` and only the matching `ChatModel` exist (and that Claude's temperature is unset).
- `SpringAiInvoiceExtractorTest` uses a fake `ChatModel` to check the PDF is attached, the JSON format
  instructions are sent, and Claude's thinking blocks are skipped when reading the answer.

## What the samples show

| File | Result | Why |
|---|---|---|
| 01 clean, 19 % | VALID | all rules pass, XRechnung generated |
| 02 clean, 7 % + 19 % | VALID | VAT checked per rate |
| 03 Dutch supplier, 0 % reverse charge | VALID | 0 % is only accepted with a reverse-charge note and the buyer's VAT ID |
| 04 printed total €10 too high | NEEDS_REVIEW | `BR-CO-15` printed total 580.49 != computed 570.49. Can only be rejected, not approved |
| 05 no seller VAT ID, no due date | NEEDS_REVIEW | `BR-CO-26` and `BR-CO-25` |
| 06 blurry rotated scan of 01 | NEEDS_REVIEW | rules pass, but extraction confidence is low, so a human confirms before it goes out |

## Code tour

| Where | What |
|---|---|
| `config/OpsProps` | `ops.*` settings: `extractor` (claude, gemini, replay), `samples-dir`, `min-confidence` |
| `config/AiConfig` | imports the matching Spring AI auto-configuration only for the chosen extractor (`@ConditionalOnProperty` + `@Import`) |
| `config/OpsConfig` | provides the one `InvoiceExtractor` bean, chosen by `ops.extractor`; everything else depends on the interface |
| `ops/extraction/SpringAiInvoiceExtractor` | one extractor for any model: PDF as media, `BeanOutputConverter` for typed JSON. Prompt says: copy, never correct |
| `ops/validation/InvoiceValidator` | All rules, named after EN 16931 / XRechnung business rules where one exists |
| `ops/xrechnung/XRechnungWriter` | UBL 2.1 Invoice with XRechnung 3.0 CustomizationID, amounts recomputed from lines |
| `model/service/InvoiceService` | extract → validate → route, stored in MariaDB (`invoice_document`) |
| `web/InvoiceController` + `templates/` | upload page, review list, approve/reject, XML download |

## Spring AI gotchas worth knowing

- With both starters on the classpath, the default `ChatClient.Builder` bean can't choose between two
  `ChatModel`s, so it's disabled (`spring.ai.chat.client.enabled: false`) and the extractor builds its own `ChatClient`.
- The Anthropic starter defaults `temperature` to 0.8, which current Claude models reject, and an empty YAML
  value doesn't unset it. `AiConfig` clears it with a small `BeanPostProcessor`.
- The Google GenAI starter fails at startup without an API key or project, which is why both
  auto-configurations are excluded in `application.yml` and imported conditionally instead.

## Honest limitations (good things to mention in a conversation)

- A PDF doesn't carry everything XRechnung requires, e.g. the seller's electronic address (BT-34) and
  contact (BT-41..43). The official KoSIT validator will flag those. In a real pipeline they come from
  partner master data, which is exactly what an integrator maintains.
- Only `unitCode="C62"` (pieces) is written; real mappings translate units (kg, hours, m).
- No ZUGFeRD input yet: if a PDF already has embedded XML, it should be read directly instead of asking an AI.
- Next steps would be EDIFACT INVOIC output and sending via AS2 or SFTP.
- A fun extension: run Claude and Gemini on the same invoice and send it to review when they disagree.

Sample invoices are fictional, see `samples/README.md`.
