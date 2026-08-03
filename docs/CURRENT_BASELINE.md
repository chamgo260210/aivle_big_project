# Current Repository Baseline

- Status: CURRENT_BASELINE
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Phase 0 code-verified implementation baseline
- Supersedes: docs/current as-built and audit documents
- Implementation Status: IMPLEMENTED

## Runtime and ownership

현재 저장소는 Spring Boot backend, React/Vite frontend, FastAPI AI Server로 구성된다. Spring이 JPA/Flyway를 통해 RDB를 관리하며 local 또는 S3-compatible Object Storage adapter를 보유한다. Frontend는 Spring API를 호출한다.

인증은 JWT access/refresh token, Spring Security, 사용자·관리자 권한을 구현한다. Project 조회와 업무 resource의 주요 경로는 owner scope를 적용하며 cross-owner 접근을 찾을 수 없음 또는 접근 거부로 처리한다.

## Current workflow

현재 구현은 다음 legacy 연쇄를 포함한다.

`DOCX → DocumentVersion → DOCUMENT_PARSE AnalysisJob → StructuredPlan/12 sections → FILLED·WAIVED/confirm → legal review → feasibility → financial/persona → panel interview/market response → marketing → browser-composed report`

이 흐름은 Target Workflow가 아니다. `ReportPage`는 여러 조회 API를 조립하며, V2의 Report entity와 연결된 저장 가능한 final snapshot이 아니다.

## Current AI boundary

Spring에는 문서 구조화, 법률 검토, 타당성 분석, Persona 추천용 OpenAI 직접 adapter가 있다. 별도의 Spring→FastAPI task contract는 smoke와 marketing banner에 사용된다. FastAPI는 RDB에 접근하지 않지만 presigned URL로 Object Storage를 읽고 쓸 수 있고 mock banner를 로컬 파일로 기록하는 코드가 있다. 두 동작 모두 Target 경계에서는 허용되지 않는다.

## Current persistence and delivery

- Flyway V1~V26이 존재하며 V5와 V10은 Java migration이다.
- `AnalysisJob`은 claim, retry, recovery, idempotency 기반을 제공한다.
- GitHub Actions는 backend, PostgreSQL, frontend, Docker E2E, OpenAPI lint와 보안 scan을 실행한다.
- FastAPI pytest 전용 CI job과 실제 배포 workflow는 없다.

자세한 legacy 제거 범위는 [CURRENT_TO_TARGET_MAPPING](migration/CURRENT_TO_TARGET_MAPPING.md)에서 관리한다.
