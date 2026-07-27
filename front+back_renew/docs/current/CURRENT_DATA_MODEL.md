# Current data model

- Status: Current
- Verified HEAD: `bea5d38f15209d488a95cc1d156c17dfead30e1e`
- Verified Date: 2026-07-24
- Owners: Backend/Data
- Related Source: `backend/src/main/resources/db/migration`, JPA entities
- Supersedes: earlier ERD snapshots as an As-built description
- Known Limitations: V2 contains reserved/legacy tables not exposed by current APIs

## Active aggregates

- Identity: `users`, `refresh_tokens`, `audit_events`.
- Project: `projects`, `product_services`.
- Document: `stored_files`, `project_documents`, `document_versions`.
- Plan: `structured_plans`, `structured_plan_sections`, `missing_fields`.
- Work: `analysis_jobs` with nullable, type-specific source FKs.
- Legal: `legal_reviews`, `legal_findings`, `legal_review_questions`.
- Feasibility: `feasibility_assessments`, `feasibility_dimension_results`, `feasibility_validation_tasks`.
- Persona: `baseline_personas`, `persona_recommendations`, items, hypotheses, validation plans and task links.

## Legacy/reserved

`feasibility_analyses` and its metric/evidence/recommendation children predate the active assessment model. V2 persona-instance, simulation, report and marketing tables and `financial_analyses` remain schema-mapped but have no operational controller/application service.

IDs are numeric PKs. Owner scope starts at `projects.owner_id`. Most domain rows carry audit timestamps; selected mutable roots use optimistic versioning. Structured payloads and evidence are stored as TEXT JSON where a stable relational shape was not justified. This is accepted project-scale debt, not a claim of JSON schema enforcement.
