# Current feature inventory

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24

| Feature | Backend | Frontend / route | Persistence / Job | Status |
|---|---|---|---|---|
| Auth/User | `auth`, `user` | `/auth/*` | users, refresh_tokens | Active |
| Project CRUD | `project` | `/projects`, `/projects/:id/overview` | projects | Active |
| Project dashboard | existing latest APIs | `.../overview` | runtime aggregation | Active |
| Document/version | `file`, `document` | `.../documents` | stored/document/version | Active |
| DOCX parsing | parser/AI port | polling UI | `DOCUMENT_PARSE` | Active |
| Structured plan | structure | `.../structure` | plan/section/missing_field | Active |
| Legal review | `analysis.legal` | `.../legal-review` | V7 + job | Active |
| Feasibility | `analysis.feasibility` | `.../feasibility` | V8 + job | Active |
| Persona/validation plan | `persona.*` | `.../personas` | V9 + job | Active |
| Integrated report/export | no endpoint | `.../report` | current-view only | Active |
| Audit | `audit` | no audit UI | audit_events | Active backend |
| OpenAPI | controllers/DTO | API clients | none | Active |
| Financial/marketing/panel | reserved/legacy | placeholders | reserved V2 tables | Deferred |

Report는 저장된 최종본이 아니며 공유·서명·version history를 제공하지 않습니다.
