# Current ERD

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend/Data
- Related Source: Flyway V1–V9
- Supersedes: external `erDiagram.md` for As-built use
- Known Limitations: legacy/reserved V2 tables are summarized separately

```mermaid
erDiagram
  USERS ||--o{ PROJECTS : owns
  USERS ||--o{ REFRESH_TOKENS : has
  PROJECTS ||--o{ PROJECT_DOCUMENTS : contains
  PROJECT_DOCUMENTS ||--o{ DOCUMENT_VERSIONS : versions
  DOCUMENT_VERSIONS ||--|| STORED_FILES : stores
  PROJECTS ||--o{ STRUCTURED_PLANS : derives
  DOCUMENT_VERSIONS ||--o| STRUCTURED_PLANS : source
  STRUCTURED_PLANS ||--|{ STRUCTURED_PLAN_SECTIONS : sections
  STRUCTURED_PLANS ||--o{ MISSING_FIELDS : gaps
  PROJECTS ||--o{ ANALYSIS_JOBS : schedules
  STRUCTURED_PLANS ||--o{ LEGAL_REVIEWS : reviewed
  LEGAL_REVIEWS ||--o{ LEGAL_FINDINGS : findings
  LEGAL_REVIEWS ||--o{ LEGAL_REVIEW_QUESTIONS : questions
  STRUCTURED_PLANS ||--o{ FEASIBILITY_ASSESSMENTS : assessed
  LEGAL_REVIEWS ||--o{ FEASIBILITY_ASSESSMENTS : constrains
  FEASIBILITY_ASSESSMENTS ||--|{ FEASIBILITY_DIMENSION_RESULTS : dimensions
  FEASIBILITY_ASSESSMENTS ||--o{ FEASIBILITY_VALIDATION_TASKS : tasks
  PROJECTS ||--o{ PERSONA_RECOMMENDATIONS : recommends
  BASELINE_PERSONAS ||--o{ PERSONA_RECOMMENDATION_ITEMS : selected
  PERSONA_RECOMMENDATIONS ||--o{ PERSONA_RECOMMENDATION_ITEMS : ranks
  PERSONA_RECOMMENDATIONS ||--o{ CUSTOMER_HYPOTHESES : hypotheses
  PERSONA_RECOMMENDATIONS ||--o{ CUSTOMER_VALIDATION_PLANS : plans
```

`analysis_jobs` references one of document version, structured plan, legal review, or feasibility assessment according to JobType. Database constraints cover FK/uniqueness; Java invariants enforce the matching source combination.

Legacy V2: persona instance/prompt/project links → simulations → rounds/questions/responses/discussions/insights/predictions; reports → versions/sources/files; marketing materials → assets/variants.
