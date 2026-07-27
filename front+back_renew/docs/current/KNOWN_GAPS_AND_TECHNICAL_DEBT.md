# Known gaps and technical debt

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`

| Gap/debt | Impact | Risk | Next |
|---|---|---|---|
| persisted/shareable report version absent | report reflects latest state | Medium | product decision |
| committed reusable browser E2E absent | reproduction depends on browser tooling | Medium | automation |
| physical screen-reader/device certification absent | accessibility uncertainty | High | operational QA |
| password reset/MFA absent | production identity incomplete | Medium | security |
| refresh token not HttpOnly cookie | XSS exposure | Medium | contract change |
| real AI not live-certified | runtime uncertainty | Medium | controlled certification |
| current legal/market data not integrated | output remains preliminary | High | external data |
| customer response execution absent | plans cannot be completed in app | High | product/privacy |
| no plan reopen | correction needs new workflow | Medium | product decision |
| deterministic document Mock has no missing fields | live FILLED/WAIVED E2E gap | Medium | test fixture |
| legacy UI/schema remains | maintenance noise | Medium | focused cleanup |
| catalog upgrade policy incomplete | reproducibility risk | Medium | before catalog v2 |

Redis, Kafka, microservices, generic workflow engines, and RAG are not justified by current scale.
