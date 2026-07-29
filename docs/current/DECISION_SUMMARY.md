# Decision summary

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Platform architecture
- Related Source: ADRs and Phase 0–10 handoffs
- Supersedes: none; summarizes still-valid decisions
- Known Limitations: reopen conditions require team approval

| Decision | Reason/trade-off | Current | Reopen when |
|---|---|---|---|
| Java 17 / Spring Boot 4.1 / React | stable team baseline | valid | platform constraint changes |
| Modular monolith | delivery and transaction simplicity | valid | demonstrated scale/team boundary |
| JWT access+refresh | stateless API with revocation store | valid | production cookie/security review |
| Access memory, refresh sessionStorage | reduce access persistence | valid | HttpOnly cookie migration approved |
| Owner mismatch → 404 | avoid resource enumeration | valid | none without security approval |
| DOCX only, 20 MB | bounded parser risk | valid | product requirement and security tests |
| Canonical 12 sections, five statuses | deterministic plan contract | valid | versioned schema migration |
| Durable generic jobs | recovery/idempotency/progress | valid | branching cost exceeds benefit |
| Additive Flyway + validate | protect schema history | valid | never rewrite released migrations |
| TEXT JSON for variable evidence | avoids premature table explosion | accepted | query/schema needs mature |
| Mock/real typed AI adapters | deterministic tests/provider isolation | valid | provider abstraction changes |
| Persist provenance, not raw response | audit with privacy/size boundary | valid | legal retention policy changes |
| Legal safe language | not professional advice | valid | reviewed legal product scope |
| Feasibility fact/assumption/inference | prevent false certainty | valid | never relax silently |
| Versioned 56-person catalog | reproducible recommendation | valid | approved catalog migration |
| Project URL source of truth | direct entry/recovery | valid | none |
| Legacy UI preserved but unrouted | retain design evidence safely | temporary | Phase 11 visual migration completes |
