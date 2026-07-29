# Job technical debt

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Owners: Backend
- Related Source: job entity/repository/runner/services
- Supersedes: Phase-local job risk lists
- Known Limitations: priority reflects current educational deployment

| Debt | Evidence/impact | Rating | Classification | Recommendation |
|---|---|---|---|---|
| Type branching growth | claim/query/failure/recovery/entity all know types | Medium | SAFE_REFACTOR later | typed policy registry when fifth operational type arrives |
| Nullable source FKs | one table stores four source shapes | Medium | CONTRACT_CHANGE_REQUIRED | retain now; evaluate subtype/source-reference model later |
| Result reference is logical | result type/id not one FK | Medium | ACCEPTED | preserve audit/provenance checks |
| Single-node polling orientation | DB queue, no broker | Low | DEFERRED_DEBT | suitable for project scale |
| No cancellation API | queued/running cannot be user-cancelled | Medium | POST_PHASE11_PRODUCT_DECISION | decide UX and atomic state rules first |
| Progress is phase-based | factual milestones, not percent prediction | Low | HEALTHY | preserve; never fake progress |
| Reserved JobTypes | enum includes unimplemented workflows | Low | DOCUMENT_ONLY | document and reject operational queries |

The core has solid claim token, attempt count, heartbeat, backoff, stale recovery, idempotency, source snapshot, audit and tests. Phase 11 also verified that failure-state writes use an independent transaction so an outer rollback does not erase them. A broader refactor would touch a high-risk concurrency surface without a fifth operational use case, so it remains deferred.
