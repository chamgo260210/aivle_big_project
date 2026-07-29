# Current system overview

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Known Limitations: persisted/shared report, marketing, panel execution, password reset, and settings are not operational

## As-built summary

시스템은 Spring Boot modular monolith와 React SPA입니다. 백엔드는 identity, owner-scoped project data, durable jobs, provenance, validation을 소유합니다. 프론트엔드는 URL의 project ID와 서버 상태를 source of truth로 사용합니다.

주요 흐름:

`signup/login → project/dashboard → DOCX → DOCUMENT_PARSE → structured plan completion/confirm → LEGAL_REVIEW → FEASIBILITY_ANALYSIS → PERSONA_RECOMMENDATION → runtime report/export`

## Runtime boundaries

- Backend: Java 17, Spring Boot 4.1.0, JPA, Flyway, PostgreSQL/H2.
- Frontend: React 19, React Router 7, Vite 8, shared Fetch API client.
- Persistence: Flyway V1–V9, `ddl-auto=validate`.
- Auth: bearer access token in memory, refresh token in session storage.
- API: `/api/v1`, OpenAPI `0.10.0-phase10`.
- Report: frontend parallel aggregation; no report endpoint/entity/job/V10.

AI는 기본 비활성화/Mock입니다. 각 분석은 typed port, Mock/optional OpenAI adapter, model/prompt provenance, durable result와 job recovery를 가집니다.
