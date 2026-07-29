# Job architecture and extension guide

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend
- Related Source: `backend/src/main/java/com/aivle/backend/job`
- Supersedes: job handoffs through Phase 10
- Known Limitations: generic core branches on four source/result families

Operational JobTypes are `DOCUMENT_PARSE`, `LEGAL_REVIEW`, `FEASIBILITY_ANALYSIS`, and `PERSONA_RECOMMENDATION`. Other enum values are reserved legacy values and are rejected by latest-query/runners.

Lifecycle:

1. Command validates owner, confirmed inputs/stage, duplicate active job, and idempotency.
2. Transaction persists `AnalysisJob` with an immutable input snapshot/fingerprint and the matching source FK.
3. Polling runner claims eligible jobs with token, worker, heartbeat and attempt metadata.
4. Registry selects a typed `JobExecutor`; context service rehydrates a bounded input.
5. Executor advances factual phases, calls a typed AI port, and persistence service writes result/provenance.
6. Failure classification schedules bounded exponential backoff or terminal failure.
7. Recovery releases stale running jobs; latest/job queries support refresh recovery.

To add a type, change the enum, job factory/invariants, repository claim query, claim filter/source validation, latest-query allowlist, failure/audit mapping, recovery, executor registry, result reference, OpenAPI/frontend mapping, and tests. This breadth is the primary extension cost; do not add a generic workflow framework in Phase 10.5.
