# Legacy data model audit

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Owners: Backend/Data
- Related Source: V1, V2, V8, V9 and JPA entities
- Supersedes: none
- Known Limitations: production row counts were not inspected

| Model | Classification | Current runtime | Decision |
|---|---|---|---|
| `feasibility_analyses` + metric/evidence/recommendation | LEGACY_UNUSED | no controller/service | retain released schema; V8 assessment is canonical |
| `financial_analyses` | RESERVED | no controller/service; placeholder route | retain until scope decision |
| V2 persona instances/segments/prompts/project links | LEGACY_UNUSED | V9 baseline catalog is canonical for recommendation | retain; do not mix identities |
| simulations/rounds/FGI/discussion/insight/prediction | RESERVED | no application slice | candidate for later panel execution |
| reports/versions/sources/files | RESERVED | not used by current runtime report | retain released schema; frontend aggregation is canonical for Phase 11 |
| marketing material/assets/variants | RESERVED | no API | later scope |
| V9 baseline/recommendation/hypothesis/validation plan | ACTIVE | persona vertical slice | canonical |

The presence of an entity/table is not evidence of a working feature. No table, column, enum or migration was removed or rewritten. Data retirement requires usage telemetry, backup/restore plan, approved migration and contract decision.
