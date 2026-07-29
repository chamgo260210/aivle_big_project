# Backend code audit

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend
- Related Source: 352 Java source/test files
- Supersedes: none
- Known Limitations: static/manual audit, not formal SAST

| Area | Finding | Rating | Action |
|---|---|---|---|
| Controller boundary | DTO → service; no direct repository/AI | HEALTHY | keep |
| Owner scope | authenticated user and project-scoped queries, foreign owner 404 | HEALTHY | keep tests |
| Transaction/JPA | open-in-view off; deliberate query/entity graphs | HEALTHY | monitor N+1 |
| Job core | strong durability, growing type branches | REFACTOR_RECOMMENDED | defer until next type |
| AI | typed ports/validation/provenance; HTTP mechanics duplicated | CLEANUP_RECOMMENDED | common transport config later |
| File parsing | archive/size/count/inflate/character limits | HEALTHY | retain threat tests |
| Errors | common ErrorCode/envelope | HEALTHY | keep API parity |
| Legacy domains | entity-only packages imply functionality that is not operational | ACCEPTABLE_FOR_PROJECT | mark reserved |
| Large files | parser 489, AnalysisJob 357; integration test 742 | ACCEPTABLE | split tests only when edited |
| Security/config | secret env, allowlisted CORS, no raw AI persistence | HEALTHY | production cookie review later |

No source refactor was justified: the likely candidates cross concurrency, parser hardening, or legacy migration boundaries. This Phase therefore made documentation-only changes.
