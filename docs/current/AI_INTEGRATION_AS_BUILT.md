# AI integration as built

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend/AI integration
- Related Source: `backend/src/main/java/com/aivle/backend/integration/ai`
- Supersedes: AI integration handoffs through Phase 10
- Known Limitations: real provider was not called in this audit

Four domains expose typed ports: document structure, legal review, feasibility, and persona recommendation. Each has deterministic Mock behavior and an optional OpenAI adapter selected by configuration. AI is disabled by default; tests do not require a secret or network.

Provider boundary controls include required base URL/model/key, connect/read timeouts, retry count, maximum input characters, maximum response bytes, JSON shape validation, duplicate/unknown code rejection, and domain mapping. Results persist provider, model, prompt version, hashes and input snapshot/provenance. Raw provider response content is parsed and discarded rather than stored.

Legal output uses non-authoritative safe language and professional-review flags. Feasibility separates document fact, user assumption, AI inference and legal evidence. Persona recommendations are constrained to the versioned 56-item catalog; invented labels/codes are rejected and original catalog source responses are not claimed.

Common transport behavior is duplicated across adapters, but domain DTOs and validation must remain separate. Consolidating only HTTP/error configuration is a future safe refactor.
