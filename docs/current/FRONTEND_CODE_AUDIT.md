# Frontend code audit

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Frontend
- Related Source: 115 JS/JSX/CSS runtime and legacy files
- Supersedes: none
- Known Limitations: manual architecture audit plus lint/tests

| Area | Finding | Rating | Action |
|---|---|---|---|
| Runtime entry | main → app/App only | HEALTHY | keep |
| API | one Fetch client, coordinated refresh | HEALTHY | keep |
| Auth storage | access memory; refresh sessionStorage | ACCEPTABLE_FOR_PROJECT | HttpOnly later |
| Routing | project ID and direct-entry recovery | HEALTHY | keep |
| Analysis state | common polling/state panels | HEALTHY | extend consistently |
| StructuredPlanCompletion | 581 lines, multiple editing/result responsibilities | REFACTOR_RECOMMENDED | split only with focused tests |
| DocumentPages | 336 lines | ACCEPTABLE | monitor |
| Legacy folders | large unrouted duplicate app | CLEANUP_RECOMMENDED | delete after Phase 11 migration sign-off |
| Legacy credentials | prototype users/passwords are hardcoded in unrouted files | SECURITY_RISK (low runtime) | remove with legacy tree; never reuse |
| Placeholders | explicit, no fake API/result | HEALTHY | implement only in approved phase |
| CSS | tokens/shared UI plus feature repetition | ACCEPTABLE | visual QA and gradual consolidation |

No direct `fetch` outside the shared client, no scattered hardcoded API URL, and no result dependence on `localStorage`/`location.state` were found in the current runtime. Lint and all 107 tests pass. Repository-wide scanning does find prototype credentials in the non-runtime legacy tree; this distinction is intentional and recorded rather than suppressed.
