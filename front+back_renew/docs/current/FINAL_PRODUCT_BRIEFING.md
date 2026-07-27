# Final product briefing

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Readiness: `READY_WITH_KNOWN_LIMITATIONS`

## Phase 11 follow-up consistency note

- The Phase 11 baseline-to-HEAD Git diff contains zero deleted files.
- Legacy auth/login files were retained and modified only to remove
  credential-like values and autofill.
- The major end-to-end product flow passed in the browser.
- FILLED and WAIVED were not executed in that browser flow because the
  deterministic document Mock supplies all 12 sections. Existing
  integration/component tests cover those branches.

## 1. 제품 목적

DOCX 사업계획을 근거가 연결된 검증 workflow로 바꾸고, 불확실성을 숨기지 않은 채 다음 행동을 제시합니다.

## 2. 해결하려는 문제

초기 팀은 누락 입력, 가정, 법률 불확실성, 검증되지 않은 시장 주장, 고객 가설을 섞기 쉽습니다. 제품은 이 상태를 분리하고 복구 가능한 흐름으로 연결합니다.

## 3. 전체 사용자 여정

Signup/login → project/dashboard → DOCX → 구조화/보완/확정 → 법률 → 타당성 → 페르소나·검증 계획 → dashboard → report → Markdown/print/PDF.

## 4. Frontend architecture

React 19, React Router 7, Vite 8, feature folders, shared UI/tokens, Fetch API client, URL project identity, server-state recovery입니다. `features/report`가 read model과 export를 소유합니다.

## 5. Backend architecture

Java 17/Spring Boot 4.1 modular monolith입니다. Controller, application service, typed entity/repository, owner scope, security, audit, file, durable job 경계가 있습니다.

## 6. Database

PostgreSQL 17과 H2를 사용하며 Flyway V1–V9는 immutable입니다. Hibernate는 `ddl-auto=validate`이고 Phase 11 migration은 없습니다.

## 7. Job

Document/legal/feasibility/persona job은 claim, progress, retry, recovery, timeout, source reference를 저장합니다. 실패 처리는 독립 transaction에서 안전하게 기록합니다.

## 8. AI

각 분석은 typed port, deterministic Mock, optional OpenAI adapter, provenance, limit와 safe error를 가집니다. AI는 기본 비활성화이고 report AI는 없습니다.

## 9. StructuredPlan

12개 canonical section, evidence, completion, missing field, FILLED/WAIVED, version, immutable confirmation을 제공합니다.

## 10. Legal review

확정 계획에서 category/risk/question/action/evidence를 만들며 항상 “법률 자문 아님” 경계를 유지합니다.

## 11. Feasibility

10 dimensions, fact/assumption/inference, nullable score, verdict, confidence, risk와 validation task를 보존합니다. 시장·재무 사실을 만들지 않습니다.

## 12. Persona

Versioned 56-cluster 한국미디어패널 catalog와 project evidence를 비교합니다. 결과는 가설과 조사 계획이며 실제 고객 응답·구매확률이 아닙니다.

## 13. Dashboard

네 분석 상태, 현재 결과 수, Mock 표시, CTA와 deterministic next action을 보여 줍니다.

## 14. Integrated report

`/projects/:projectId/report`는 기존 API를 병렬 조합합니다. Structured plan, legal, feasibility, persona, validation task, provenance를 보여 주며 저장된 report snapshot은 아닙니다.

## 15. Export

Allowlisted UTF-8 Markdown과 print-friendly HTML을 제공합니다. Browser Save as PDF만 지원하며 server PDF/DOCX/PPTX/email/public share는 없습니다.

## 16. Security

JWT owner scope, cross-owner 404, local CORS allowlist, environment secret, raw response 비저장, safe export와 credential-string cleanup을 적용했습니다.

## 17. Data provenance

Project/document/plan versions, provider/model/prompt/catalog version, result time, evidence와 limitation을 표시합니다. Token/hash/raw prompt/response/path/audit payload는 export하지 않습니다.

## 18. Mock/Real

Mock는 dashboard, analysis, report, export에서 명시합니다. Real adapter는 별도 운영 인증이 필요합니다.

## 19. 테스트

Frontend 192, H2 168, PostgreSQL 19, OpenAPI 0/0, npm vulnerability 0입니다. 실제 browser current flow와 360–1440 responsive/print 검증을 통과했습니다.

## 20. 기준 문서 적용

외부 DOCX 9개와 53-row requirements CSV의 route/state/evidence/disclaimer/responsive/component 원칙을 적용했습니다.

## 21. 기준과 다른 부분

Report는 persisted final artifact가 아니라 current-view입니다. Customer validation은 response execution이 아니라 plan입니다. External market/legal data, marketing, panel, admin analytics는 없습니다.

## 22. 차이 이유

기존 API로 안전한 current view가 가능해 report domain 중복을 피했습니다. 외부 사실과 고객 결과는 데이터·동의·법률·제품 결정 없이 만들 수 없습니다.

## 23. 현재 제한

Persisted/shared report, server PDF, actual customer response, real-provider certification, password reset/MFA, HttpOnly refresh cookie, physical screen-reader certification이 없습니다.

## 24. Demo flow

Mock와 유효 DOCX를 사용하고 disclaimer를 설명합니다. 기본 Mock에 missing field가 없으면 FILLED/WAIVED를 건너뜁니다. Dashboard → report → Markdown → browser PDF로 마무리합니다.

## 25. 후속 개발

Real-provider certification, customer research/privacy, auth hardening, assistive-technology QA를 우선합니다. Persisted report/share는 reproducibility와 distribution requirement 승인 후 결정합니다.
