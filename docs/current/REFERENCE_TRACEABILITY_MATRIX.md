# Reference traceability matrix

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Reference: external read-only `docs/reference`

외부 DOCX 9개와 53-row requirements CSV는 Phase 10.5까지 직접 검토했습니다. Phase 11은 그 기준과 current As-built를 우선했습니다.

| Source | Status | Application | Difference |
|---|---|---|---|
| Project standard v0.2 | Implemented with limits | full core flow + dashboard/report | marketing/panel deferred |
| Minimum domain/backend v0.1 | Implemented with refactor | project/document/plan/job/provenance | stronger owner scope |
| UI/UX integrated v0.1 | Implemented | URL identity, durable async, no fake progress | current-view report |
| IA v0.1 | Partially implemented | public/auth/project/analysis/report | deferred placeholders remain |
| Component guide v0.1 | Adapted | shared UI + domain states | legacy prototypes retained |
| Design/UX/visual guides | Adapted | mint tokens, hierarchy, focus, disclaimer | no full physical device certification |
| Responsive guide v0.1 | Adapted | 360–1440 browser matrix passed | physical devices remain |
| Legal/feasibility requirements | Adapted | V7/V8 | preliminary, no external fact feed |
| Persona/customer validation | Partially implemented | V9 + validation plans | no response execution |
| Report | Implemented current-view | `/projects/:id/report`, Markdown/print | no persisted version/share |

Reference와 코드가 다르면 runtime code/migrations를 As-built truth로, OpenAPI를 public contract로, reference를 target intent로 구분합니다.
