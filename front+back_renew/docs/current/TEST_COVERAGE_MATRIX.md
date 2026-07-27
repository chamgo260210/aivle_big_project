# Test coverage matrix

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`

| Feature | Unit | H2/API | PostgreSQL | Frontend | Browser | OpenAPI |
|---|---|---|---|---|---|---|
| Auth | yes | yes | migration/security | yes | signup/login/return | yes |
| Project | yes | yes | owner/schema | yes | create/404 | yes |
| Document | parser/policy | yes | migration/concurrency | yes | upload/poll | yes |
| Structured plan | mapper/policy | yes | constraints | yes | result/confirm | yes |
| Job core | claim/retry | yes | concurrency | polling | recovery | yes |
| Legal | policy/adapter | yes | V7 | yes | full flow | yes |
| Feasibility | policy/adapter | yes | V8 | yes | full flow | yes |
| Persona | catalog/adapter | yes | V9 | yes | full flow | yes |
| Dashboard/report/export | view model/export | existing APIs | owner regression | 85 Phase 11 additions | full current flow | no new API |
| Marketing/panel | entity only | no flow | reserved schema | placeholder | no | no |

남은 핵심 위험은 reusable browser suite, physical assistive technology/device QA, real-provider certification, missing-field Mock fixture입니다.
