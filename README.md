# Invoice Agent

A Spring Boot app that turns PDF invoices into XRechnung (UBL) XML.

```
PDF invoice ──► AI reads it ──► Java rules validate ──┬──► VALID ──► XRechnung XML
                (Claude or Gemini                      │
                 via Spring AI)                        └──► NEEDS_REVIEW ──► human approves / rejects
```

The AI only extracts the data from the PDF. Deterministic rules (totals, VAT, VAT IDs, IBAN, required
fields) decide whether the invoice is valid. Invoices that fail a rule, or that the AI was unsure about,
wait in a review list. Everything is stored in MariaDB.

## Run

Requires Java 21 and Docker.

```bash
docker compose -f docker/compose/dependencies.yaml up -d
./gradlew bootRun
```

Open http://localhost:8080 and upload PDF invoices.

The model is chosen with `OPS_EXTRACTOR`:

| `OPS_EXTRACTOR` | Also set |
|---|---|
| `claude` | `ANTHROPIC_API_KEY` |
| `gemini` | `GOOGLE_API_KEY`, or `GOOGLE_CLOUD_PROJECT` with `gcloud auth application-default login` (Vertex AI) |
| `replay` (default) | nothing; replays `ground_truth.json` from the samples directory without calling an AI |

Example:

```bash
OPS_EXTRACTOR=claude ANTHROPIC_API_KEY=... ./gradlew bootRun
```

## Test

```bash
./gradlew test
```

The replay mode and the tests read the sample invoices from `../invoice-agent-samples`,
or from `OPS_SAMPLES_DIR` if set.
