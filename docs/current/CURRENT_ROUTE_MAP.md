# Current route map

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24

| Route | Guard/layout | Page/state | Status |
|---|---|---|---|
| `/` | Public | Home | Active |
| `/auth/login`, `/auth/signup` | Public-only | auth API | Active |
| `/auth/password-reset` | Public-only | placeholder | Deferred |
| `/dashboard` | Protected/AppShell | project summary | Active |
| `/projects`, `/projects/new` | Protected/AppShell | project list/create | Active |
| `/projects/:projectId/overview` | Protected/Project | integrated dashboard | Active |
| `.../documents` | Protected/Project | document/upload/job | Active |
| `.../structure` | Protected/Project | structured plan | Active |
| `.../legal-review` | Protected/Project | legal result | Active |
| `.../feasibility` | Protected/Project | feasibility | Active |
| `.../personas` | Protected/Project | persona/validation plan | Active |
| `.../report` | Protected/Project | runtime integrated report | Active canonical |
| `.../reports`, `.../reports/:reportId` | Protected/Project | redirect to `.../report` | Compatibility |
| financial/panel/market-validation/marketing/settings | Protected | placeholder | Deferred |
| `*` | any | Not found | Active |

Direct entry, refresh, logout/login return, and cross-owner 404 were verified in Phase 11.
