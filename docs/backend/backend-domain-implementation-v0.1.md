# Backend Domain Implementation v0.1

## 1. 현재 코드 분석

작업 전 백엔드는 Spring Boot 4.1.0/Java 17, JPA·Validation·H2 기반이며 루트 패키지는 `com.aivle.backend`였다. 공통 시간 엔터티와 `User`, `Project`, `Product`, `FeasibilityAnalysis`, `Persona`, `Simulation`, `DiscussionLog`, `Report`만 있었고 Repository/API/마이그레이션은 없었다. `application.yaml`의 `server.port` 들여쓰기가 잘못되어 있었고, 문자열 상태·역할과 `User.password`가 기준 문서와 충돌했다.

기존 코드는 사용자 작업 전 변경이 없는 깨끗한 `main` 상태였다. 작업은 `feature/backend-domain-foundation` 브랜치에서 수행했다.

## 2. 기존·신규 Entity 매핑

- 기존 Entity는 삭제 후 재작성하지 않고 도메인 패키지로 이동해 의미를 확장했다.
- `Product` → `ProductService`, `Persona` → `PersonaInstance`로 명칭을 구체화했다.
- `FeasibilityAnalysis`, `Simulation`, `DiscussionLog`, `Report`의 기존 핵심 의미는 유지하면서 Enum, Job, 버전 관계를 추가했다.
- 신규 Core는 파일·문서 버전·구조화·누락 필드·공통 Job·법률·분석 세부·재무·페르소나 세그먼트·FGI/FGD·보고서 버전이다.
- 마케팅 3개 Entity는 프로젝트 기준 정의서 v0.2의 MVP 포함 결정을 반영했다.

## 3. 이번 구현 범위

- `BaseEntity`: JPA Auditing, 논리 삭제, 낙관적 잠금.
- 기준 Enum 전체를 문자열 저장 방식으로 적용.
- Aggregate Root Repository와 최소 조회 메서드.
- 프로젝트 생성/목록/상세/수정, Job 상태 조회 API.
- Bean Validation DTO와 Entity 직접 반환 방지.
- 공통 성공·오류 응답 및 안전한 예외 메시지.
- 로컬 파일 저장 계약·구현과 경로 이탈 방지.
- AI 서비스 계약과 고정 상태 Mock.
- H2 호환 Flyway V1/V2.

## 4. 구현하지 않은 범위

JWT/세션 인증, 이메일 인증, 실제 업로드 Controller, 파일 시그니처 검사, S3 저장, 실제 Python AI 호출, FGI·FGD 실행 알고리즘, 보고서 PDF/DOCX 생성, 마케팅 이미지 생성, 관리자 전체 API, 결제는 구현하지 않았다. 인증 경계는 `CurrentUserProvider`로 분리했고 현재 개발 계약은 `X-User-Id` 헤더를 요구한다. 실제 인증 도입 시 구현체만 교체한다.

## 5. 패키지 구조

`common`, `user`, `project`, `file`, `document`, `job`, `analysis/legal`, `analysis/feasibility`, `analysis/financial`, `persona`, `simulation/survey`, `simulation/discussion`, `report`, `marketing`, `integration/ai`로 구성했다. 실제 유스케이스가 없는 빈 Controller/Service는 만들지 않았다.

## 6. 주요 설계 결정

- Long/IDENTITY를 유지해 초기 코드와 호환했다.
- Enum은 모두 `EnumType.STRING`이다.
- 모든 ToOne 관계는 LAZY다.
- 유동 AI 원본은 DB 종속 JSON 타입이 아닌 `TEXT` JSON 스냅샷으로 둔다.
- 문서·프롬프트·보고서는 복합 유니크 키로 버전을 보존한다.
- 프로젝트 논리 삭제는 Repository 메서드에서 명시적으로 제외한다.
- 인증·운영 DB·AI 통신 방식처럼 문서가 “검토 필요”로 둔 항목은 확정하지 않았다.

## 7. DB 마이그레이션

`V1__create_core_tables.sql`은 프로젝트·문서·분석 Core를, `V2__create_simulation_report_tables.sql`은 페르소나·시뮬레이션·보고서·마케팅을 생성한다. 스키마는 Entity와 동일한 FK, 유니크 키, 진행률 범위, 낙관적 잠금 필드를 갖는다. 운영 DB 확정 전이므로 JSON/DB Enum과 공급자 전용 문법을 쓰지 않았다.

## 8. 파일 저장 원칙

DB에는 메타데이터와 체크섬만 저장한다. 로컬 파일은 설정 루트 아래 난수 기반 `storageKey`로 저장하며 정규화된 대상이 루트를 벗어나면 거부한다. 사업계획서 20MB, 이미지 10MB와 확장자 허용 목록은 설정으로 분리했다. MIME·시그니처 검사는 실제 업로드 유스케이스에서 반드시 추가해야 한다.

## 9. AI Client

Spring은 사용자 권한·Job 영속성과 응답 정규화를 담당하고 Python AI 서비스는 파싱·분석·생성을 담당한다. `AiServiceClient`는 시작·조회·취소만 노출하며 Mock은 외부 결과를 만들지 않고 재현 가능한 QUEUED 응답만 반환한다.

## 10. 후속 작업 순서

1. Spring Security 기반 인증 방식 합의 및 `CurrentUserProvider` 교체.
2. 파일 업로드·서명 검증과 S3 호환 구현.
3. 문서 구조화 Job과 실제 AI 계약 테스트.
4. 법률·병렬 타당성·재무 유스케이스.
5. 페르소나 선택, FGI 조사, FGD 토론.
6. 보고서 렌더링·다운로드와 마케팅 생성.
7. 운영 DB 전환, 감사·관측성·관리자 정책.

## 11. 위험·미확정 사항

- Spring Boot 4.1.0/Java 17 조합은 기존 설정을 임의 변경하지 않고 실제 빌드로 확인한다.
- 운영 DB, 인증 방식, HWP 파서, AI REST/Queue, 법률 판정 세부 정책은 팀 합의가 필요하다.
- 개발용 `X-User-Id`는 인증이 아니므로 운영에 사용할 수 없다.
- 기존 DB 데이터가 존재한다면 `password`→`password_hash`, 테이블 명칭 변경을 위한 별도 비파괴 전환 마이그레이션이 필요하다. 현재 저장소에는 기존 마이그레이션이 없어 신규 초기 스키마로 작성했다.
