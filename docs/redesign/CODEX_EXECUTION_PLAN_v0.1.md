# AI 사업검증 플랫폼 Codex 실행 계획 및 작업 지시서 v0.1

**적용 저장소**: `C:\Users\seewo\Desktop\big_proj_01\new_2`  
**기준 문서**: `docs/redesign/AI_JOURNEY_REDESIGN_SPEC_v0.3.md`  
**현재 구현 종료점**: Idea 입력·보완 → Idea Origin 확정 → 법제처 기반 Legal Precheck → Legal Guardrail → Concept 생성·검증 → 적격 Concept 3개 표시

---

## 1. 기준 문서를 Codex에 주는 방식

DOCX는 사람 검토용 기준본으로 유지하고, Codex에는 동일 내용을 Markdown으로 제공한다. Codex는 DOCX만 첨부받는 것보다 저장소 안의 Markdown을 검색·대조하기가 쉽다.

권장 배치:

```text
new_2/
└─ docs/
   └─ redesign/
      ├─ AI_JOURNEY_REDESIGN_SPEC_v0.3.md
      └─ CODEX_EXECUTION_PLAN_v0.1.md
```

`AI_JOURNEY_REDESIGN_SPEC_v0.3.md`는 설계 기준이고, 이 문서는 작업 순서와 개별 지시문이다. 매 작업에서 전체 문서를 처음부터 다시 해석시키지 말고, 해당 묶음에서 읽어야 할 절을 지시한다.

### junwoo 법률 구현 참조 준비

법률 묶음을 시작하기 전에 사용자가 `junwoo` 브랜치 소스를 현재 저장소 바깥의 읽기 전용 참조 폴더에 준비한다.

권장 예시:

```text
C:\Users\seewo\Desktop\big_proj_01\reference_aivle_big_project_junwoo
```

Codex에는 이 폴더를 **읽기만 하고 수정하지 말라**고 지시한다. 현재 저장소에 참조 저장소 전체를 복사하거나 하위 모듈처럼 포함하지 않는다.

---

## 2. 전체 진행 방식

작업은 아래 5개 묶음으로 진행한다. 각 묶음은 사용자에게 확인 가능한 흐름 하나를 완성한다.

| 순서 | 작업 묶음 | 결과 |
|---|---|---|
| 1 | Idea Origin·Clarification | 원문 전체 재작성 없이 필수값 보완·확정·복원 |
| 2 | Legal Source Pipeline | Registry·법제처 API·조문 Evidence·AI/Spring 계약 구축 |
| 3 | Legal Precheck UX·Guardrail | 실제 근거 결과 표시, 질문·수정, Concept 진입 차단/허용 |
| 4 | Concept Eligibility Loop | 실패 후보 비노출, 내부 대체 생성, 적격 Concept 3개 표시 |
| 5 | 통합 안정화·선택적 Legacy 정리 | Version/Stale/새로고침 복원, 대체 코드만 제거 |

다음 묶음으로 넘어가는 최소 조건은 전체 테스트 통과가 아니라 **해당 묶음의 사용자 흐름이 브라우저에서 한 번 정상 동작하는 것**이다. 사용자가 최소 빌드·실행을 수행하고 오류 로그가 있으면 별도의 빌드 오류 수정 지시문으로 처리한다.

---

## 3. 모든 작업에 적용하는 공통 지시

아래 공통 지시는 각 작업 지시문 앞에 붙인다.

```text
[공통 작업 규칙]
저장소: C:\Users\seewo\Desktop\big_proj_01\new_2
기준 문서: docs/redesign/AI_JOURNEY_REDESIGN_SPEC_v0.3.md

작업 방식:
- 기준 문서의 이번 작업 관련 절과 현재 코드 계약을 최대 5~10분 안에 확인한 뒤 바로 수정한다.
- 로컬 파일만 수정한다.
- 현재 사용자 흐름을 완성하는 데 필요한 최소한의 Domain/API/AI Schema/UI를 함께 맞춘다.
- 기존 Migration은 수정하지 않고 새 Migration만 추가한다.
- Spring이 업무 데이터, Version, TaskRun, 결과 검증을 소유한다.
- AI Server는 내부 실행과 공식 Source 조회를 담당하고 업무 DB에 직접 접근하지 않는다.
- 기존 Landing, Auth, Admin, 시장 이후 MVP 기능을 임의로 재작성하지 않는다.
- 실제 Provider와 법제처 API 경로를 유지하고 성공을 꾸미는 Fixture/하드코딩 Fallback을 만들지 않는다.
- 이번 작업과 무관한 공통 Framework, 범용 Workflow Engine, 대규모 Rename/Formatting을 만들지 않는다.

금지:
- git status/diff/add/commit/push/branch/PR 등 모든 Git 작업
- docker compose build/up/down 및 장시간 로그 대기
- 전체 Backend/Frontend/AI Test Suite 실행
- 전체 Lint 실행
- 현재 작업과 무관한 Legacy 제거

빌드 실행 예외:
- 이번 지시는 기능 구현 작업이므로 Codex가 Build/Test/Docker를 실행하지 않는다.
- 사용자가 별도로 제공한 실제 빌드 오류를 수정하는 작업에서만 최소 compile/build 명령을 실행할 수 있다.

완료 보고:
1. 구현한 사용자 흐름
2. 핵심 Domain/API/AI Contract 변경
3. 수정·추가 파일 목록
4. 기존 기능 보존 여부
5. 사용자가 실행할 최소 명령
6. 각 명령에서 확인할 내용
7. 브라우저 확인 순서
8. 실행하지 않은 항목
```

---

# 4. 작업 묶음 1 — Idea Origin·Clarification

## 목표

사용자가 자유 입력을 저장한 뒤 AI가 이해한 내용을 구조화하고, 누락 질문에 직접 답해 Idea Origin을 확정한다. 긴 원문 전체를 다시 작성하는 과정을 기본 보완 경로로 사용하지 않는다.

## Codex 지시문

```text
[작업 1 — Idea Origin·Clarification 구현]

공통 작업 규칙을 모두 적용한다.
기준 문서에서 1장, 3장, 4.1~4.3, 5.1~5.2, 10.1, 11.3을 읽고 구현한다.

목표:
- 기존 IdeaSource와 IdeaVersion을 보존하면서, 사용자 확인을 거친 Idea Origin Version과 보완 질문/답변 흐름을 현재 /api/v2 Journey에 구현한다.
- 사용자가 원문 전체를 다시 쓰지 않고 필수 Origin 필드를 채우고 확정할 수 있어야 한다.

먼저 확인할 코드:
- 기존 IdeaSource/IdeaVersion Entity, Repository, Service, Controller, Migration
- 현재 Idea Interpretation TaskRun과 AI 요청/응답 Schema
- Idea 화면, API Hook, 상태 복원 방식, Project Journey Route
- 기존 공용 Form/Card/Alert/Dialog 컴포넌트

구현 범위:
1. Idea Origin Snapshot
- 필수 필드: productServiceDescription, problem, target, solution, coreValue, primaryCategory, targetRegion, fixedValues
- 선택 확정값: pricingIntent, revenueModelIntent, salesChannelIntent, knownUnitCost, alternatives, knownCompetitors, differentiationIntent, internalConstraints 등
- confirmedValues와 assumptions를 분리한다.
- AI가 만든 Draft와 사용자가 확정한 Version을 구분한다.

2. 입력 Metadata
- sourceType, requiredForStages, status, locked, fallbackPolicy를 현재 범위에 필요한 수준으로 저장한다.
- 범용 입력 엔진을 만들지 말고 Idea Origin과 Legal/Concept에 필요한 필드에 적용 가능한 얇은 구조로 구현한다.

3. Clarification
- AI Interpretation 결과에서 누락된 Origin 필드와 법률 사전검토에 필요한 조건부 질문을 구조화한다.
- 질문에는 targetField, requirement, question, reason이 있어야 한다.
- 답변과 사용자가 입력한 확인 출처를 별도로 저장한다.
- 답변을 원문에 자동 삽입하지 않는다.
- 사용자가 '보완 내용 반영'을 실행할 때 Draft + 답변을 합쳐 새 Idea Origin Version을 만든다.

4. Backend/API
- 기존 /api/v2 Project Journey 패턴을 재사용한다.
- 병렬 중복 API를 만들기보다 기존 Idea API를 확장하거나 명확히 대체한다.
- 사용자 소유권 검증과 현재 Version 조회를 유지한다.
- 새로고침 후 현재 Draft, 질문 답변, 확정 Origin을 복원할 수 있어야 한다.

5. AI Contract
- Prompt, Python Pydantic Schema, Java DTO/Validator, 저장 Mapping이 동일한 필드를 사용하게 맞춘다.
- extra field 허용으로 계약 오류를 숨기지 않는다.
- 필수값 누락은 구조화된 question으로 반환한다.

6. Frontend
- '이해한 사업', '필수 보완 질문', '사용자 확정값', 'AI 가정', '진행 준비도'를 구분해 표시한다.
- 기존 디자인 Token과 공용 컴포넌트를 사용한다.
- 원문 편집은 보조 행동으로 남기고, 기본 CTA는 질문 답변과 Origin 확정으로 둔다.
- 준비도는 READY / NEEDS_INPUT / BLOCKED로 표시한다.

이번 작업에서 하지 않을 것:
- 법제처 API 연동
- Legal Guardrail 생성
- Concept 생성
- 시장 이후 단계 연결
- 기존 Idea 코드를 즉시 삭제하는 작업

완료 조건:
- 자유 입력 저장 → 구조화 Draft 확인 → 누락 질문 답변 → 보완 반영 → Idea Origin 확정이 가능하다.
- 새로고침 후 동일한 Origin Version과 답변을 확인할 수 있다.
- AI 가정과 사용자 확정값이 UI와 저장 구조에서 분리되어 있다.

완료 보고에는 사용자가 실행할 Backend compile, Frontend build, 관련 AI contract test의 정확한 최소 명령과 브라우저 검증 순서를 적고 직접 실행하지 않는다.
```

## 사용자 최소 확인

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_2\backend
.\gradlew.bat compileJava

cd ..\frontEnd
npm.cmd run build

cd ..\ai
python -m pytest <Codex가 지정한 Idea contract 테스트 파일> -q

cd ..
docker compose up -d --build ai-server backend frontend
```

브라우저 확인: 프로젝트 생성 → Idea 자유 입력 → 구조화 → 질문 답변 → Origin 확정 → 새로고침 복원.

---

# 5. 작업 묶음 2 — Legal Source Pipeline

## 목표

`junwoo` 브랜치에서 구현한 Registry, Category Rule, 법제처 API, 조문 Normalizer/Selector, 구조화 Evidence 기술을 현재 AI Server와 TaskRun 계약에 맞게 이식한다. 이 묶음에서는 화면보다 Source와 계약의 정확성을 먼저 완성한다.

## Codex 지시문

```text
[작업 2 — Legal Source Pipeline 이식]

공통 작업 규칙을 모두 적용한다.
기준 문서에서 2장, 5.3, 6장, 11.4를 읽는다.
읽기 전용 참조 소스:
C:\Users\seewo\Desktop\big_proj_01\reference_aivle_big_project_junwoo
참조 폴더는 수정하지 않는다.

목표:
- junwoo 구현의 실제 법률 조사 기술을 현재 bp_new_2의 ai-server + Spring TaskRun 구조에 이식한다.
- Entity/Controller를 그대로 복사하지 말고 Registry·법제처 조회·조문 선별·Evidence·Reasoning 계약을 현재 IdeaOriginVersion과 ConceptVersion에 맞춘다.

먼저 확인할 참조 코드:
- reference 저장소의 ai/legal 전체
- law_registry.json, category_map.json, category_rules.json 및 변경 이력
- 법제처 API Client, 법령/조문 Normalizer, Route 판정, Selector, Aggregator, Validator
- Backend LegalPipelineAdapter와 LegalReviewAiRequest/Response
- Evidence와 Reasoning 관련 테스트

현재 저장소에서 확인할 코드:
- ai-server 내부 실행 라우트와 task type registry
- TaskRun/TaskAttempt/TaskResult 실행 계약
- 현재 Legal Precheck AI Schema, Spring DTO/Validator, Migration
- AI Provider 설정과 env example/compose 설정

구현 범위:
1. Versioned Legal Registry
- 규제 Route, Route별 법령, Focus Keyword, Route→Category, 조문 제목→Category Rule을 ai-server 자산으로 추가한다.
- registryVersion을 모든 결과에 남긴다.
- Registry에 없는 분야는 REGISTRY_GAP, 일부 Source만 확인되면 SOURCE_PARTIAL을 반환한다.

2. 법제처 API Client
- 법령 검색, 현행 법령 식별, 시행일, 조문 조회, 공식 URL, 조회 시점을 구조화한다.
- API Key는 AI Server 환경변수로만 받는다.
- Key, 전체 민감 요청, 원문 전체를 로그나 TaskResult에 노출하지 않는다.
- Timeout, Retryable/Non-retryable 오류를 구분한다.
- 필요한 수준의 Cache를 둔다. 업무 DB가 아니라 AI Server의 조회 최적화 범위로 제한한다.

3. Route 판정과 입력 인용 검증
- Idea Origin Snapshot에서 관련 규제 Route, 적용 가능성, 근거 인용, 부족 정보를 추출한다.
- LLM이 반환한 인용문 또는 Statement ID가 실제 Snapshot에 존재하는지 검증한다.
- 검증되지 않은 인용으로 강한 적용 판정을 유지하지 않는다.

4. 조문 Normalizer/Selector
- 조문을 REQUIREMENT / SANCTION / SCOPE / SUPPORTING / EXCLUDE로 분류한다.
- Citation ID가 실제 조회 조문에 존재하는지 검증한다.
- LLM이 법령명과 조문번호를 새로 만들어 Source Truth로 사용하지 못하게 한다.

5. 구조화 Evidence와 5단 Reasoning
- lawName, article, title, role, plainSummary, whyRelevant, excerpt, effectiveDate, lawUrl, verifiedAt를 반환한다.
- Idea 근거 → 규제 영역 → 의무 → 위반 결과 → 필요한 조치 구조를 반환한다.
- AI Server Pydantic과 Spring DTO/Validator가 동일한 Enum과 필수 필드를 사용한다.

6. 두 Task Contract의 기반
- IDEA_LEGAL_PRECHECK
- CONCEPT_LEGAL_VALIDATION
이번 묶음에서는 두 Contract의 공통 Source Pipeline과 Schema를 만들되, Concept 대체 생성 Orchestration은 구현하지 않는다.

7. 설정
- 실제 저장소 변수명에 맞춰 env example, compose, AI 설정 객체를 추가한다.
- 예시 키 값은 넣지 않는다.

이번 작업에서 하지 않을 것:
- Legal 결과 화면의 완성
- 질문 답변/수정 제안 UX
- Concept 생성·대체 루프
- 후속 시장 검색 Provider
- 전체 junwoo FeedbackLoop 복사

완료 조건:
- 지정된 Idea Origin Fixture 또는 단일 요청을 기준으로 Registry → 법제처 Source → 구조화 Evidence/Reasoning 결과 Contract가 만들어진다.
- Python Schema, Spring DTO/Validator, 내부 HTTP Body 필드가 일치한다.
- junwoo에서 발견된 mode/rerunCategories/confirmedFacts 전송 누락과 같은 계약 불일치가 없다.

완료 보고에는 사용자가 실행할 단일 AI contract test, Backend compile 또는 지정 adapter test, 필요한 env 변수와 예상 오류 상태를 적고 직접 실행하지 않는다.
```

---

# 6. 작업 묶음 3 — Legal Precheck UX·Guardrail

## 목표

Idea Origin을 입력으로 실제 Legal Precheck TaskRun을 실행하고, 질문·수정 제안·공식 Evidence·Legal Guardrail을 저장하고 복원한다. Concept Builder 진행 가능 여부를 이 결과로 통제한다.

## Codex 지시문

```text
[작업 3 — Idea Legal Precheck·Guardrail·UI 구현]

공통 작업 규칙을 모두 적용한다.
작업 1과 작업 2의 구현 결과를 기준으로 한다.
기준 문서에서 2.1~2.2, 4.3, 5.2~5.3, 6.2~6.4, 10.2, 11.4를 읽는다.

목표:
- 확정된 Idea Origin Version에서 IDEA_LEGAL_PRECHECK TaskRun을 실행한다.
- 공식 법제처 Evidence, 질문, 수정 제안, Guardrail Version을 Spring이 검증·저장한다.
- Concept Builder 진행 가능 여부를 Legal 상태로 차단하거나 허용한다.

구현 범위:
1. 실행과 소유권
- 확정된 현재 Idea Origin Version만 입력으로 사용한다.
- inputSnapshotHash와 registry/prompt/schema version을 TaskRun 입력에 남긴다.
- 동일 입력의 중복 활성 실행을 방지하고, 완료 결과는 복원한다.
- Frontend 요청은 202 + taskRunId 형태로 시작하고 기존 polling 패턴을 사용한다.

2. Legal 결과 영속화
- Precheck Run/Version, Finding, Evidence, Question, Revision Suggestion, Guardrail Set을 현재 Domain에 맞춰 추가한다.
- 기존 Migration은 수정하지 않고 새 Migration을 추가한다.
- sourceVerified를 임의 true로 저장하지 않고 실제 Source 확인 상태를 반영한다.

3. 상태와 Gate
- PASS
- PASS_WITH_CONDITIONS
- REVISION_REQUIRED
- PROHIBITED
- INSUFFICIENT_INFORMATION
- EXPERT_REVIEW_REQUIRED
Concept Builder 허용 여부를 상태와 필수 질문 해결 여부로 결정한다.
- PASS_WITH_CONDITIONS는 Guardrail로 강제 가능한 경우만 허용한다.
- INSUFFICIENT_INFORMATION, REVISION_REQUIRED, PROHIBITED는 차단한다.

4. 질문·수정
- 필요한 추가 정보는 Clarification Workspace의 구조화 질문으로 연결한다.
- 문제가 되는 Origin 문장/필드와 수정 이유·제안안을 보여준다.
- 수정안은 자동 반영하지 않는다. 사용자 선택 후 새 Idea Origin Version을 만들도록 기존 보완 흐름을 사용한다.

5. Guardrail
- hardConstraints
- prohibitedPatterns
- conditionalConstraints
- requiredDisclosures
- requiredOperationalControls
Guardrail은 Version과 source Legal Run을 갖고, Concept Builder 입력 Snapshot으로 사용할 수 있어야 한다.

6. Frontend
- 종합 상태와 Concept 진행 가능 여부
- Category별 Finding
- 5단 Reasoning
- 법령명·조문·쉬운 설명·관련 이유·시행일·공식 링크
- 질문/수정 제안
- 최종 Guardrail Set
- 실행 중/실패/재시도/새로고침 복원 상태
기존 디자인 시스템을 사용한다.

7. Stale
- Idea Origin 새 Version이 확정되면 기존 Precheck와 Guardrail을 current로 표시하지 않는다.
- 기존 결과를 삭제하지 말고 STALE 또는 입력 Version 불일치로 구분한다.

이번 작업에서 하지 않을 것:
- Concept 생성
- Concept 법률 검증
- 시장 이후 단계 연결
- 기존 Legal 코드를 즉시 삭제

완료 조건:
- Idea Origin 확정 → Precheck 실행 → 실제 Evidence와 Guardrail 저장 → 새로고침 복원이 가능하다.
- 부족 정보는 정확한 질문으로 되돌아간다.
- 차단 상태에서는 Concept 생성 CTA가 활성화되지 않는다.

완료 보고에는 필요한 env, 사용자 실행 명령, 실제 법제처 호출에서 확인할 로그 필드와 브라우저 시나리오를 적고 직접 실행하지 않는다.
```

---

# 7. 작업 묶음 4 — Concept Eligibility Loop

## 목표

Idea Origin과 Legal Guardrail을 입력으로 Concept 초안을 생성하고, Origin Integrity와 Concept Legal Validation을 통과한 Concept만 사용자에게 표시한다. 실패한 자리는 내부에서 대체 생성하며 최종 목표는 적격 Concept 3개다.

## Codex 지시문

```text
[작업 4 — Concept Eligibility Loop 구현]

공통 작업 규칙을 모두 적용한다.
작업 1~3의 현재 계약을 기준으로 한다.
기준 문서에서 1.2~1.3, 2장, 5.4~5.5, 7장, 10.3, 11.5를 읽는다.

목표:
- Concept Builder 입력을 Idea Origin + 잠긴 사용자 확정값 + current Legal Guardrail로 교체한다.
- Origin 또는 법률 검증에 실패한 후보를 사용자에게 노출하지 않는다.
- 최초 3개, 실패 수만큼 대체, 최대 2라운드/전체 9후보 정책으로 적격 Concept 3개를 확보한다.

구현 범위:
1. Concept Batch/Attempt/Draft
- 한 번의 사용자 생성 요청을 Batch로 관리한다.
- 최초 Draft 3개를 생성한다.
- 각 Draft에 생성 순서, attempt/round, input snapshot hash, prompt/schema version을 남긴다.
- 실패 Draft는 내부 감사·중복 방지용으로 저장하되 일반 후보 조회에서 제외한다.

2. Concept Contract
- conceptName
- targetSegment
- positioning
- featureSet
- pricing/revenueModel/channels
- operatingModel
- newAssumptions
- newBusinessActivities
- originTrace
- legalTrace
사용자 확정 가격·수익모델·채널이 있으면 Concept Builder가 변주하지 않고 상속한다.

3. Origin Integrity
- problem, target 본질, coreValue, fixedValues, 사용자 잠금값을 검증한다.
- 결과는 PASS / FAIL_ORIGIN이다.
- 필수 보존 항목 하나라도 위반하면 통과시키지 않는다.

4. Concept Legal Validation
- current Guardrail과 Concept Draft를 입력으로 CONCEPT_LEGAL_VALIDATION을 실행한다.
- hardConstraints, prohibitedPatterns, 필수 고지/운영 통제, 새 거래·데이터·책임 구조를 검사한다.
- 결과는 PASS / FAIL_LEGAL이다.
- 실패 후보에 사용자용 조건부 코멘트를 달아 통과시키지 않는다.

5. 대체 생성
- 실패한 수만큼만 다음 Round에서 생성한다.
- 실패 사유, 위반 구조 키, 기존 통과 Concept의 차별화 요약을 negative constraint로 전달한다.
- 동일하거나 실질적으로 유사한 실패 구조를 재생성하지 않도록 한다.
- targetEligibleCount=3, maxReplacementRounds=2, maxInspectedCandidates=9를 설정값으로 관리한다.
- 적격 3개를 확보하면 즉시 종료한다.
- 최대 시도 후 부족하면 FAIL 후보를 노출하지 않고 필요한 Origin/Legal 보완사항을 반환한다.

6. 상태와 복원
- GENERATING / VALIDATING_ORIGIN / VALIDATING_LEGAL / COMPLETED / NEEDS_INPUT / FAILED
- 새로고침 후 Batch 진행 상태와 완료 결과를 복원한다.
- 현재 Idea Origin 또는 Guardrail과 input hash가 다르면 기존 Batch를 current로 표시하지 않는다.

7. Frontend
- 생성 중 내부 Draft나 실패 후보를 일반 카드로 노출하지 않는다.
- 진행 상태는 '초안 생성 → Origin 검증 → 법률 검증 → 대체 후보 생성' 수준으로만 표시한다.
- 완료 시 적격 Concept 3개를 한 번에 표시한다.
- 각 Concept에서 Origin 보존 내역과 Guardrail 반영 내역을 펼쳐볼 수 있게 한다.
- 후속 시장 분석이나 Concept 선택으로 자동 이동하지 않는다.

이번 작업에서 하지 않을 것:
- 시장·BM·기술운영·재무 연결
- Persona·Interview·Marketing 연결
- 점수 기반 Concept Ranking
- 기존 후속 Journey 상태 변경

완료 조건:
- 정상 입력에서 적격 Concept 3개가 표시된다.
- Origin 또는 법률 위반 Fixture/실제 응답은 사용자 후보에 보이지 않고 실패 자리만 대체된다.
- 최대 한도 후 3개 미확보 시 보완 안내를 반환한다.
- 새로고침 후 완료 Batch와 3개 Concept이 복원된다.

완료 보고에는 사용자가 실행할 최소 Contract test/compile/build 명령과 3가지 브라우저 시나리오(전부 통과, 일부 대체, 최대 시도 실패)를 적고 직접 실행하지 않는다.
```

---

# 8. 작업 묶음 5 — 통합 안정화·선택적 Legacy 정리

## 시작 조건

사용자가 작업 1~4 흐름을 브라우저에서 확인한 뒤 수행한다. 이 묶음을 조기에 실행하지 않는다.

## Codex 지시문

```text
[작업 5 — Idea→Legal→Concept 통합 안정화 및 선택적 Legacy 정리]

공통 작업 규칙을 모두 적용한다.
사용자가 작업 1~4의 브라우저 흐름을 확인한 뒤 진행하는 작업이다.
기준 문서에서 9장, 10장, 11.6~11.8, 부록 A~B를 읽는다.

목표:
- Idea 입력부터 적격 Concept 3개 표시까지 현재 결과 판정, 새로고침 복원, 오류/재시도 흐름을 일관되게 만든다.
- 새 흐름으로 완전히 대체된 Idea·Legal·Concept 코드만 선택적으로 제거한다.
- 기존 시장 이후 MVP는 유지하고 새 흐름과 연결하지 않는다.

구현 범위:
1. Current/Stale
- current 결과는 최신 생성 시각이 아니라 IdeaOriginVersion, GuardrailVersion, input hash 일치로 판정한다.
- Origin 변경 시 Legal/Guardrail/Concept가 STALE임을 화면과 API에서 일관되게 처리한다.
- 과거 결과를 삭제하지 않는다.

2. 오류와 재시도
- TaskRun 실패, Provider Timeout, Registry Gap, Source Partial, Contract Invalid를 사용자 메시지와 운영 로그에서 구분한다.
- 재시도 가능 오류만 재시도 CTA를 제공한다.
- 중복 실행과 새로고침 후 재요청 폭주를 막는다.

3. Route/Journey
- Idea → Legal → Concept 범위의 Route와 단계 상태를 정리한다.
- Concept 결과에서 시장 이후 단계로 자동 이동하거나 기존 MVP API를 호출하지 않는다.
- 후속 단계는 기존 코드로 보존하되 이번 Journey의 완료 단계로 오인되지 않도록 한다.

4. 선택적 Legacy 제거
- 호출처가 없고 새 흐름으로 완전히 대체된 Idea/Legal/Concept Hook, API, Prompt, Schema, Redirect, 컴포넌트만 제거한다.
- 제거 전 코드 검색으로 참조 0건을 확인한다.
- 기존 Migration 삭제/수정 또는 Drop Migration은 하지 않는다.
- Landing/Auth/Admin/시장 이후 MVP 코드는 정리 대상이 아니다.

5. 문서와 env
- 실제 저장소에 존재하는 env example 기준으로 AI, 법제처, 내부 token 설정 설명을 정리한다.
- 변수명은 compose와 설정 객체의 실제 이름과 일치시킨다.
- 운영 배포, CI 복구, Secret Manager는 포함하지 않는다.

완료 조건:
- Idea → Legal → Concept 3개 표시 흐름이 새로고침과 재접속 후 복원된다.
- Origin 변경 후 이전 Legal/Concept가 current로 잘못 표시되지 않는다.
- 후속 MVP 기능은 삭제되거나 자동 연결되지 않는다.
- 새 흐름으로 대체된 중복 코드만 정리된다.

완료 보고에는 제거 파일과 보존 파일을 구분하고, 사용자가 실행할 최소 전체 흐름 검증 명령과 브라우저 체크리스트를 적고 직접 실행하지 않는다.
```

---

# 9. 빌드 오류 수정 전용 지시문

사용자가 최소 명령을 실행한 뒤 오류가 발생했을 때만 사용한다.

```text
[빌드 오류 수정]
저장소: C:\Users\seewo\Desktop\big_proj_01\new_2

아래에 제공한 실제 오류만 수정한다.
- 현재 작업 묶음과 관련된 변경 파일과 호출 계약을 먼저 확인한다.
- 오류 재현에 필요한 가장 작은 명령만 실행할 수 있다.
- Backend는 compileJava 또는 지정된 단일 test까지만 허용한다.
- Frontend는 제공된 build 오류 확인을 위한 npm build까지만 허용한다.
- AI Server는 import/구문 또는 지정된 단일 contract test까지만 허용한다.
- 오류가 해결되면 전체 Test/Lint/Docker로 확장하지 않는다.
- Git 명령은 실행하지 않는다.
- 기능 추가, 광범위한 리팩터링, 무관한 정리를 하지 않는다.

오류 로그:
<여기에 실제 로그 붙여넣기>

완료 보고:
1. 오류 원인
2. 수정 파일과 변경 내용
3. 실행한 최소 명령과 결과
4. 사용자가 다음에 실행할 명령
5. 남은 오류 또는 확인 필요 사항
```

---

# 10. 운영 원칙 요약

- DOCX는 사람용 기준본, Markdown은 Codex용 기준본으로 사용한다.
- 모든 구현을 한 번에 지시하지 않는다.
- 작업 1~4는 기능 묶음별로 순서대로 수행한다.
- 작업 5의 Legacy 정리는 사용자 흐름 확인 후에만 수행한다.
- Codex는 코드 구축에 집중하고, 사용자가 최소 Build/Test/Docker와 브라우저 검증을 수행한다.
- 빌드 오류는 실제 로그를 제공한 별도 작업에서만 최소 명령으로 수정한다.
- 이번 구축은 적격 Concept 3개 표시에서 종료하며 후속 MVP와 연결하지 않는다.
