# AI prompt and model registry

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend/AI integration
- Related Source: domain policies and AI adapters
- Supersedes: Phase-local prompt lists
- Known Limitations: model is environment-configured and was not live-verified

| Domain | Prompt/catalog version | Provider/model | Mock | Persisted provenance |
|---|---|---|---|---|
| Document structure | request factory policy version and section catalog version | `openai` / `AI_MODEL` | yes | provider, model, prompt/parser versions, raw-result hash |
| Legal review | `legal-review-v1` | configured | yes | prompt version/hash, provider/model, result hash, input snapshot |
| Feasibility | `feasibility-analysis-v1` | configured | yes | prompt version/hash, provider/model, result/input hashes |
| Persona recommendation | `persona-recommendation-v1`; baseline catalog version | configured | yes | prompt/catalog version, hashes, provider/model |

Prompt text lives with domain policy because its schema and evidence rules are domain contracts. Secrets and real model names live in environment variables, not source. Provider output is not logged or persisted raw.

Before changing a prompt: version it, keep old result readability, add malformed/unknown/duplicate/size tests, update provenance documentation, and decide whether re-running produces a new result version. A model-only environment change must still be observable through persisted model metadata.
