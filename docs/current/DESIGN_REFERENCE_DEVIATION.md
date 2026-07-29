# Design reference deviation

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Owners: Frontend/Design
- Related Source: nine external UI/UX DOCX references and current CSS
- Supersedes: Phase 4 deviation notes
- Known Limitations: visual board comparison was document/text based, not pixel-diff

| Reference intent | As-built | Deviation | Classification |
|---|---|---|---|
| coherent mint SaaS shell | current AppShell/tokens comply | legacy unrouted screens use older style | SAFE_CLEANUP after migration |
| light/dark support | token foundations exist | full route-by-route dark QA incomplete | POST_PHASE11_QA |
| responsive sidebar/drawer | implemented | browser matrix 360–1440 passed; physical devices remain | ACCEPTABLE_WITH_QA |
| no fake async progress | durable job phases | compliant | HEALTHY |
| evidence/provenance | result panels expose it | presentation varies by domain | ACCEPTABLE |
| report version/export UX | runtime report, Markdown, print/PDF save | no persisted version/share | PRODUCT_DECISION |
| persona cluster vs individual clarity | catalog/recommendation language | panel individual simulation absent | DEFERRED |
| accessibility/44 px | report/dashboard browser checks pass | physical screen-reader and legacy controls remain | POST_PHASE11_QA |
| rich admin/marketing views | legacy demo only | intentionally not wired to fake APIs | DEFERRED |

The implementation favors truthful state and recoverability over visual completeness. No working flow was replaced merely to match a static visual reference.
