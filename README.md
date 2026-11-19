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

The model comes from whichever Spring AI starter is enabled in `build.gradle.kts`. Keep exactly one:

| Starter | Also set |
|---|---|
| `spring-ai-starter-model-google-genai` (default) | `GOOGLE_API_KEY`, or `GOOGLE_CLOUD_PROJECT` with `gcloud auth application-default login` (Vertex AI) |
| `spring-ai-starter-model-anthropic` | `ANTHROPIC_API_KEY` |

## Test

```bash
./gradlew test
```

The database tests start MariaDB with Testcontainers, so Docker must be running. The tests read the
sample invoices from `../invoice-agent-samples`, or from `OPS_SAMPLES_DIR` if set, and replay
`ground_truth.json` instead of calling an AI.
