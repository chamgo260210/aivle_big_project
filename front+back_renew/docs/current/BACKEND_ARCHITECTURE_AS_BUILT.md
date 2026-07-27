# Backend architecture as built

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend
- Related Source: `backend/src/main/java/com/aivle/backend`
- Supersedes: backend architecture descriptions through Phase 10
- Known Limitations: legacy entity packages exist without application slices

## Packages and responsibilities

- `auth`, `user`: signup/login/refresh/logout, JWT issue/validation, current user.
- `project`: owner-scoped project commands and queries.
- `file`, `document`: storage, DOCX policy/parser, versions, structured-plan lifecycle.
- `job`: durable queue, claim, execution registry, progress, retry, stale recovery, latest query.
- `analysis.legal`, `analysis.feasibility`: typed vertical slices.
- `persona.catalog`, `persona.recommendation`: immutable baseline catalog and project recommendation.
- `integration.ai`: provider adapters and transport-specific validation.
- `audit`, `config`, `common`: cross-cutting records, configuration, error envelope.
- `simulation`, `report`, `marketing`, old persona/financial entities: schema-mapped legacy/reserved packages, not runtime features.

Controllers accept DTOs and delegate to application services. Entities are not returned directly. Repositories are accessed from services. Open-in-view is disabled, so response mapping happens inside deliberate transaction/query boundaries.

## Execution profiles

The default `local` profile requires a valid JWT secret configuration supplied by its profile/environment. AI and job polling are disabled in the base configuration. PostgreSQL is the operational target; H2 is a fast compatibility test database.

## Extension rule

New vertical slices should add a typed command/query boundary, DTOs, owner-scoped repository query, durable job only when asynchronous, provider port/adapter, additive migration, OpenAPI entry, and both H2/PostgreSQL evidence.
