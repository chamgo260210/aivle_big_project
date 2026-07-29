# Current API catalog

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- OpenAPI: `0.10.0-phase10`

모든 path는 `/api/v1` 아래에 있습니다.

| Methods | Path family | Frontend consumer |
|---|---|---|
| POST | `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout` | auth |
| GET | `/users/me` | auth shell |
| GET, POST | `/projects` | project list/create |
| GET, PATCH | `/projects/{projectId}` | layout/dashboard |
| GET, multipart POST | `/projects/{projectId}/documents` | document |
| GET | `/documents/{documentId}/versions/{versionId}` | document |
| GET | `/jobs/{jobId}` | polling |
| GET | `/projects/{projectId}/jobs/latest?jobType=` | recovery/report |
| GET | `/projects/{projectId}/structured-plans/latest` | structure/report |
| PATCH | `.../structured-plans/{planId}/missing-fields/{fieldId}` | completion |
| POST | `.../structured-plans/{planId}/confirm` | confirmation |
| POST, GET latest | `/projects/{projectId}/legal-reviews` | legal/report |
| POST, GET latest | `/projects/{projectId}/feasibility-assessments` | feasibility/report |
| GET | `/personas/catalog` | personas |
| POST, GET latest | `/projects/{projectId}/persona-recommendations` | personas/report |

오류는 공통 envelope를 사용하고 다른 owner의 resource는 404입니다. Phase 11은 endpoint를 추가하지 않았습니다. Dashboard/report는 위 계약을 병렬 재사용하며 `/reports` API는 존재하지 않습니다.
