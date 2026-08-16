# New Pipeline Platform

이 저장소는 프로젝트별 기획을 정리하고 외부 분석 모듈과 연결하는 신규 제품 파이프라인을 구현합니다.

1. Idea Brief 작성·확정
2. 사업안(컨셉) 생성과 공식 근거 기반 법률 검토 — 개수는 요청 파라미터 `maxConcepts`(1~5, 기본 5)
3. 컨셉 비교·선택
4. Market Handoff와 외부 시장분석 결과 반영
5. Finalized Planning 생성, BM·재무 및 Persona 외부 모듈 Handoff
6. Marketing Content 생성·수정·확정

⚠ 아래 한도는 **옛 `ConceptFactory` 경로**의 것이고 현행 사업안 모듈(`conceptportfolio`)의 값이 아닙니다
(2026-08-12 확인). `ConceptFactoryLimits` 기준으로도 지금은 Slot 5 · Replacement 2 ·
법률 Redesign 1 → **검사 상한 20**입니다. 사실이나 근거가 부족하면 성공을
가장하지 않고 입력 필요 또는 실패 상태로 종료합니다.

## Runtime

- `frontEnd`: React/Vite Project Shell, 모듈 상태, 작업 센터와 사용자 화면
- `backend`: Spring Boot, PostgreSQL/Flyway, TaskRun/JobEvent, 신규 `pipeline/**`
- `ai`: FastAPI 내부 execution API와 신규 `app/tasks/**`
- `compose.yaml`: PostgreSQL, MinIO, Backend, AI, Frontend 로컬 구성

브라우저는 Spring Backend만 호출합니다. Backend는 상태와 snapshot을 소유하고,
`InternalAiExecutionClient`를 통해 FastAPI의 `POST /internal/v1/ai/executions`를 호출합니다.
AI 서버가 직접 DB 상태를 변경하지 않습니다.

## Database

보존 데이터가 없는 rebuild 환경을 전제로 Flyway migration 은
`backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql` 로 시작하고,
현재 **V21 까지 21개 파일 · 테이블 57개**입니다. 다음 빈 번호는 **V22**입니다.
기존 DB/volume에 대한 in-place upgrade는 지원하지 않으므로 적용 전에 DB를 초기화해야 합니다.

## Runtime contracts

- 사용자가 직접 호출하는 제품 API는 `/api/v3/projects/{projectId}/...` 아래에 있습니다.
- 작업 Event 조회는 `/api/v2/jobs/{jobId}/events`의 SSE와 `?after=<sequence>` JSON polling을 사용합니다.
- Backend Query API가 상태 정본이며 Job Event는 갱신 신호입니다.
- Provider 작업은 Idea Brief, Concept Candidate, Concept Legal Review, Concept Redesign,
  Marketing Content Generation의 다섯 계약으로 제한합니다.
- 실제 Provider 검증은 `python -m app.tools.idea_brief_provider_smoke`,
  `python -m app.tools.concept_factory_provider_smoke`,
  `python -m app.tools.marketing_content_provider_smoke`로 수행합니다.

## Documentation

- 구현 계약: `docs/legacy/rebuild/`
- API 요약: `docs/api/openapi.yaml`
- 최종 구조: `docs/legacy/rebuild/FINAL_REPOSITORY_STRUCTURE.md`
- Entity/Table 목록: `docs/legacy/rebuild/FINAL_ENTITY_TABLE_INVENTORY.md`
- DB baseline: `docs/legacy/rebuild/FINAL_DATABASE_BASELINE.md`
- 현재 실행 단위 검증: `docs/legacy/rebuild/verification/PRODUCT-CUTOVER-CLEANUP_USER_VERIFICATION.md`
