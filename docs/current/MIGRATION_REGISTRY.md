# Migration registry

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend/Data
- Related Source: `backend/src/main/resources/db/migration`, `backend/src/main/java/db/migration`
- Supersedes: Phase-local migration lists
- Known Limitations: hashes must be recalculated if files move; released migrations must not be edited

| Version | Purpose | Main effect | SHA-256 |
|---|---|---|---|
| V1 | core foundation | users/project/document/plan/job/legal/old feasibility/financial | `15011E63…` |
| V2 | simulation/report | persona, simulation, report, marketing tables | `A9B13C…` |
| V3 | document metadata | provenance, section status/evidence, source version/idempotency | `2259C4…` |
| V4 | job controls | attempt/claim/heartbeat/backoff and ordering | `79D79E…` |
| V5 | integrity hardening | active document uniqueness and not-null constraints | `FC16F5EC284E0D204AD58B16E109E916FB5E68899FC6AC59694DE612C86D27D8` |
| V6 | auth/audit/confirm | refresh token, audit, plan confirmation metadata | `AB4682…` |
| V7 | legal slice | job source plan, legal provenance/findings/questions | `E0C15E…` |
| V8 | feasibility slice | assessment, dimension, validation task | `BBAED5…` |
| V9 | persona slice | 56-person catalog and recommendation/hypothesis/plan | `BE9CC82B90DC89738141913D5767F2FD9232EF8E1145F8CF8B891A321AD6D9AD` |

The full hashes recorded by prior phase evidence remain authoritative for V1–V8; this audit rechecked that no V1–V9 file was modified. Fresh H2/PostgreSQL migration and `ddl-auto=validate` pass. V8→V9 upgrade and V1–V8 checksum compatibility are covered by PostgreSQL tests.

Policy: append V10+ only for an approved contract/domain need; never rewrite V1–V9.
