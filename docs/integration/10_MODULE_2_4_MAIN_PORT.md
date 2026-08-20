# 모듈 2·4 를 `main` 판으로 되돌린 작업 — 인수인계

- Status: DONE (코드·DB 완료 / 파이프라인 실행 중단 지점 있음)
- 작업일: 2026-08-19
- 기준 브랜치: `full` (작업 트리, **커밋하지 않음**)
- 대조 대상: `origin/main`

---

## 0. 지시 사항 (이 문서의 전제)

> **모듈 1·3·5·6 은 무조건 `full` 판.**
> **모듈 2·4 는 절대적으로 `origin/main` 판.**
> `full` 브랜치가 2·4 에 대해 무엇을 했든 무시한다.

추가 지시:

> 프론트는 **바이트 단위 클론**이어야 한다. 「이건 이래서 안 똑같다」는 예외를 만들지 마라.
> 안 똑같을 가능성이 0 이 되게.

| 번호 | 모듈 | 기준 |
|---|---|---|
| 1 | 컨셉 포트폴리오 (선택·가설·법률·시드) | `full` |
| **2** | **사업 검증 (시장조사 · BM 캔버스 · 컨셉 다듬기)** | **`main`** |
| 3 | 기술·운영 / 재무 | `full` |
| **4** | **시장 인터뷰** | **`main`** |
| 5 | 마케팅 | `full` |
| 6 | 최종 사업 제안서 | `full` |

### 왜 되돌렸나 — 근거

`full` 이 모듈 2 의 **판단층을 통째로 들어냈다**. 브랜치 대조로 확인한 사실:

- `research2/tools/` 가 616KB → 285KB 로 줄었고 **추가된 파일은 0 개**다.
- `main` 의 `pipeline.py` 는 그 도구들을 실제로 부른다(`:592-598`, `:726`, `:807`).
- `full` 의 `serialize.py:719` `verified_report()` 는 `"judgment": None` 을 **하드코딩**하고
  독스트링에 "This is deliberately not an interpretation layer" 라고 적어 두었다.
- 결과: 봉투 계약과 프론트 정규화기는 남아 있는데 **`judgment`·`prescriptions` 를 만드는
  자가 없어 구조적으로 항상 `null`** 이었다.
- `main` 의 병합 커밋 `598209fe` 는 "1·2·4 파이프라인은 실측 판을 지킨다 … 여정 1·2·4 는
  오늘 유료로 완주해 실측이 있는 판을 쓰고" 라고 기록한다.
  `full` 쪽 커밋은 전부 `FAST …` / `P0 MASTER …` 형태의 자동 재작성이다.

---

## 1. 결과 — 세 층 모두 `main` 과 바이트 동일

아래 세 명령이 **아무것도 출력하지 않으면** 합격이다. 2026-08-19 기준 전부 공백.

```bash
git diff origin/main -- \
  frontEnd/src/features/market \
  frontEnd/src/features/market-interview \
  frontEnd/src/features/twin-survey

git diff origin/main -- \
  backend/src/main/java/com/aivle/backend/pipeline/market \
  backend/src/main/java/com/aivle/backend/pipeline/refinement \
  backend/src/main/java/com/aivle/backend/taskrun/contract

git diff origin/main -- \
  ai/app/research ai/app/interview ai/app/validation \
  ai/app/twin ai/app/providers ai/app/progress
```

전체 변경 규모: **추가 149 · 삭제 96 · 수정 169** 파일.

### 1.1 백엔드

**지운 것** (`full` 의 2·4)

```
pipeline/businessvalidation/   6개  ← 통째로
pipeline/marketinterview/      6개  ← 통째로
pipeline/refinement/          19개  ← 통째로
pipeline/market/ 안의 2·4 파일 (MarketResearch*, BmPlan*, TwinSurvey*,
                               ResearchCompetitorSeed*, MarketLedgerArtifact* …)
```

**가져온 것** (`main` 의 2·4)

```
pipeline/market/      38개   MarketResearch · MarketInterview · TwinSurvey · BmPlan
                             · BusinessValidationWorker
pipeline/refinement/   8개
taskrun/contract/      3개   MarketResearchContract · MarketInterviewContract
                             · TwinSurveyContract
+ MarketAnalysisSeedLookup, ConceptDriftContract  (main 2·4 가 쓰는데 full 에 없던 것)
```

**`main` 2·4 API 의 결정적 차이 — 옮길 때 반드시 알아야 한다**

`main` 의 `MarketInterviewRun` 은
`{id, project, taskRun, inputSnapshotHash, sampleSize, state, errorCode, completedAt}` 이고
`State = {QUEUED, RUNNING, SUCCEEDED, FAILED}` 다.
**시드 계보가 없고 `STALE` 상태도 없다.** 설계 의도다 — 인터뷰가 응답자에게 보인 것은
그때 화면이 보낸 컨셉보드이므로, 낡았는지는 시드가 아니라 그 보드가 정한다.
결과는 `MarketInterviewRun` 이 아니라 `MarketInterviewVersion` 에 있다.

또한 `main` 에는 **`BusinessValidationSession` 개념이 없다.** 「이 시드·이 개정으로 시장과
BM 이 한 세션에서 끝났다」를 증명하는 행이 없고, 각 종류의 **최신 버전을 그대로** 붙든다.

### 1.2 AI

**가져온 것** — 사용자가 「모듈 2 가 이상하다」고 지적한 그 판단층이 여기서 되살아난다.

| 경로 | 내용 |
|---|---|
| `research2/tools/` | **20개 추가** — `prescribe` · `synthesize` · `write_report` · `write_sections` · `judge_lines` · `promote_cards` · `publish_gate` · `read_passages` · `reask_*` · `render_*` … |
| `research2/rules/` | `prescribe.v1` · `promote.v1` · `publish.v1` · `synthesize.v1` |
| `app/interview/` | **10개 신규** — 모듈 4 의 실제 엔진 (코딩 · 포화도 · 타깃팅 · 원장) |
| `app/validation/` | `runner.py` 신규 + `gate` · `drift` · `mapping` · `citation` |

**지운 것**: `app/tasks/market_interview/` 7개(`deep_engine.py` 665줄 포함),
`research/semantic_relevance.py`, `research2/section_recall.py`.

### 1.3 프론트

`features/market/` 41개 · `features/market-interview/` 11개 · `features/twin-survey/` 22개 를
`origin/main` 에서 통째로 가져왔고, 파일 수까지 일치한다.
`features/business-validation/`(full 전용 8개)은 삭제했다.

`shared/ui/{content.jsx,ui.css}` 도 `main` 판이 **필수**였다 — `main` 의 `Card` 는 `title`
prop 을 `<h3 class="ui-card__title">` 로 그리는데, 그 prop 을 쓰는 17곳이 전부 2·4 안이다.
모듈 1·3·5·6 컴포넌트 중 `title` 을 넘기는 것은 없어서 안전하다.

---

## 2. 손으로 병합한 «공용 파일» — 통째 교체가 불가능한 자리

파일 소유가 2·4 가 아니라서, 통째로 `main` 을 덮으면 1·3·5·6 이 죽는다.
2·4 에 해당하는 줄만 `main` 판으로 맞췄다.

| 파일 | `main` 에서 가져온 줄 (2·4) | `full` 로 지킨 줄 (1·3·5·6) |
|---|---|---|
| `ai/app/api/executions.py` | `BUSINESS_VALIDATION` · `MARKET_INTERVIEW` 갈래 | `LAUNCH_READINESS` · `MARKETING_STRATEGY_*` · `FINAL_BUSINESS_PROPOSAL_*` |
| `app/routing/AppRouter.jsx` | `market` · `business-model` · `concept-refinement` · `market-interview` 라우트 | `tech-ops` · `finance` · `marketing/report` |
| `projectRoutes.js` | 파일 전체 main | `techOps` · `finance` · `marketingStrategyReport` 재추가 |
| `projectJourneyModel.js` | 여정 4 = 시장 인터뷰, `PATH_TO_JOURNEY` | `OPTIONAL` 상태, `launch`/`finalReport` |
| `projectModuleModel.js` | `market`/`businessModel`/`conceptRefinement` 행 | `techOps`/`finance`/`launchReadiness` 행 |
| `ProjectLayout.jsx` | 경로 합치기 1줄 제거 | 나머지 전부 |
| `jobPresentation.js` | `TWIN_*` 라벨 | `MARKETING`/`TECH_OPS`/`FINANCE`/`LAUNCH_READINESS` |
| `InternalAiExecutionClient.java` | 클라이언트 배정 + 인터뷰 실패 사유 2종 | 나머지 |
| `TaskRunService.java` | 화면에 나가는 사유 낱말 4종 | 나머지 |

> ⚠ **`ai/app/api/executions.py` 는 방향을 뒤집어 만들었다.**
> 처음에는 `full` 파일에 `main` 갈래를 «넣었다». 그러면 2·4 줄이 `main` 과 같은지를
> 사람이 눈으로 대조해야 한다. 다시 만들 때는 **`main` 파일에서 시작해 1·3·5·6 갈래만
> 얹었다.** 그래서 2·4 에 해당하는 모든 줄이 `main` 바이트 그대로이고, 남는 diff 는
> 정의상 1·3·5·6 것뿐이다 — diff 한 장으로 증명된다. **같은 상황이 또 생기면 이 방향으로.**

### 2.1 아직 `full` 판인 공용 파일 2개 — **의도적으로 남김**

사용자 결정: **둘 다 유지.** 「0% 동일」을 증명할 수 없는 유일한 자리이므로 여기 남긴다.

| 파일 | `full` 이 더 가진 것 | 2·4 에 미치는 영향 |
|---|---|---|
| `shared/api/apiError.js` | `IDEMPOTENCY_CONFLICT` 사용자 문구 (5줄) | 409 일 때 **글자만** 다르다. 코드→문자열 조회라 동작·결과 불변. 이 줄이 막은 사고는 모듈 1(컨셉 확정이 이미 성공했는데 화면이 실패만 반복) |
| `shared/async-events/useJobEvents.js` | cursor 0 의 404 를 2회까지 재접속 (13줄) | 이 훅을 쓰는 13개 화면 중 2·4 는 `MarketResearchPage` 하나. 헛에러가 재접속으로 바뀔 뿐 결과 불변. 지우면 1·3·5·6 의 잡 등록 레이스가 되살아남 |

---

## 3. 도려낸 «사문(死文)» — 호출부 0건을 확인하고 삭제

| 대상 | 확인 방법 | 결과 |
|---|---|---|
| `ConceptPortfolioSelection.attachAuxiliaryTask` / `completeAuxiliaryTask` / `clearAuxiliaryTaskIfActive` / `recoverBlockedRefinement` | 본문·테스트 전체 grep | **0건** → 삭제 |
| `ConceptPortfolioSelectionTaskFactory.createAuxiliary` | 〃 | **0건** → 삭제 |
| 실패 사유 `HARNESS_PRECONDITION_FAILED` · `RESEARCH_SNAPSHOT_MISSING` · `MARKET_ROUTE_UNRESOLVED` | AI 쪽 emitter grep | **0건**(full 엔진과 함께 소멸) → 삭제 |
| `AiTaskProgressService` 의 `completedCount`/`totalCount`/`candidateCount` | AI 전체 grep | **0건** → `main` 판으로 |
| `jobEventMessages.js` 의 `job.market-interview.*` 5줄 + `MI_*` 합치기 규칙 | `main` 워커는 `job.market.interview.*`(점) 을 낸다 — 하이픈 키를 내는 자가 없음 | 삭제 |
| `structured.py` 의 `temperature_override` / `reasoning_effort_override` | 넘기는 곳 grep | **0건** → `main` 판으로 |

---

## 4. 이 작업이 실제로 **고친** 결함

옮기는 김에 발견해 고친 것. 안 고쳤으면 2·4 가 `main` 과 다르게 동작했다.

| 파일 | 문제 | 조치 |
|---|---|---|
| `TaskRunService` | 사유 낱말 4개(`TWIN_BANK_UNAVAILABLE` · `TWIN_TASK_TYPE_NOT_SERVICEABLE` · `MARKET_INTERVIEW_NO_USABLE_RESPONSE` · `MARKET_INTERVIEW_NO_TARGET_SAMPLE`)가 없어 전부 `TRANSIENT_EXECUTION_FAILURE` 로 접혔다. 화면이 **「조건을 고쳐라」와 「AI 가 죽었다」를 구분해 말할 수 없었다** — 사용자가 할 일이 정반대인데도 | 4개 복원 |
| `AiServerProperties` | 시장조사 전송 타임아웃 **22분** < 워커 예산 **60분**. 전송이 먼저 끊기고 그 실패가 retryable 이라 **이미 지불한 수집을 버리고 한 번 더 태운다** | 63분(main) |
| `InternalAiExecutionClient` | `BUSINESS_VALIDATION` 이 30초 클라이언트로, `MARKET_INTERVIEW` 가 twin 아닌 long 클라이언트로 갔다 | main 배정 |
| `ConceptPortfolioSelectionService` | `BUILD_HANDOFF` 가 다듬기 오버레이를 안 실었고, `READY_FOR_MARKET` 상태의 **재확정을 거절**했다. 다듬기가 `targetUsers`·`featureSet` 만 고치면 `confirm()` 이 안 불려 상태가 거기 머무는데, 그러면 **다듬어진 두 칸이 시드에 영영 못 실린다** | 오버레이 전달 + 재확정 허용 |
| `ConceptPortfolioSelectionMaterializationService` | `NARRATE_REFINED` 갈래 자체가 없었다 | main 갈래 + 검증 3종(`narrativeKeepsConcept` · `narrativeMatchesChanges`) 이식 |
| `FinalReportLaunchReadinessV21Tests` | `.get(6)` 으로 7번 절을 집어 「출시 준비」 대신 「주요 위험」을 검사했다(오프바이원, 0부터 셈) | `.get(5)` |

---

## 5. Flyway · DB

### 5.1 번호 재배치

`V27` 부터 두 브랜치가 충돌했다(main V27=market_interview / full V27=launch_readiness).
`V1~V16`, `V18~V26` (25개)는 세 곳 모두 md5 동일 — 공통 조상이다.

**`full` 의 기존 번호는 하나도 바꾸지 않았다**(체크섬 보존).
`main` 의 2·4 마이그레이션 9개만 뒤로 재번호했다.

| 번호 | 파일 | 모듈 | 출처 |
|---|---|---|---|
| V1–V16, V18–V26 | (공통 25개) | 전 모듈 | 양쪽 동일 |
| V27 | launch_readiness_user_document_authority | 3·6 | full (= main V36 과 **바이트 동일**) |
| V38 · V39 · V40 · V42 · V43 | marketing / launch / finalreport / techops | 3·5·6 | full |
| V45 | market_interview | **4** | main V27 |
| V46 | business_validation_kind | **2** | main V28 |
| V47 | concept_refinement_rounds | **2** | main V29 |
| V48 | concept_refinement_finals | **2** | main V30 |
| V49 | seed_refinement_applied | **2** | main V31 |
| V50 | market_seed_unique_index_is_stale_aware | **2** | main V32 |
| V51 | refinement_accepted_fields | **2** | main V33 |
| V52 | refinement_backfill_legacy_accepted | **2** | main V34 |
| V53 | refinement_cycle_per_research | **2** | main V35 |

**총 40개.** 빈 번호 V17 · V28–V37 · V41 · V44 는 **태워진 번호라 재사용 금지.**
가져온 9개는 `origin/main` 원본과 md5 일치를 확인했다(주석 안 번호가 낡았지만 **일부러 안
고쳤다** — 바이트 동일성을 지켜야 나중에 main 과 다시 대조할 수 있다).

제거: `full` 판 V28~V37 · V41 (모듈 2·4 것, 다른 모양의 동명 테이블을 만든다),
그리고 이 작업 중 손으로 만들었던 `V44__seed_refinement_applied.sql`(main V31→V49 와 **완전 중복**).

### 5.2 스키마 검증

엔티티 **67개 전수**를 SQL 40개로 재구성한 스키마와 대조 → **누락 테이블·컬럼 0건.**
(`scratchpad/verify_schema.py`, 음성 대조로 스크립트가 실제로 결함을 잡는 것도 확인)

### 5.3 DB 재생성 — **실행 완료**

`flyway repair` 로는 안 됐다. 옛 V28~V44 가 이력에 남아 있는 데다 **다른 모양의 동명
테이블**을 이미 만들어 놔서 새 V45~V53 의 `CREATE TABLE` 이 중복으로 죽는다.
`clean-disabled: true` 라 Flyway clean 도 못 쓴다 → `DROP SCHEMA public CASCADE` 가 유일한 길.

```
백업   C:\Users\A\Desktop\aivle-backup-2026-08-19-before-renumber.sql
       (2.6MB · 69테이블 · 데이터 포함 · 옛 프로젝트 3번이 이 안에 있다)
결과   Successfully applied 40 migrations to schema "public", now at version v53
       Started BackendApplication in 12.717 seconds
```

새 스키마가 `main` 모양인지 확인함 —
`market_interview_runs` 가 `input_snapshot_hash·sample_size·error_code`(main 판),
`concept_refinement_rounds` 가 `accepted_fields_json·research_version`(main 판),
`business_validation_sessions` 는 **소멸**.

> ⚠ MinIO(`aivle-ai-artifacts`) 객체는 남아 있고 `project_evidence_artifacts` 행만
> 사라져 **참조를 잃은 고아**가 됐다. 정리하려면 버킷을 따로 비워야 한다.

---

## 6. 테스트 현황

| 층 | 결과 |
|---|---|
| 백엔드 | `BUILD SUCCESSFUL` — **726개 전부 통과** |
| AI | **1061 통과 / 3 실패** |
| 프론트 | 빌드 `✓ 453ms` · **747 통과 / 7 실패** |

### 실패 3+7 건은 전부 **이번 작업과 무관한 기존 실패** — 증거

- `test_headline_rules.py`, `test_rules_are_business_agnostic.py[publish.v1.json]`
  → `origin/main` 워크트리를 따로 만들어 돌렸더니 **거기서도 똑같이 실패**한다.
- `test_marketing_content_contract.py`
  → 모듈 5 의 `MarketingSourceSnapshot.strategy` 스키마 문제.
  그 테스트가 닿는 파일 전부가 `full` HEAD 와 바이트 동일함을 확인했다.
- 프론트 `AuthPages.test.jsx` 6건 → `ServicePolicyProvider` 없이 렌더하는 기존 실패.
  기록된 baseline 에 정확히 이 6건이 있다.
- 프론트 `BusinessModelPage.test.jsx` 1건 → **`origin/main` 자체의 stale 테스트**다.
  heading `'BM 분석'` 을 기대하는데 컴포넌트(양쪽 바이트 동일)는 `'수익 구조 분석'` 을 그린다.
  바이트 동일성을 위해 main 판을 그대로 가져왔다. 그 feature 디렉터리는 **라우트도 import 도
  없는 죽은 코드**라 화면 영향 없음.

---

## 7. 실행 방법 — 함정 2개

### ⚠ ① `SPRING_PROFILES_ACTIVE=postgres` 를 반드시 준다

`.env` 에 이 키가 **없다.** 없이 띄우면 `local` 프로파일로 떨어져
`jdbc:h2:file:./data/aivle` 로 붙고, Flyway 가 엉뚱한 DB 를 본다.
(이 때문에 재생성 직후 첫 기동이 `Detected failed migration to version 1` 로 실패했다)

### ⚠ ② `scratchpad/load-env.ps1` 을 쓰지 마라

PowerShell 5.1 이 UTF-8(BOM 없음) 경로를 ANSI 로 읽어 `빅프` 가 깨진다
(`C:\Users\A\Desktop\鍮낇봽\.env`). 아래처럼 **인라인으로** 읽어야 한다.

```powershell
$root = Join-Path $env:USERPROFILE 'Desktop\빅프'
Get-Content (Join-Path $root '.env') -Encoding UTF8 | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) { return }
  $i = $line.IndexOf('='); $k = $line.Substring(0,$i).Trim(); $v = $line.Substring($i+1).Trim()
  if ($k) { Set-Item -Path "Env:$k" -Value $v }
}
$env:SPRING_PROFILES_ACTIVE = 'postgres'
Start-Process -FilePath "C:\Users\A\anaconda3\envs\aivlejdk\Library\lib\jvm\bin\java.exe" `
  -ArgumentList '-jar','build\libs\backend-0.0.1-SNAPSHOT.jar' `
  -WorkingDirectory (Join-Path $root 'backend')
```

AI 서버:

```powershell
# 같은 방식으로 .env 를 읽은 뒤
Start-Process -FilePath (Join-Path $root 'ai\.venv-local\Scripts\python.exe') `
  -ArgumentList '-m','uvicorn','main:app','--host','127.0.0.1','--port','8000' `
  -WorkingDirectory (Join-Path $root 'ai')
```

### 포트 — **화면은 3000 으로 연다**

| 포트 | 정체 |
|---|---|
| **3000** | **기록 프록시** (`scratchpad/trace-proxy.mjs`). 요청을 5173/8080 으로 넘기면서 전 구간을 `scratchpad/trace.jsonl` 에 남긴다. **화면 확인은 여기로 할 것** — 이상이 생겼을 때 그 기록으로 원인을 짚을 수 있다 |
| 5173 | Vite 개발 서버 (원본) |
| 8001 | 프록시의 API 다리 |
| 8080 / 8000 / 5432 | 백엔드 / AI / PostgreSQL |

```
http://localhost:3000/auth/login       로그인
http://localhost:3000/app/projects/2   작업 중인 프로젝트
```

> ⚠ `LOCAL_RUN.md` 가 말하는 3000 은 **Docker compose 의 nginx** 다. 이 환경은 WSL2 가 없어
> Docker 를 못 쓰므로 그것이 아니다. 지금 3000 은 위의 기록 프록시다.

### 로그인 계정

```
아이디   admintest
비밀번호 mastermastermaster!
```

`.env:62-65` 의 부트스트랩 관리자다. DB 를 비웠지만 백엔드가 뜨면서 다시 만들어져,
현재 `users` 테이블에는 이 계정 하나만 있다.

현재 기동 상태: 3000 ✅ · 5173 ✅ · 8001 ✅ · 8080 ✅ · 8000 ✅ · 5432 ✅

### 기타 환경

- `TWIN_BANK_DIR=C:/Users/A/Desktop/빅프/ai/app/twin/bank` (`.env` 에 채워 둠)
  뱅크는 8,604명 / 10.8MB, **재배포 금지 자산**이라 `.gitignore:14` 로 빠져 있다.
- Docker 는 WSL2 미설치로 사용 불가. `local` H2 프로파일은
  `V1__new_pipeline_baseline.sql:63` 의 PostgreSQL 전용 부분 인덱스 때문에 부팅 실패.

---

## 8. 파이프라인 실행 — 현재 위치

### 8.1 프로젝트 선정 근거 (중요)

처음에 만든 **B2B 안(소상공인 재고 예측 SaaS)은 폐기**했다. 이유는 인터뷰다.

트윈 뱅크는 **일반 소비자 8,604명 패널**이고, 조건으로 걸 수 있는 축이 이것뿐이다:

> 나이 · 성별 · **가구원 수** · 지역 · 개인 월소득 · 직업 · 자녀 유무 · 가구 내 역할

**「소매 자영업자」는 이 중 어디에도 없다.** 조건이 0명이 되면 표본 전원이 조건 밖에서
채워지는데, 그건 조사가 아니라 일화다.
(`app/interview/targeting.py` 가 이 함정을 문서로 남기고 있다 — 「맞벌이」는 뱅크에
**0회** 나와서 40명 전원이 조건 밖에서 채워졌는데 화면 경고는 0건이었다)

바꾼 안은 실측으로 뽑힌다:

| 조건 | 패널 인원 |
|---|---|
| 1인 가구 전체 | **704명** |
| 1인 가구 20~49세 | **85명** |
| 1인 가구 20~39세 | 42명 ← 40명 조사엔 마름 |

**선정: 「1인 가구 소용량 간편식 정기배송」 (projectId = 2)**

- **B2C** ✓
- **인터뷰 가능** — 「혼자 사는 20~40대」 = 가구원 수 축으로 실제로 뽑힌다(85명 풀)
- **시장조사 공개자료로 가능** — 통계청 1인가구 통계 · 가계동향조사 식료품비 ·
  aT 간편식 시장규모. 경쟁사(프레시지 · CJ제일제당 · 풀무원)가 상장사라 **DART 로 매출·원가를
  실제로 집을 수 있다** — 추정이 아니라 관측이 된다
- **대기업 신사업 맥락 반영** — "식품 대기업이 기존 제조·물류 역량을 활용한 D2C 구독"

### 8.2 진행 상태

```
projectId = 2  「1인 가구 소용량 간편식 정기배송」

✅ 프로젝트 생성
✅ 아이디어 브리프 도출 · 해석 확정 · 아이디어 확정
     status = CONFIRMED   readyForConfirm = true   score = 100
     confirmedSnapshotId = 2c0afd09-548e-47ef-84c3-e7ecf54530d7
     questions = 0   contradictions = 0

⬜ 컨셉 포트폴리오 5개 생성   ← **여기서 멈춰 있다** (유료)
⬜ 컨셉 선택 → 가설 7종 확정 → 법률·규제 보고서 → 시장분석 시드
⬜ 모듈 2  사업 검증 (시장조사 최대 60분 · 약 470콜 → BM → 다듬기)
⬜ 모듈 3  기술·운영 / 재무
⬜ 모듈 4  시장 인터뷰
⬜ 모듈 5  마케팅
⬜ 모듈 6  최종 사업 제안서
```

> ⚠ 확정 단계에서 `POST /idea-brief/confirm` 이 400 「검토 준비 상태가 아닙니다」를 냈는데
> **무해했다.** `confirm-interpretation` 이 이미 확정까지 처리해서, 그다음 `confirm` 이
> 「이미 확정됨」으로 거절된 것이다. 상태를 조회해 `CONFIRMED` 임을 확인했다.

### 8.3 다음 명령

```bash
cd "C:/Users/A/AppData/Local/Temp/claude/C--Users-A-Desktop---/<세션>/scratchpad"
PYTHONIOENCODING=utf-8 "C:/Users/A/Desktop/빅프/ai/.venv-local/Scripts/python.exe" -c "
from api import call, ok
root='/api/v3/projects/2'
st, pl = call('GET', f'{root}/concept-portfolio-runs/current')
run = pl.get('data') if st==200 else None
if not run:
    run = ok('POST', f'{root}/concept-portfolio-runs',
             {'ideaBriefSnapshotId': '2c0afd09-548e-47ef-84c3-e7ecf54530d7',
              'maxConcepts': 5, 'idempotencyKey': 'portfolio-2'}, timeout=300)
print(run['runId'], run.get('productStatus'))
"
```

---

## 9. 크롬 확장 (Claude in Chrome) — 연결 실패 원인

**Chrome 쪽은 전부 정상이다.** 문제는 세션 쪽이다.

| 항목 | 결과 |
|---|---|
| 확장 설치 | `Claude` v1.0.85 · `Profile 2` |
| 확장 활성 | `disable_reasons: []` — 꺼져 있지 않음 |
| 권한 | `nativeMessaging` 부여 · `<all_urls>` granted+active · `withholding_permissions: false` |
| 네이티브 호스트 등록 | `HKCU\…\NativeMessagingHosts\com.anthropic.claude_code_browser_extension` ✓ |
| `allowed_origins` | `chrome-extension://fcoeoabgfenejglbffodgkkbkcdhcgfn/` = **설치된 확장 ID 와 일치** |
| Chrome 실행 프로필 | `--profile-directory="Profile 2"` = **확장이 깔린 그 프로필** |
| 네이티브 호스트 프로세스 | **안 돌고 있음** |

**원인은 시각이다.**

```
09:06  Claude Code 세션 시작
14:38  크롬 확장 설치
14:50  chrome-native-host.bat 생성 (/chrome 을 처음 누른 시점)
```

세션이 시작될 때 셋 다 없었다. **도구 스키마는 세션이 뜨는 순간 주입되고 중간에 못 붙는다.**
그래서 `/chrome` 이 `Status: Disabled` 로 뜨고, 도구 레지스트리를 직접 조회해도
`No matching deferred tools found` 가 나온다.

**해결: 재시작.**

```
claude --continue --chrome
```

(`~/.claude.json` 에 `claudeInChromeDefaultEnabled: true` 라 플래그 없이도 될 수 있다.
`/chrome` 메뉴의 「Reconnect extension」을 먼저 눌러 보는 것이 더 싸다)

---

## 10. 도구 · 스크립트 인벤토리

전부 세션 scratchpad
(`C:\Users\A\AppData\Local\Temp\claude\C--Users-A-Desktop---\<세션>\scratchpad`)에 있다.
**세션용 임시 디렉터리라 사라질 수 있다** — 계속 쓸 것은 옮겨 둘 것.

| 파일 | 용도 |
|---|---|
| `api.py` | UTF-8 고정 API 클라이언트. 401 자동 재로그인, POST 에 `Idempotency-Key` 자동 부착(**재시도에도 같은 키** — 새로 뽑으면 401 재시도가 두 번째 실행이 된다), `poll()` 헬퍼 |
| `q.sh` | `.env` 접속정보로 PostgreSQL 질의 (비밀번호 미출력) |
| `verify_schema.py` | SQL 40개 → 스키마 재구성 후 엔티티 67개 전수 대조 |
| `db_rebuild.md` | DB 재생성 절차 (이미 실행함) |
| `p_new.py` | B2C 프로젝트 생성 + 아이디어 도출 |
| `patch_*.py` | 이번 이식에 쓴 패치 스크립트들 |
| `before-main-merge.patch` | 이식 «전» 복원점 |
| `aivle-backup-before-renumber.sql` | DB 백업 (사본이 바탕화면에도 있음) |

> ⚠ **Bash heredoc 에 한글·`«»`·따옴표를 넣지 마라.** 이번 작업 내내 `unexpected EOF` 로
> 깨졌다. 패치 스크립트는 **Write 툴로 파일을 만들어 실행**할 것.
> 같은 이유로 `\n` 이 heredoc 안에서 진짜 개행이 돼 f-string 을 깨뜨린 적도 있다.

---

## 11. 남은 일

1. **커밋하지 않았다.** 작업 트리에만 있다. 리뷰 후 커밋 필요.
2. 파이프라인 1→6 완주 (8.2 의 ⬜ 항목).
3. MinIO 고아 객체 정리 (선택).
4. 마이그레이션 주석 안의 낡은 번호 정정 (선택 — **main 과 재대조가 끝난 뒤에** 별도 커밋으로).

---

## 12. 철로 점검 — 파이프라인을 누르기 전에 확인한 것 (2026-08-19 재개 세션)

### 12.1 ⚠ 이식이 놓친 한 줄 — 모듈 2 가 22분에 끊긴다

§4 는 시장조사 전송 타임아웃을 「63분(main)으로 고쳤다」고 적었다. **런타임에는 안 고쳐져
있었다.** 자바 기본값만 63분이고, 그 값은 `@ConstructorBinding` 이 **null 일 때만** 쓴다.

| 자리 | 발견 당시 | 조치 |
|---|---|---|
| `AiServerProperties.java:34` 자바 기본값 | 63분 | 그대로 (main 과 동일) |
| `application.yaml:51` 플레이스홀더 기본값 | **22분** | → 63분 |
| `.env:30` | **22분** ← 실사용 | → 63분 |

`origin/main` 의 `application.yaml:51` 은 **원래 63m 이다.** `full` 이 22m 이었고 이식이 이
한 줄을 안 가져왔다. 그래서 63m 으로 고치는 것은 발산이 아니라 **파리티 복원**이고,
고친 뒤 `git diff origin/main -- backend/src/main/resources/application.yaml` 은 **공백**이다.

`clientFor():190-192` 가 `MARKET_RESEARCH` 와 `BUSINESS_VALIDATION` 을 둘 다 이 클라이언트로
보내고 두 워커의 `BUDGET` 이 각각 60분이므로, **어느 쪽으로 눌러도 22분에 전송이 먼저
끊겼다.** 그 실패는 retryable 이라 이미 지불한 수집을 버리고 한 번 더 태운다.

### 12.2 같은 결함이 모듈 3·5 에도 있었다 — 기본 클라이언트 75초

`clientFor()` 의 longRunning 목록에 `MARKETING_VISUAL_GENERATION` · `TECH_OPS_PROPOSAL` ·
`FINANCE_ESTIMATE` · `FINANCE_ANALYSIS_REPORT` 가 **빠져 있어** 전부 기본 클라이언트로 간다.
`.env:28` 이 75초였으므로 예산(3~5분)보다 짧았다.

| taskType | 클라이언트 | read-timeout (전) | 워커 예산 | |
|---|---|---|---|---|
| `CONCEPT_PORTFOLIO_V2_*` | conceptPortfolio | 15m | ai-deadline 14m | ok |
| `MARKET_RESEARCH` | marketResearch | 22m | 60m | **결함** |
| `BUSINESS_VALIDATION` | marketResearch | 22m | 60m | **결함** |
| `TWIN_SURVEY` | twinSurvey | 14m | 12m | ok |
| `MARKET_INTERVIEW` | twinSurvey | 14m | 10m | ok |
| `TWIN_STIMULUS_DRAFT` | 기본 | 75s | 90s | **결함** |
| `MARKETING_CONTENT/STRATEGY` | longRunning | 7m | 5m | ok |
| `MARKETING_VISUAL_GENERATION` | 기본 | 75s | 5m | **결함** |
| `FINAL_BUSINESS_PROPOSAL_*` | longRunning | 7m | 6m·5m | ok |
| `LAUNCH_*_READINESS` | longRunning | 7m | 7m | **동률 — 경계** |
| `TECH_OPS_ADVISORY` | longRunning | 7m | 6m | ok |
| `TECH_OPS_PROPOSAL` | 기본 | 75s | 3m | **결함** |
| `FINANCE_ESTIMATE` · `FINANCE_ANALYSIS_REPORT` | 기본 | 75s | 3m | **결함** |

워커 예산은 `claimNext(TYPE, workerId, lease, budget)` 의 **4번째 인자**다
(모듈 3·5·6 은 `static final Duration BUDGET` 상수를 안 쓰므로 상수 이름으로 grep 하면 안 보인다).
컨셉 포트폴리오만 `ConceptPortfolioExecutionProperties`(`:19` 기본 14분,
`:25` 에 `aiDeadline >= taskTimeout` 이면 기동을 막는 불변식)로 잡는다.

**조치 — `.env` 3줄. yaml 은 파리티 때문에 51행만 건드렸다.**

```
AI_SERVER_READ_TIMEOUT                  75s -> 6m    # 재무 3m · 마케팅 이미지 5m 를 덮는다
AI_SERVER_LONG_READ_TIMEOUT             7m  -> 9m    # LAUNCH_* 7m 동률을 푼다
AI_SERVER_MARKET_RESEARCH_READ_TIMEOUT  22m -> 63m   # 모듈 2
```

`.env` 원본은 `scratchpad/env.before-timeout-fix` 에 있다.
재기동 확인: 프로파일 `postgres` · Flyway 40개 검증 · schema v53 · 12.5초 기동 · health 200.

> ⚠ 실행 중인 jar 안의 `application.yaml` 은 아직 22m 판이다. `.env` 가 이기므로 런타임은
> 63m 이 맞지만, **소스와 jar 를 일치시키려면 커밋 전에 `bootJar` 를 다시 말아야 한다.**

### 12.3 통과한 점검

| 점검 | 결과 |
|---|---|
| 층간 계약 테스트 8종 | **93 passed / 7 skipped** |
| `test_internal_task_type_alignment` | 통과 (backend enum 29 ↔ AI 갈래) |
| 외부 의존 `tools/preflight.py` | **진입 가능** — openai(엔진·하네스 실호출) ok · KOSIS 200 · DART 200 · Tavily 키 존재 · `pdfplumber 0.11.9`·`trafilatura 2.2.0` 설치됨 |
| 트윈 뱅크 | `ai/app/twin/bank/` 12MB · manifest·cards·frame 3종 실재 |

### 12.4 모듈 5 기존 실패 — 화면에 안 닿는다 (판정 완료)

`test_marketing_content_contract.py` 의 1건은 **테스트만의 문제**다. 실제 응답 스키마인
`MarketingContentResult` 는 런타임 가드 `strict_schema_failures()` 에서 **결함 0** 이고
(`app/tasks/marketing_content/service.py:31-32` 가 이것을 `response_schema` 로 쓴다),
실패하는 `MarketingSourceSnapshot` · `MarketingContentInput` 은 **입력 모델**이라
`response_format` 으로 나가지 않는다. 테스트가 입력 모델까지 싸잡아 린트한 것이다.

`structured.py:184-191` 은 스키마가 불량하면 **호출 전에**
`RESULT_SCHEMA_INVALID / PROVIDER_RESPONSE_SCHEMA_REJECTED`(retryable=false)로 던지므로,
만약 응답 스키마가 걸렸다면 모듈 5 는 유료 호출도 못 해 보고 죽었을 것이다. 확인해 둘 값어치가 있었다.

### 12.5 실패 코드를 액면대로 믿지 마라

`ConceptPortfolioWorker.java:104-107` 의 마지막 `catch (RuntimeException)` 이 **모든 런타임
예외를 `RESULT_SCHEMA_INVALID` 로 접는다.** 이번에 실제로 속았다 — 폐기한 프로젝트 1번의
실행이 `RESULT_SCHEMA_INVALID` 로 기록돼 있었는데, 진짜 원인은
`JobEventPublisher:54` 의 `PROJECT_NOT_FOUND` 였다(프로젝트 1이 14:43:20 에 소프트 삭제됐고
그 실행이 14:47:19 까지 살아 있었다). AI 응답 자체는 정상으로 돌아왔다 —
예외가 터진 `processOne:76` 은 `response != null` 을 통과한 뒤의 `MATERIALIZING` 발행 지점이다.

---

## 13. 단계별 소요시간 — 실측

계측을 새로 붙이지 않았다. `task_runs`(`started_at`·`finished_at`·`attempt_count`)와
`job_events`(`stage` 전이)가 이미 재고 있다. `scratchpad/timings.py` 가 이것을 읽는다.

| 모듈 | 단계 | 소요 | attempt |
|---|---|---|---|
| 1 | 아이디어 브리프 도출 | **0:04** | 1 |
| 1 | 컨셉 포트폴리오 생성 (`CONCEPT_PORTFOLIO_V2_RUN`) | **6:45** | 1 |

컨셉 포트폴리오 내역(stage 누적): 법률검토 2:22 · 후보확장 2:01 · 후보검증 0:55 ·
계획 0:51 · 법률복구 0:34.

> `attempt_count` 를 반드시 같이 본다. 소요시간만 보면 **두 번 태운 단계가 안 보인다.**

### 13.1 컨셉 포트폴리오는 5개를 요청해 1개만 나왔다

`RESULTS_WITH_OPEN_INPUT` · 컨셉 1개(「야근 직장인 주중 식사 안전망」, 법률 ACCEPT) ·
**열린 입력 2건**. 둘 다 법률검토가 사업 사실을 되묻는 것이다.

`ConceptPortfolioContinuationService` 계약:
- `confirmedFacts` 의 키 집합이 그 요청의 `affectedFields` 와 **정확히 같아야** 한다(`:207`)
- `TEXT_FACTS = {sellerRole, providerRole, intermediaryRole}` 는 문자열,
  `LIST_FACTS = {transactionFlow, paymentFlow, partnerRequirements, personalDataUsage,
  physicalActivities}` 는 리스트(1..20)
- `requireContinuable:213` 이 `activeTaskRunId != null` 이면 거절한다 →
  **열린 입력이 여러 건이어도 한 번에 하나씩** 순차로 넣어야 한다

### 12.6 AI 자기모순 — fact-consistency 복구 후보는 이어가기가 불가능했다

**이번 실행에서 실제로 파이프라인을 끊은 결함이다.** 이식과 무관하게 양쪽 브랜치에 원래 있었다
(`engine.py`·`models.py` 둘 다 `origin/main` 과 바이트 동일이고 이식이 안 건드렸다).

증상: 열린 입력에 답했더니 이어가기가 **0초 만에** `AI_RESULT_INVALID` 로 죽었다.
진짜 원인은 백엔드 로그에만 있었다:

```
code=REQUEST_SCHEMA_INVALID
path=input.continuationArtifact.candidateSnapshot.recoverySource
expectedType=allowed literal  category=literal_error
```

기전:

- `engine.py:1000` 이 fact-consistency 복구 후보에 `recoverySource="FACT_CONSISTENCY_REPAIR"`
  를 붙이고 `candidateId` 에 `-FC1` 접미사를 단다(`:998`).
- 출력 모델(`app/tasks/concept_portfolio_v2/models.py:196`)은 `recoverySource: str` 이라
  **나갈 때는 통과**한다.
- 입력 모델 `CandidateEnvelope.recoverySource`(`app/concept_portfolio_v2/models.py:316`)는
  Literal 8종인데 **그 값이 없다.** 그래서 **돌아올 때 자기가 만든 값을 거절**한다.
- 결과: `-FC1` 후보는 구조적으로 이어가기가 불가능했다. 실패한 후보가 정확히 `C1-FC1` 이었다.

> `FACT_CONSISTENCY_REPAIR` 는 같은 파일 `:438` 의 `reasonType` Literal 에는 들어 있다.
> **다른 필드다.** 이름이 같아서 있는 것처럼 보이는 자리라 놓치기 쉽다.

조치: `models.py:316` Literal 에 `"FACT_CONSISTENCY_REPAIR"` 추가.
`tests/concept_portfolio_v2` **243개 통과**. AI 서버 재기동 후 `retry` 로 되살렸고,
같은 이어가기가 이번에는 80초 돌아 정상 진행했다.
원본은 `scratchpad/models.py.before-recoverysource`.

> 이 결함이 왜 여태 안 보였나 — fact-consistency 복구는 **후보가 사실 정합성에서 걸릴 때만**
> 도는 갈래다. 그 갈래를 탄 후보에 **열린 입력이 붙어야** 비로소 이어가기가 일어난다.
> 두 조건이 겹쳐야 해서 평소 실행에서는 안 밟힌다.

### 12.7 열린 입력은 한 번에 끝나지 않는다 — 라운드로 돈다

법률검토는 답을 받으면 **다음 사업 사실을 되묻는다.** 실측:

| 라운드 | 후보 | 물은 것 | 소요 |
|---|---|---|---|
| 1 | C2 | 포장재 회수 구조 | 39초 |
| 1 | C1-FC1 | 판매 주체(운영사/중개) | 실패 → 재시도 80초 |
| 2 | C2 | 식품 유형·제조 장소·냉장 온도·소비기한 근거 | |
| 2 | C1-FC1 | 용기·포장재 재질·일회용 여부·사용량 | |

2라운드부터는 `affectedFields` 가 **빈 배열**로 온다. 그러면 `validateFacts` 의 두 검사
(`:192` 필드 소속, `:207` 집합 일치)가 **모두 건너뛰어지고**, `TEXT_FACTS`/`LIST_FACTS` 중
아무 키나 1~8개 넣을 수 있다. 물음에 맞는 키를 **직접 골라야 한다.**

라운드 상한은 `app/tasks/concept_portfolio_v2/models.py:220` 의
`continuationArtifacts ... max_length=5` 다.

### 12.8 ⚠ `confirmedFacts` 는 그 필드를 **덮어쓴다** — 병합이 아니다

이어가기를 라운드로 돌 때 **가장 쉽게 밟는 함정**이다. 실측으로 확인했다.

C2 후보의 `candidate.physicalActivities` 를 라운드마다 뽑아 보면:

| 시각 | 내용 |
|---|---|
| 06:23:28 (1라운드 답 반영) | 포장재 회수 정책 4줄 |
| 06:35:52 (2라운드 답 반영) | 식품유형·제조·냉장·소비기한 4줄 — **1라운드 4줄이 사라졌다** |

그래서 3라운드가 **1라운드와 사실상 같은 질문**(회수 대상 포장재의 소재·식품 접촉·처리 방식)을
다시 물어 왔다. 법률검토가 고집을 부린 것이 아니라 **답이 지워져서 다시 물은 것**이다.
겉보기에는 「AI 가 같은 걸 계속 묻는다」로 보이므로 원인을 엉뚱한 데서 찾기 쉽다.

**규칙: 같은 필드에 이어서 답할 때는 앞 라운드에 확정한 내용을 매번 다시 실어라.**

확인 방법(공짜):

```sql
select to_char(tr.started_at,'HH24:MI:SS'),
       res.result_json::json->'continuationArtifact'->'candidateSnapshot'
              ->'candidate'->>'physicalActivities'
  from task_runs tr join task_results res on res.id = tr.final_result_id
 where tr.project_id = 2 and res.result_json::json->>'candidateId' = 'C2'
 order by tr.created_at;
```

### 12.9 로그가 없는 실패 경로 — `catch (ContractViolation)`

`ConceptPortfolioContinuationWorker.java:84-88` 은 `ContractViolation` 을 잡아
`AI_RESULT_INVALID` 로 발행하는데 **로그를 한 줄도 남기지 않는다.**
같은 파일의 다른 갈래(`:98-100` catch-all)는 `log.warn` 을 남긴다.

그래서 이 경로로 죽으면:

- 백엔드 로그: **아무것도 없다**
- AI 로그: `POST /internal/v1/ai/executions 200 OK` — 정상으로 보인다
- DB: `task_runs.state=FAILED`, `last_error_code=AI_RESULT_INVALID`,
  `final_result_id`= **null**(계약 검증이 저장 전에 돌아서 응답 본문이 안 남는다)

**응답을 볼 방법이 아무 데도 없다.** 진단하려면 저장된 `input_snapshot_json` 으로
AI 를 다시 불러야 하는데 그것은 유료다. `ConceptPortfolioContinuationResultContract`
가 검사하는 것은 `contract`/`contractVersion`/`schemaVersion` 문자열과 outcome 별
`candidateId`·`lineageId` 일치다(`:22-38`).

> 고칠 값어치가 있는 자리다. `failContract` 앞에 `log.warn` 한 줄이면 유료 재현이 필요 없어진다.

### 12.10 가설 생성기가 확정된 사실을 되돌린다 — 반드시 대조할 것

컨셉을 고르면 가설 7종이 나오는데, 그중 둘이 **바로 앞 단계에서 법률검토를 통과시킨 사실과
정면으로 모순**됐다.

| 가설 | AI 가 낸 값 | 확정된 사실 |
|---|---|---|
| `REVENUE_MODEL` | 「포장재 회수는 기본 배송에 포함하고」 | 회수 대상 포장재가 없다 |
| `DIFFERENTIATORS` | 「회수 가능한 냉장 포장재 옵션을 차별화 요소로」 | 역물류 경로를 운영하지 않는다 |

컨셉 이름의 「순환」에서 되끌어온 것으로 보인다. **그대로 확정하면 모듈 2·3·6 이 존재하지
않는 역물류를 원가에 얹고 계산한다.** 실패가 아니라 «틀린 숫자로 성공»이라 더 위험하다.

> **가설 7종은 확정 전에 앞 단계의 `confirmedFacts` 와 한 줄씩 대조하라.**
> `PRE_MARKET_SOM` 같은 수치는 산수도 확인한다(이번 판은 30만명 × 5만원 × 12개월 = 1,800억으로 맞았다).

고치는 법: `POST …/hypotheses/confirm` 의 `changes` 에 `{가설타입: 새 값}` 을 넣는다(최대 7).
`CONFIRM_HYPOTHESES` 는 `engine.confirm_hypotheses` 로 **AI 호출 없이 결정적으로** 돈다 — 공짜다.

### 12.11 사용자 수정은 델타 법률검토를 부르고, 그것은 라운드로 돈다

`changes` 로 값을 고치면 그 가설이 `USER_EDITED_ACCEPTED` 가 되고
`legalImpact = DELTA_REVIEW_REQUIRED` 로 **델타 법률검토가 걸린다**(약 26초, 유료).
그 검토는 한 번에 통과하지 않고 **설계 공백을 하나씩** 되묻는다.

| 라운드 | `redesignRequirements` | 어디서 나왔나 |
|---|---|---|
| 1 | PP·PET 용기·아이스팩·종이 상자의 재질·구조·식품 접촉 용도·공급업체 적합성 자료 | 「포장재는 전량 일회용」이라고 쓴 데서 |
| 2 | 수요예측·개인화 추천을 내부 운영 전용인지 / 고객에게 자동화된 결정을 주는지 / 외부에 SaaS 로 파는지 | 「선택 데이터를 생산계획에 연결」이라고 쓴 데서 |

**`retryDelta` 를 쓰지 마라 — 같은 입력이면 같은 답이 나온다.** `confirm` 에는 상태 가드가
없으므로(`lockedCurrent` 만) `DELTA_LEGAL_FAILED` 에서 **다시 `confirm`** 해서 공백을 메우면 된다.

진단은 DB 에서 공짜로 볼 수 있다. `deltaLegalStatus=FAILED` 는 시스템 실패가 아니라
**판단 결과**다(작업은 `SUCCEEDED`, AI 는 200):

```sql
select k, left((j->'deltaLegalResult'->'legalReview'->k)::text, 900)
  from (select res.result_json::json j from task_runs tr
          join task_results res on res.id = tr.final_result_id
         where tr.project_id = 2
           and tr.task_type = 'CONCEPT_PORTFOLIO_V2_SELECTION_ACTION'
           and res.result_json::json->>'action' = 'DELTA_LEGAL'
         order by tr.created_at desc limit 1) t,
       lateral json_object_keys(j->'deltaLegalResult'->'legalReview') k
 where k in ('route','productionStatus','redesignRequirements','requiredControls',
             'designGapCount','legalSourceStatus');
```

### 12.12 컨셉 선택은 트윈 뱅크로 «먼저» 검증하라 — 실측

컨셉 3개의 타깃 축을 뱅크 8,604장에 직접 세었다. 인터뷰가 되는 축은 **하나뿐이었다.**

| 낱말 | 출현 |
|---|---|
| `1인 가구` | **704회** |
| `임금 근로자` | 4,198회 |
| `야근` · `교대` · `초과근무` | **0회** |
| `예산` | **0회** |
| `직장인` | **0회** |

| 조건 | 인원 |
|---|---|
| 1인 가구 20~49세 | **85명** |
| 1인 가구 25~44세 | 65명 |
| 1인 가구 20~39세 | 42명 |

- C4 「야근 직장인 주중 식사 안전망」 → 정의적 축(야근·교대)이 **0회**. 골랐으면 §8.1 의
  「맞벌이 0명」 함정을 그대로 다시 밟았다.
- C1-FC1 「1인분 식사 예산관리 구독」 → 「예산」 축도 **0회**.
- **C2 「냉장고 비우는 1인분 반찬 순환 구독」** → 가구원 수 축으로 실제로 85명이 뽑힌다. 선택.

> ⚠ **`직장인` 도 0회다.** 뱅크는 `임금 근로자`로 쓴다. 세 컨셉 전부 이름에 「20~40대 직장인」이
> 들어가므로 모듈 4 에서 `jobKeywords: ["직장인"]` 이 생성되면 **어느 컨셉이든 0명**이 된다.
> 인터뷰 실행 직전에 `condition_matches` 의 축별 수와 「전부 동시에 만족」 줄을 반드시 볼 것.

재현: `scratchpad/bankprobe.py`

---

## 14. 모듈 1 완주 — 1→2 이음매 검증 결과

### 14.1 §4 가 고쳤다는 결함은 정말 고쳐졌다

「다듬어진 두 칸이 시드에 영영 못 실린다」(`ConceptPortfolioSelectionService` 의
`BUILD_HANDOFF` 오버레이 결함)는 **해결돼 있다.** 실측:

시드 `snapshot.finalHypotheses` 에 사용자가 고친 값이 그대로 실렸다 —
`revenueModel.value` 에 「생산자책임재활용」, `differentiators.value` 에 「자동화된」.
`snapshot` 의 최상위 키는
`{contract, schemaVersion, snapshotId, projectId, selectionId, conceptId, createdAt,
sourceSnapshotHash, originalSeed, aiInterpretation, selectedConcept, finalHypotheses, legalResult}` 다.

### 14.2 ⚠ 그러나 컨셉 «본문»은 갱신되지 않는다 — 시드가 자기모순을 담는다

이어가기의 `confirmedFacts` 는 `physicalActivities` 같은 **사실 필드만** 갱신하고
`solution.featureSet` · `operation.actorRoles` · `partnerModel` · `transactionFlow` ·
`paymentFlow` · `partnerRequirements` 는 **손대지 않는다.**

그 결과 이번 시드는 이렇게 생겼다:

| 시드 위치 | 내용 |
|---|---|
| `selectedConcept.solution.featureSet[8]` | 「회수 가능한 포장재 회수 신청」 |
| `selectedConcept.operation.actorRoles[5]` | 「포장재 회수 파트너」 |
| `selectedConcept.operation.partnerModel` | 회수·세척·재활용을 전문 파트너에 위탁 |
| `selectedConcept.operation.paymentFlow[5]` | **회수 파트너에 처리량 기준 수수료 지급** |
| `selectedConcept.operation.physicalActivities[4]` | **「회수 대상 포장재가 없다 · 역물류 경로를 운영하지 않는다」** |
| `legalResult.prohibitedVariants[5]` | **「실제 회수·처리 경로가 없는 상태에서 포장재 회수·세척·재활용을 제공한다고 표시하는 운영」** |

**같은 시드 안에서 법률검토가 금지한 상태를 컨셉 본문이 서술하고 있다.**

파급 범위는 BM 어댑터에서 확인된다 — `research2/service/bm_adapter.py:223-224`:

```python
"key_resources": ("platformRole", "featureSet"),
"key_partners":  ("partnerModel", "partnerRequirements"),
```

→ BM 캔버스가 **존재하지 않는 「포장재 회수 파트너」를 핵심 파트너로**, 「회수 신청」을
핵심 자원으로 싣는다. 그리고 모듈 3 재무와 모듈 6 제안서가 그 BM 을 읽는다.
실패가 아니라 «틀린 구조로 성공»이라 화면에는 아무 경고도 안 뜬다.

**사용자 결정: 그대로 진행하고 모듈 2 의 세 번째 걸음인 「컨셉 다듬기」에서 걷어낸다.**
(모듈 2 = 시장조사 → BM → 다듬기 이고, 다듬기가 `featureSet` 을 고치는 자리가 맞다 — §4)

> 되풀이하지 않으려면: **컨셉 본문이 전제하는 것과 어긋나게 `confirmedFacts` 를 답하지 마라.**
> C2 는 이름부터 「순환」이라 설계가 회수를 전제하는데 「회수 없음」으로 답해서 벌어진 일이다.
> 답하기 전에 `selectedConcept.operation.*` 을 먼저 읽고 정합을 맞추는 편이 싸다.

### 14.3 `BUSINESS_VALIDATION` 은 이 트리에서 사문이다

`MarketResearchController.java:46-56` 이 명시한다 —
「사업 검증(`POST /business-validation`, 한 실행에 FULL+BM)은 **여기에 없다.**
여정 2번은 main 의 두 실행(`/market-research` → `/business-model`)을 그대로 쓴다.」

남아 있는 것은 `TaskType.BUSINESS_VALIDATION` · `BusinessValidationWorker` ·
`MarketResearchRun.Kind.VALIDATION` · `ai/app/validation/runner.py` 넷뿐이고 호출부가 없다.

→ §12.2 의 타임아웃 표에서 **실사용 경로는 `MARKET_RESEARCH`** 다. 그쪽도
`MarketResearchWorker.java:60` `BUDGET=60분` 이라 22분 수정은 그대로 유효하다.

### 14.4 모듈 1 실측 총계

| 단계 | 소요 | attempt |
|---|---|---|
| 아이디어 브리프 도출 | 0:04 | 1 |
| 컨셉 포트폴리오 생성 | 6:45 | 1 |
| 이어가기 × 8 (열린 입력 3라운드) | 0:39 · 1:18 · 0:40 · 0:48 · 0:46 · 0:43 + 실패 2 | 각 1 |
| 컨셉 선택 → 가설 7종 생성 | 0:26 | 1 |
| 가설 확정 + 델타 법률검토 × 4라운드 | 각 0:26 안팎 | 각 1 |
| 법률·규제 보고서 확정 | 즉시(200) | — |
| 시장분석 시드 확정 (`BUILD_HANDOFF`) | **0:08** | 1 |

산출: 컨셉 3개(전부 `selectable`·법률 `ACCEPT`) · 선택 C2 ·
법률보고서 `66a8dc83…`(`sha256:1849cf96…`) · 시드 `62d9c4da…`(`sha256:5465ed8f…`)

---

## 15. 모듈 2 가 1분 만에 죽었다 — `.env` 의 빈 `OPENAI_BASE_URL`

### 15.1 증상과 진짜 원인

```
run 1·2·3 · FULL · state=FAILED · errorCode=EXECUTION_FAILED · 각 30~60초
```

타임아웃과 무관하다(63분 예산에서 30초 만에 죽었다). 비싼 수집(약 470콜)에 **들어가기 전에**
죽어서 금전 손실은 작았다. 진짜 원인:

```
openai.APIConnectionError: Connection error.
slot_harness.HarnessError: LLM 호출 실패 (시도 1/3) — APIConnectionError: Connection error.
app.research.pipeline._Hard: harness 실패 — HarnessError: ...
```

`.env` 에 **`OPENAI_BASE_URL=` 이 빈 값으로** 있었다. `research2/harness/slot_harness.py:561` 은
**인자 없는 `OpenAI()`** 를 쓰고, openai SDK 는 환경변수를 직접 읽는다. 실측:

| `OPENAI_BASE_URL` | SDK 의 `base_url` |
|---|---|
| 미설정 | `https://api.openai.com/v1/` ok |
| **`""`** | **`''`** → 모든 요청이 상대 URL 로 나간다 → `APIConnectionError` |

런처(`.env` 를 통째로 export 하는 방식, PowerShell 판도 동일)가 **빈 값도 설정**하므로
「키가 없는 것」이 아니라 「키가 빈 문자열인 것」이 되어 SDK 가 걸려 넘어졌다.

**모듈 2 만 죽는 이유**: 다른 모듈이 쓰는 `app/providers/structured.py:47` 은
`AI_BASE_URL` 을 읽어 **비어 있으면 안 넘긴다.** `research2` 하네스만 SDK 의 환경변수
자동 읽기에 기댄다.

**조치**: `.env` 의 그 줄을 주석 처리했다. **값이 없으면 키 자체를 두지 마라.**

### 15.2 preflight 는 멀쩡했다 — **환경을 안 맞추고 돌린 것이 잘못이었다**

처음에 preflight 가 `openai engine=ok · harness=ok` 를 냈다. 그래서 도구에 사각지대가
있다고 적었는데 **틀렸다.** `tools/preflight.py:70-75` 는 주석까지 달아 가며
`OpenAI(api_key=key)` 로 실제 클라이언트를 만들어 호출한다.

같은 환경변수를 주고 다시 돌리니 정확히 잡는다:

```
$ OPENAI_BASE_URL="" python tools/preflight.py --need openai
  [unreachable ] openai  engine(OPENAI_API_KEY)=unreachable · harness(OPENAI_API_KEY)=unreachable
진입 금지 — 유료 판에 들어가지 않는다
```

통과한 이유는 하나다 — **AI 서버가 아니라 `.env` 를 안 올린 셸에서 돌렸다.**
그 셸에는 `OPENAI_BASE_URL` 이 아예 없었고, 없으면 SDK 가 기본값을 쓴다.

> **규칙: 점검 도구는 서버와 «같은 환경»에서 돌려야 뜻이 있다.**
> `scratchpad/preflight.sh` 가 `.env` 를 올린 뒤 부르도록 해 뒀다. 맨손으로 부르지 마라.

### 15.3 엔진 stderr 의 마지막 한 줄만 남기고 버린다

`product_pipeline.py:424` 는 서브프로세스 실패 시 stderr 의 **마지막 줄만** 오류로 쓴다:

```python
detail = stderr.decode("utf-8", "replace").strip().splitlines()
raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
            detail[-1] if detail else "시장조사 엔진 실패")
```

마지막 줄은 바깥쪽 예외(`ProviderFailure: TRANSIENT_EXECUTION_FAILURE`)라 **정보가 0** 이다.
진짜 원인(`_Hard` 메시지, `openai.APIConnectionError`)은 그 **위쪽** 줄에 있다.
`raise _fail(...)` 이 `from hard` 를 안 쓰므로 원본 트레이스백이 앞 구간으로 밀린다.

바로 위 `app/research/runner.py:65` 에 「예전엔 `detail` 을 받아 놓고 **버렸다** … 유료 BM
실행이 15초 만에 죽었는데 두 낱말뿐이라 원인을 끝내 못 밝혔다」는 주석이 있다.
**같은 함정이 이 자리에 남아 있었다.**

> 진단할 때: 그 한 줄을 임시로 넓혀서(예: `_Hard`·`Error:`·`During handling` 이 든 줄만 골라
> 이어 붙이기) 다시 돌리면 30초에 원인이 나온다. **확인 뒤 반드시 되돌린다** —
> 이 파일은 `origin/main` 과 바이트 동일이어야 한다.
> 백업: `scratchpad/product_pipeline.py.orig`

---

## 16. 모듈 2 완주 — 판단층이 실제로 판단한다

### 16.1 실측

| 실행 | 결과 | 소요 | attempt |
|---|---|---|---|
| MARKET_RESEARCH #1·2·3 | FAILED (`AI_SERVICE_UNAVAILABLE`) | 각 4~6초 | 1 |
| **MARKET_RESEARCH #4 (FULL)** | **SUCCEEDED** | **19:35** | 1 |
| **MARKET_RESEARCH #5 (BM)** | **SUCCEEDED** | **0:23** | 1 |

- FULL 이 끝나자 **BM 이 자동으로 이어 돌았다.** `/business-model` 을 따로 부르지 않았다.
- BM 이 23초인 것은 수집(evidence 982건)을 재사용하고 캔버스·판정만 새로 하기 때문이다.
- **19:35 는 옛 22분 한도를 2분 25초 차로 밑돈다.** 이번 판은 안 고쳤어도 살아남았을 수 있다.
  다만 470콜 규모에서 그 여유는 언제든 넘어간다 — 고친 것이 옳다.

### 16.2 2→3·5·6 이음매 — 통과

`FinancialPreparationFactory:85,104-105,206` 이 읽는 키가 FULL 판 결과에 전부 있다:

| 키 | 값 |
|---|---|
| `market` | dict(7) — `price`·`tam`·`sam`·`som`·`growth`·`notFound`·`coverageCaveat` |
| `market.price` | `base=5900` · `baseKind=MEDIAN_PROVISIONAL` · 관측 77건의 중앙값 |
| `evidence` | **982건** |
| `scorecard` | 10 |
| **`judgment`** | **dict(3)** |
| **`prescriptions`** | **list(2)** |

> BM 판 결과는 `market`·`judgment`·`prescriptions`·`report` 가 `None` 이다. **설계다** —
> 각 kind 가 자기 몫만 채운다(BM 은 `canvas.cells` 9칸 + `bm` dict(13)).
> **재무를 붙일 때 어느 버전을 읽는지 확인할 것** — BM 버전을 읽으면 `market` 이 비어
> `applyMarketDefaults` 가 조용히 기본값으로 떨어진다.

### 16.3 §4 의 진단이 실행으로 증명됐다

「`full` 이 모듈 2 의 판단층을 통째로 들어냈고 `judgment` 가 구조적으로 항상 `null` 이었다」 —
`main` 판으로 되돌린 결과, **판단층이 값을 낼 뿐 아니라 실제로 판단한다.**

BM 판정: `decision=REVISION_REQUIRED` · `confidence=MEDIUM` ·
`marketFitStatus=PARTIAL` · `consistencyStatus=PARTIAL`

그리고 **§14.2 에서 사람이 지적했던 회수 모순을 판단층이 스스로 찾아냈다** —
알려주지 않았는데:

> `consistencySummary`: 「… 그리고 **일회용 포장재·회수 없음과 회수 가능한 포장재·회수
> 파트너가 동시에 기재된 점** 때문에 내부 일관성은 부분 판정이다.」
>
> `risks[0]`: 「**일회용 포장재 운영과 회수 가능한 포장재 운영 설명이 충돌함**」

### 16.4 BM 캔버스 오염 — 예측대로 «한 칸»

`bm_adapter.py:224` 가 `key_partners ← partnerModel, partnerRequirements` 로 잇는 탓에:

```
[KEY_PARTNERS] 지역 냉장 배송사 · 결제대행사 · 권역 확대 시 냉장 물류 거점
               · 포장재 회수·세척·재활용 전문 파트너      <- 존재하지 않는 파트너
```

**그러나 `COST_STRUCTURE` 는 깨끗하다** — 「냉장 배송비 · 일회용 포장재 비용 ·
포장재 관련 생산자책임재활용 분담금 · 제조·소분·냉장 포장 비용」. 회수 수수료가 안 들어갔다.
`REVENUE_MODEL` 가설을 고쳐 둔 것이 비용 쪽을 지켰다. `KEY_RESOURCES` 도 오염되지 않았다.

→ **가설을 고치면 캔버스의 수익·비용 칸은 지킬 수 있다. 컨셉 본문에서 오는 칸
(`KEY_PARTNERS`)은 못 지킨다.** 그것은 다듬기의 몫이다.

### 16.5 판단층이 낸 다른 지적

- `gateReasons`: `G1 / CHANNELS / "출처가 컨셉 서술과 입력값뿐 — 시장 근거 0건" / UNCITED`
- `weaknesses`: 「경쟁사 분석 결과가 비어 있어 차별성 검증이 제한됨」
  ← 시작 전 `competitor-seeds` 가 이미 「경쟁 씨앗이 없습니다」로 경고했던 그것이다
- `legal.status`: `UNVERIFIED`

### 16.6 다음 단계 — 남은 사람 결정 관문

조회만으로 확인한 각 모듈의 진입 전제(유료 0회):

| 모듈 | 응답 |
|---|---|
| 3 재무 | 409 「current Market Research 결과가 필요합니다」 → 이제 충족됐다 |
| 3 기술운영 | 409 「입력 준비를 먼저 시작해 주세요」 + 「보유 자산·설비를 하나 이상 입력해 주세요」 |
| 4 인터뷰 | 404 「**다듬어진 컨셉을 확정한 뒤에** 인터뷰를 걸 수 있다」 |
| 5 마케팅 | `ready=true` 인데 `missingSources` 7종 — **순서를 안 막는다**(`REQUIRED=[CURRENT_CONCEPT]` 뿐) |
| 6 최종보고서 | `blockingSources:[MARKET, BUSINESS_MODEL]` → 이제 풀린다 |

⚠ **모듈 5 는 사람이 순서를 지켜야 한다.** 지금 눌러도 열리고, 컨셉·프로젝트명만으로
전략이 나온다. 모듈 6 은 제대로 막는다.

### 16.7 그 밖에 찾은 결함

필수 쿼리 파라미터가 빠지면 **400 이 아니라 500 「서버 오류가 발생했습니다」** 가 나간다
(`MissingServletRequestParameterException` 미처리 —
`GET /api/v2/projects/2/concept-refinement` 를 `selectionId` 없이 부르면 재현된다).
화면이 「파라미터가 빠졌다」와 「서버가 죽었다」를 구분해 말할 수 없다.

## 17. 모듈 2 확정 · 모듈 3 초안 — 결함 5건과 그 자리 (2026-08-19)

### 17.1 컨셉 재확정이 만드는 「죽은 시드」 연쇄

컨셉을 다시 확정하면 `MarketAnalysisSeedSnapshot` 이 **새로 발급**된다. 옛 시드는 `stale_at` 이 찍힌다.
그런데 하위 단계는 「처음 만들 때의 시드 ID」를 붙들고 있어, 그 ID 로 상위를 찾다가 조용히 실패한다.
**실패가 아니라 「자료 없이 성공」이라 화면에 경고가 안 뜬다.** 이 세션에서 이 연쇄로 4곳이 깨졌다.

| # | 자리 | 증상 | 고친 방법 |
|---|---|---|---|
| ① | `ConceptPortfolioSelectionMaterializationService` | 확정 0초 만에 `AI_RESULT_INVALID` — 실제로는 `uk_market_seed_portfolio_selection` 유니크 충돌 | 옛 시드 `markStale` 후 **`flush()`** — 없으면 ActionQueue 가 INSERT 를 UPDATE 앞에 놓아 재발 |
| ② | 〃 | `fk_market_research_run_seed` 위반 — 계보 UPDATE 가 새 시드 INSERT 보다 먼저 나감 | `saveAndFlush` 로 순서 강제 후 `carrySeedLineageForward` |
| ③ | `ConceptPortfolioSelectionService.finalizeMarketSeed` | 한 번 실패하면 상태가 `FAILED` 로 남고 되돌리는 문이 없어 영구 409 | 가설 7종이 모두 `ready` 일 때만 재시도 허용(`retryAfterFailure`) |
| ④ | `MarketAnalysisSeedSnapshot.createPortfolio` 11-arg 오버로드 | `refinement_applied` 가 **한 번도 true 가 안 됨** → 모듈 4 인터뷰 게이트가 영영 404 | 호출부가 `input.hasNonNull("refinementOverlay")` 를 넘기도록 수정 |
| ⑤ | `TechOpsService.initialize` | 준비값이 죽은 시드를 계속 가리켜 자문이 **빈 `marketResult` 로 유료 실행** | 확정 전·작업 없음일 때만 현재 시드로 재결속(`rebindToCurrentSeed`) |

⑤ 의 실제 경로 — `TechOpsAdvisorySourceResolver.resolve()` 는 시장조사를
`seed.getId().equals(run.getSourceMarketSeedSnapshotId())` 로 맞춘다. 시드가 어긋나면 `market = null`,
그러면 `TechOpsAdvisoryWorker:58-61` 이 **`marketResult` 와 `businessModelResult` 를 빈 객체로** 넣는다.
예외도 경고도 없다. 그래서 같은 파일에 **쓰이지 않고 놓여 있던 `unavailable()` 헬퍼를 연결해**
「시드는 있는데 시장조사가 없음」을 소리 나게 막았다.

> **원칙**: 상위가 재발급되는 구조에서는 하위가 ID 를 저장하지 말고 「현재 것」을 다시 물어야 한다.
> 저장해야 한다면 **어긋났을 때 조용히 비우지 말고 반드시 던져야 한다.**

### 17.2 5,500원 함정 — 실패가 아니라 「틀린 숫자로 성공」

`POST /finance/preparation/initialize` 직후 실측:

```
revenueModel             : HYBRID   (BUSINESS_MODEL_ASSUMPTION)
unitPrice                : 5,500원  (BUSINESS_MODEL_ASSUMPTION)
monthlySubscriptionPrice : 5,500원  (BUSINESS_MODEL_ASSUMPTION)   ← 함정
```

5,500원은 `market.price.base` 이고, 엔진 자신이 `baseKind: MEDIAN_PROVISIONAL` ·
「잠정 대표값(관측 표시가격의 중앙값)이다. **확정 단가가 아니다**」라고 적어 둔 값이다.
근거 9건의 정체는 **편의점 도시락 판매가와 외식 비용** — 우리 사업의 월 구독료(39,900~59,900원)와
자릿수가 다르다. 이대로 두면 **월 구독료 5,500원인 사업으로 손익이 계산된다.**

덮어쓰기 순서도 확인했다 (`FinancialPreparationFactory:150-152`):
`applyConceptDefaults` → `applyMarketDefaults` → `applyBusinessModelDefaults`, 뒤가 앞을 덮는다.

> **파생 결함(잠재)**: `applyConceptDefaults` 는 컨셉 가격 가설(자유 텍스트)에 `numericPrice()` 를 쓴다.
> 정규식 `([0-9][0-9,]*)` 이 **첫 숫자**를 잡으므로 「월 **4**종 구독 39,900원…」에서 **4** 를 집어
> `monthlySubscriptionPrice = 4원` 이 된다. 이번엔 뒤의 MARKET/BM 이 5,500 으로 덮어 드러나지 않았지만,
> `market.price.base` 가 없는 판에서는 **4원짜리 구독 사업**이 그대로 남는다.

막는 법은 하나뿐이다 — `decision` 이 `ASSUMPTION` 인 값은 준비 완료로 쳐주지 않는다
(`FinancialReadiness` 는 `LOCKED · ACCEPTED · USER_EDITED_ACCEPTED` 만 인정). **반드시 PATCH 로 덮어쓴다.**

### 17.3 모듈 3 은 기술·운영과 재무가 서로를 안 본다

`FinancialPreparationFactory:122` 주석이 명시한다 —
「새 Finance authority 는 TechOps 없이 current Market/BM 근거만으로 준비값을 만든다.」
그래서 재무 준비값의 `sourceTechOpsSnapshotId` 는 **null 이 정상**이다. 순서 함정이 아니다.
대신 두 쪽 숫자(고정비·초기투자·3개년 목표)를 **사람이 맞춰 넣어야** 일관된다.

### 17.4 자잘하지만 두 번 당한 것

- **한글 본문을 셸 `$(cat file)` 로 curl 에 넘기지 말 것.** UTF-8 이 깨져
  `Invalid UTF-8 start byte 0xbf` → 서버는 이걸 **500 `INTERNAL_SERVER_ERROR` · `retryable: true`** 로 돌려준다.
  요청이 잘못된 건데 재시도하라고 답한다. 항상 `--data-binary @파일` 을 쓴다.
- **`expectedMonthlyThroughputOrSales` 는 실행마다 크게 흔들린다** — 같은 시드로 12,000 → 1,000.
  AI 가설이므로 근거를 갖고 `EDIT_ACCEPT` 로 덮는 것을 기본으로 한다.
- `GET /tech-ops/preparation` 은 조회지만 `initialize` 가 준비 행을 만든다 — 조회 전에 반드시 initialize.

### 17.5 실측 (프로젝트 2)

| 단계 | 소요 | attempt |
|---|---|---|
| 시장조사 FULL | **20:08** | 1 |
| BM (자동 연쇄) | 0:24 | 1 |
| BM (중복 — 조작 실수) | 0:22 | 1 |
| 컨셉 다듬기 결정 | 0:03 | 1 |
| 컨셉 확정 `BUILD_HANDOFF` | 0:00 | 1 |
| `TECH_OPS_PROPOSAL` (초기) | 0:11 | 1 |
| `TECH_OPS_PROPOSAL` (재결속 후 재생성) | 0:11 | 1 |

컨셉 다듬기가 시드에 남긴 실제 변화는 **기능 1개 삭제**(「주간 식품 폐기 절감량 안내 기능」, 11→10개)와
`refinement_applied = true` 뿐이다. 나머지 필드는 옛 시드와 **바이트 단위로 동일**했다.

## 18. 모듈 3 완주 — 유료 실행 두 건과 그 앞을 막고 있던 벽 3개 (2026-08-19)

### 18.1 자문 봉투가 2 MB 상한에 막혔다 — 그런데 **고쳐서 막힌 것**이다

`POST /tech-ops/advisory-runs` → `400 TASK_RUN_INPUT_INVALID`.
`TaskRunService.validateCreation` · `InternalAiExecutionClient.MAX_JSON_BYTES` · AI 쪽
`main.py INTERNAL_JSON_MAX_BYTES` — **세 곳이 모두 2 MB** 다.

측정:

| | 총 크기 | 그중 `evidence` |
|---|---|---|
| 시장조사 FULL 결과 | 1,135 kB | **1,089 kB (95.9 %)** |
| BM 결과 | 1,104 kB | **1,089 kB (98.6 %)** |

두 `evidence` 는 1,086건으로 **해시까지 같았다** — 같은 근거 목록을 두 번 실어 보내고 있었다.

> ⚠ 이 벽은 §17.1 ⑤ 를 고치기 **전에는 나타나지 않는다.** 시드가 어긋나 있을 때는
> `marketResult` 가 빈 객체라 봉투가 작았기 때문이다. **결함을 고치자 비로소 진짜 제약이 드러났다.**

자문 엔진(`tech_ops_input_scaler._evidence`)은 근거에서 **URL 최대 24개**만 뽑아 쓴다.
그래서 `TechOpsAdvisoryService.shrinkEvidenceToFit()` 을 넣어 **중복본인 BM 쪽을 먼저 버리고**,
그래도 크면 시장 쪽을 뒤에서부터 줄인다. 버린 건수는 반드시 `log.info` 로 남긴다.

실측 결과 — `droppedEvidence=1086 bytes=1310598`. **BM 중복분만 버리고 시장 근거는 한 건도 안 잃었다.**

### 18.2 재무 AI 보고서는 이 트리에서 **한 번도 성공한 적이 없었다**

첫 실행이 **0.067초**에 `SUCCEEDED` 로 끝났다. AI 를 부른 시간이 아니다.
`fallback: true` · `providerStatus: FAILED` · `safeFailureReason: AI_SERVICE_UNAVAILABLE`.

진짜 원인은 **봉투 계약 불일치**였다:

| 보내는 쪽 | 받는 쪽 |
|---|---|
| `FinancialAnalysisService:66` 이 `sourceBinding` 을 **항상** 실어 보낸다 | `FinanceAnalysisReportInput` 은 `StrictModel(extra="forbid")` — 이 칸이 **없다** |

→ pydantic `ValidationError` → `ProviderFailure("INVALID_REQUEST", 400)` → 워커의 `safeReason()` 이
매핑되지 않은 코드를 전부 **`AI_SERVICE_UNAVAILABLE`** 로 접는다 → 조용히 fallback 보고서.

`sourceBinding: dict | None = None` 한 줄로 고쳤다(값은 받아두고 프롬프트엔 넣지 않는다 —
보고서 본문은 `deterministicResult` 만 쓴다). 고친 뒤 실행 시간 **0.067초 → 5~6초**,
`fallback: false` · `source: AI_GENERATED_REPORT`.

> **읽는 법**: 재무 분석의 `SUCCEEDED` 는 AI 성공을 뜻하지 않는다. **반드시 `fallback` 을 같이 본다.**
> `safeReason()` 이 원인을 뭉개므로 실패 코드도 액면 그대로 믿으면 안 된다.

### 18.3 「공헌이익 단가가 0 이하」 — HYBRID 가 만든 단위 불일치

첫 보고서의 `keyRisks`: 「공헌이익 단가가 0 이하라 손익분기점을 계산할 수 없습니다.」
공헌이익률은 25.79 % 로 **양수**인데 손익분기는 못 낸다 — AI 보고서가 이 모순을 스스로 짚었다.

원인은 계산 단위였다 (BASE 시나리오 m12 실측):

```
매출   = 활성구독 2,085 × 49,900원   ← 구독 단위
변동비 = 판매건  2,234 × 34,550원   ← 판매 건 단위
```

`revenueModel` 이 **HYBRID** 라 6,900원짜리 추가 반찬 1건에도 월 변동비 34,550원이 붙는다.
BM 이 HYBRID 로 분류한 것은 수익모델 **텍스트에 「판매」가 있어서**이지 재무 모델링 의도가 아니다.
`SUBSCRIPTION` 으로 바꾸자 경고가 사라지고 공헌이익이 정상화됐다 (m12 공헌 25.3M → 31.1M).

> 조건부 단위원가(`unitVariableCost`·`shippingCost` 등)를 **월 구독 1건 기준**으로 넣을 거라면
> `revenueModel` 은 `SUBSCRIPTION` 이어야 한다. HYBRID 를 쓰려면 단가 판매용 변동비를 따로 잡아야 한다.

### 18.4 실측 · 판정

| 단계 | 소요 | attempt | 비고 |
|---|---|---|---|
| `TECH_OPS_PROPOSAL` | 0:11 | 1 | |
| `TECH_OPS_ADVISORY` | **2:17** | 1 | 봉투 1,280 kB |
| `FINANCE_ANALYSIS_REPORT` (fallback) | 0:00 | 1 | 계약 불일치 |
| `FINANCE_ANALYSIS_REPORT` (정상) | 0:05 | 1 | |

타임아웃 행렬의 남은 ⬜ 두 칸도 채웠다 — 둘 다 안전하다:

| taskType | 클라이언트 | read-timeout | 워커 BUDGET | AI 쪽 | 판정 |
|---|---|---|---|---|---|
| `TECH_OPS_ADVISORY` | longRunning | 9m | 6m | 2콜 × 180s | ✅ |
| `FINANCE_ANALYSIS_REPORT` | 기본 | 6m | 3m | 1콜 | ✅ |

**기술·운영 자문**: `CONDITIONAL_GO` · layer1Facts 150건 · 근거 URL 18건 · 게이트 7개(전부 OPEN).
가격 근거가 편의점 도시락·외식 비용이라는 것, 채널이 미확인이라는 것, 냉동 간편식 38조는
인접 시장 상한이라 TAM 으로 쓰면 안 된다는 것을 **자문이 스스로 짚었다** — §17.1 ⑤ 를 고치기
전이었다면 이 문장들은 하나도 나오지 않았다.

**재무**: `LOSS_MAKING` — 36개월 내 손익분기·투자회수 미도달, 필요 운전자금 24.6억.
활성 구독이 **m36 에 3,787명에서 정체**하는 것이 병목이다. 손익분기는 5,537 구독인데,
신규 3,500명/년 · 월 이탈 4.5 % 의 균형점이 그 아래다. **마케팅 예산이 목표 구독 수를 못 산다.**

## 19. 모듈 4 완주 — 타겟 0명 함정은 재발하지 않았다 (2026-08-19)

### 19.1 누르기 전 실측 — 표본 크기를 여기서 정했다

`sampleSize` 는 {20, 40, 80} 셋 중 하나다. 뱅크 8,604장을 직접 세어 풀을 먼저 쟀다:

```
만 20~49세                2,916명
1인 가구                    704명
20~49세 & 1인 가구            85명   ← 우리 타겟 풀
   그중 임금 근로자             75명
```

80 을 고르면 타겟 요청이 64명이라 85명 풀의 75 % 를 긁는다. 조건식이 조금만 좁아져도
(예: 직업 축이 붙어 풀이 75명이 되면) 모자란 만큼이 **조건 밖에서 조용히 채워진다** —
2026-08-15 에 실제로 당한 그 사고다. **40 을 골랐다.**

실행 후 엔진이 낸 `criteriaText` 는 「만 20~49세(2,916명) / 1~1인 가구(704명) →
전부 동시에 만족: **85명**」 — **내가 따로 센 값과 정확히 같다.** 계수기가 맞게 붙어 있다.

### 19.2 조건식이 태도·직업을 제대로 버렸다

컨셉보드의 `targetUsers` 는 「**식품 폐기와 냉장고 잔반에 민감한** 혼자 사는 20~40대 **직장인**」이다.
LLM 이 낸 조건식:

```
ageMin 20 · ageMax 49 · householdSizeMin 1 · householdSizeMax 1
genders [] · regions [] · incomeKeywords [] · jobKeywords [] · hasChildren 0 · householdRoles []
```

「식품 폐기에 민감한」은 **행동·태도라 패널에 칸이 없고**, 「직장인」은 «직업 목록» 에 없는 말이다.
둘 다 버렸다 — `targeting.py` 가 지시한 그대로다. 이걸 억지로 `jobKeywords` 에 넣었다면
「맞벌이」와 똑같이 0명이 되어 조사 전체가 헛돌았다.

결과: `targetRequested 32 / targetDrawn 32`, `nonTargetRequested 8 / nonTargetDrawn 8`,
**`shortfall 0`**, `targetShortCells {}`. 비타겟 8명은 사고가 아니라 **설계된 대조군**이다.

### 19.3 결과 — 그리고 이 조사가 답하지 **않는** 것

표본 40 · 응답 40 · 주제 30 · **오해 0** (이해 정확 38 · 부분 2) · 유의사항 11.

| 축 | 주제 | 전체 | 타겟(32) | 비타겟(8) |
|---|---|---|---|---|
| LIKE | 1인분 소분으로 음식물 쓰레기 감소 | 40 | 32 | 8 |
| LIKE | 메뉴 변경·기호 재료 선택 | 28 | 23 | 5 |
| CONCERN | **월 39,900원 구독료 부담** | **35** | **27** | 8 |
| CONCERN | 구독 중 못 먹는 반찬 발생 | 24 | 21 | 3 |
| CONCERN | 가격 대비 반찬 양·식사 횟수 불확실 | 20 | 17 | 3 |

차별성 판정: `different 28 · similar 0 · unclear 12`.
현재 대안: 마트·반찬가게 10 · 편의점 10 · 배달 8 · 직접 장보기 7.

> ⚠ **가격 부담 35건을 지불의사로 읽으면 안 된다.** 봉투의 유의사항이 못박는다 —
> 「**가격 수용도·지불의사는 이 조사가 답하지 않는다** — 지불의사의 임계는 응답자가 아니라
> 실행 모델이 정하고, 모델을 바꾸면 방향까지 뒤집히는 것이 실측됐다」.
> 같은 문단이 「**언급 수를 백분율로 환산하지 마라**」고도 적었다. 그래서 위 표는 건수로만 적는다.
> 합성 응답자는 「우려의 강도는 과소, 호감은 과대」로 나온다는 경고도 함께 있다.

### 19.4 내가 낸 사고 — 한글 경로가 환경변수에서 깨졌다

첫 실행이 0초에 `TWIN_BANK_UNAVAILABLE` 로 죽었다. 뱅크 파일은 멀쩡히 있었다.
원인은 **내가 AI 서버를 재기동한 방식**이었다 — PowerShell `Get-Content` 가 `-Encoding utf8`
없이 시스템 ANSI(cp949)로 `.env` 를 읽어, `TWIN_BANK_DIR=C:/Users/A/Desktop/**빅프**/ai/app/twin/bank`
의 한글이 깨진 채 자식 프로세스로 넘어갔다.

> **규칙**: `.env` 를 PowerShell 로 읽어 프로세스에 넘길 때는 **반드시 `-Encoding utf8`**.
> 경로에 한글이 있으면 이 실수가 「파일이 없다」로 나타난다.
> §17.4 의 curl UTF-8 사고와 **같은 뿌리**다 — 한글이 도구 경계를 넘을 때마다 깨진다.

다행히 `TWIN_BANK_UNAVAILABLE` 은 `retryable=false` 이고 0초에 죽어 **돈은 들지 않았다.**
뱅크 없이 조용히 빈 표본으로 도는 대신 시끄럽게 죽도록 만든 `bank.py` 의 설계가 그대로 작동했다.

### 19.5 실측

| 단계 | 소요 | attempt |
|---|---|---|
| `MARKET_INTERVIEW` (뱅크 경로 깨짐) | 0:00 | 1 · 무과금 |
| `MARKET_INTERVIEW` (정상, n=40) | **0:32** | 1 |

타임아웃: twinSurvey 클라이언트 14m > 워커 BUDGET 10m ✅

## 20. 모듈 5 — 근거 뭉치가 세 번째로 길을 막았다 (2026-08-19)

### 20.1 기존 실패 테스트는 모듈 5 를 막지 않는다 (계획 §2-A 미결 판정)

`test_marketing_content_contract.py` 의 실패 1건을 **모듈 5 를 누르기 전에** 판정했다.

```
MarketingSourceSnapshot    OK
MarketingContentInput      ['schema.strategy.anyOf:unconstrained-object']   ← 실패 원인
MarketingContentResult     OK
```

실패는 **입력 모델**의 `strategy: dict[str, JsonValue] | None` 한 칸이다.
OpenAI 에 `response_format` 으로 나가는 것은 `MarketingContentResult` 이고 **그쪽은 깨끗하다.**
게다가 이 결함은 「너무 느슨한」 쪽이라 §18.2 의 `sourceBinding`(너무 빡빡해서 400) 과 **방향이 반대**다.
→ **실행을 막지 않는다.** 다만 입력 통과분이 무제한 객체인 것은 그대로 남아 있다.

### 20.2 413 — 같은 근거 뭉치, 세 번째 벽

`POST /marketing-strategy/generate` → `413 MARKETING_STRATEGY_SOURCE_TOO_LARGE`.
`MarketingStrategyService.MAX_INPUT_BYTES = 1,800,000` 인데, 소스 카탈로그의
`MARKET`(1,135 kB)·`BUSINESS_MODEL`(1,104 kB)이 각자 **같은 1,089 kB 근거 목록**을 물고 온다.

이로써 같은 뭉치가 막은 자리가 셋이다:

| 자리 | 상한 | 낸 오류 |
|---|---|---|
| 기술·운영 자문 (§18.1) | 2 MB (범용) | `400 TASK_RUN_INPUT_INVALID` — 무엇이 큰지 안 알려 준다 |
| 마케팅 전략 | 1.8 MB (전용) | `413 MARKETING_STRATEGY_SOURCE_TOO_LARGE` — **이름이 원인을 말한다** |
| 최종 보고서 (모듈 6) | 2 MB (범용) | 아직 안 눌러 봄 — **같은 벽이 기다린다** |

### 20.3 왜 마케팅에서는 버려도 되고 최종 보고서에서는 안 되는가

**인용 단위가 다르다.**

- 마케팅 전략: `marketing_strategy/service.py` 의 `allowed_refs` 가 **`sourceManifest` 의 `TYPE:id`**
  (`MARKET:1`·`BUSINESS_MODEL:3`)로만 만들어진다. 개별 근거 레코드를 인용하지 않는다.
  → 원자료 목록을 비워도 인용은 안 깨지고, 판단에 쓰는 알맹이(`market`·`scorecard`·`report`·
  `synthesis` 등 **46 kB**)는 그대로 남는다.
- 최종 보고서: `BusinessProposalEvidenceCatalog` 가 **`evidenceKey` 단위**로 카탈로그를 만들고,
  본문이 그 키를 인용한다. 키가 없으면 `FINAL_REPORT_EVIDENCE_KEY_INVALID` 로 죽는다.
  → **여기서 근거를 버리면 안 된다.**

그래서 정리를 `MarketingStrategySourceService.stripRawEvidence()` 에만 넣었다.
공용인 `FinalReportService` 의 카탈로그는 **건드리지 않았다.** 버린 건수는 `evidenceOmitted` 로
봉투에 남기고 `log.info` 로도 남긴다 — 조용히 줄이면 「자료가 원래 그만큼」으로 읽힌다.

`sourceManifestHash` 는 `catalog.strategySourceHash()` 로 따로 계산되므로
정리해도 **reportId 는 변하지 않는다.**

### 20.4 근거 뭉치는 **네 벌**이었다

재귀로 훑고 나서야 진짜 숫자가 나왔다 — `droppedEvidence=4344` = **1,086 × 4**.

맨 위 두 벌만 지웠을 때(`2172`)도 여전히 413 이었다. 진단은 상한만 보고는 불가능했고,
소스별 크기를 찍고 나서야 드러났다:

```
total=2438kB  FINANCE=2118kB  MARKET_INTERVIEW=159kB  CURRENT_CONCEPT=53kB
              MARKET=45kB  FINANCE_REPORT=43kB  BUSINESS_MODEL=14kB  PROJECT=0kB
```

**`FINANCE` 하나가 2,118 kB.** 재무 준비값의 `upstreamReferences` 가
`marketAnalysis.evidence`(1,089 kB)와 `businessModel.result`(BM 결과 **통째로**, 그 안에 또 1,089 kB)를
품고 있었고, 그게 재무 입력 Snapshot 에 그대로 굳었다(`FinancialPreparationFactory`).

> **교훈**: 상위 결과를 「참조용」으로 스냅샷에 통째로 박으면, 그 스냅샷을 읽는 **모든** 하위 단계가
> 같은 무게를 진다. 그래서 정리는 맨 위만 훑지 말고 **트리 전체**를 훑어야 한다.
> 그리고 상한에 걸렸을 때 **어느 소스가 큰지 찍어 주지 않으면 고칠 수가 없다** — 진단 로그를 남겼다.

### 20.5 `SAFETY_POLICY_BLOCKED` — 안전 위반이 아니라 프롬프트·검증기 불일치

콘텐츠 생성이 6초 만에 `AI_SERVICE_UNAVAILABLE` 로 실패했다. **또 가면이었다** (§18.2 와 같은 수법).
`task_attempts.normalized_error_reason` 이 진짜를 들고 있었다 — **`SAFETY_POLICY_BLOCKED`**.

`marketing_content/service.py:60-64` 의 검증기는 이렇게 판정한다:

```python
applied = {c.casefold().strip() for c in result.legalReview.requiredDisclosuresApplied}
for disclosure in value.source.requiredDisclosures:        # 이 판에서 5건, 각 40~90자
    if required not in applied and required not in rendered:
        raise SAFETY_POLICY_BLOCKED                        # 문자열 완전 일치
```

그런데 프롬프트는 「apply every **relevant** requiredDisclosure in the copy, and report that
application」이라고만 했다. **「relevant」는 골라내도 된다는 뜻이고 「report」는 그대로 베끼라는 뜻이 아니다.**
인스타그램 게시글에 법정 고지 5문단이 들어갈 자리도 없다.
→ 모델이 요약하거나 일부만 적으면 **매번 terminal 실패**한다. 안전 위반이 아니라 계약 불일치다.

프롬프트를 검증기에 맞췄다 — 「exact string match, not by judgement」, 「Copy EVERY entry verbatim
… into `legalReview.requiredDisclosuresApplied`」, 「compliance ledger, not copy」.
게이트는 **하나도 안 느슨하게 했다.** 결과: `compliant: true`, 고지 **5/5 그대로 적재**, 필수 문구 3/3,
금지 문구 0건, 키비주얼 이미지 1건 생성.

### 20.6 실측

| 단계 | 소요 | attempt | 비고 |
|---|---|---|---|
| `marketing-source-snapshots/finalize` | 즉시 | — | 시드 `8e0cfd34` 정상 결속 |
| `MARKETING_STRATEGY_GENERATION` | **0:39** | 1 | 소스 7종 전부 인용 |
| `MARKETING_CONTENT_GENERATION` (고지 불일치) | 0:06 | 1 | terminal · 이미지 전 차단 |
| `MARKETING_CONTENT_GENERATION` (정상) | **2:47** | 1 | 이미지 생성 포함 |

전략이 인용한 근거: `CURRENT_CONCEPT` · `MARKET:1` · `BUSINESS_MODEL:3` · `MARKET_INTERVIEW:1` ·
`FINANCE` · `FINANCE_REPORT` · `PROJECT:2` — **모듈 2·3·4 결과가 전부 실제로 닿았다.**
`missingSources` 는 `LAUNCH_TECHNOLOGY`·`LAUNCH_OPERATIONS` 둘뿐이고 이들은 OPTIONAL 이다.

## 21. 화면에서 안 보이던 것들 — 세 가지를 고치고 크롬으로 확인 (2026-08-19)

API 로만 돌리면 「됐다」고 착각한다. 화면을 열어 보니 세 가지가 어긋나 있었다.

### 21.1 생성된 키비주얼을 꺼내 볼 길이 없었다

이미지는 만들어져 저장까지 됐다(`backend/data/objects/ai-artifacts/{uuid}.jpg`, 168 kB).
그런데 화면은 깨진 아이콘만 보여 줬다. 경로를 따라가 보면:

```
프론트   <img src="ai-artifacts/7d685f43-….jpg">          ← 저장소 «키» 를 URL 로 그대로 씀
브라우저 GET /app/projects/2/ai-artifacts/7d685f43-….jpg   ← 상대경로로 풀림
응답     HTTP 200  content-type: text/html                 ← Vite SPA 폴백(index.html)
결과     JPEG 로 못 읽음 → 깨진 이미지
```

백엔드에 `ai-artifacts/*` 를 **서빙하는 엔드포인트가 아예 없었다.** 넣는 길
(`POST /internal/v1/ai/marketing-artifacts`)과 키 형식 정규식만 있고 꺼내는 길이 없다.
`project_evidence_artifacts` 행도 안 만들어지므로(생성 이미지는 사용자 근거 자료가 아니다)
기존 다운로드 경로로도 못 꺼낸다.

고친 방법 — `GET /api/v3/projects/{projectId}/marketing-contents/{contentId}/image`.

> ⚠ **저장소 키를 파라미터로 받지 않는다.** 소유가 확인된 콘텐츠의 최신 개정에서 직접 꺼낸다.
> 키를 받으면 남의 프로젝트 이미지를 요청할 수 있다.

프론트는 Bearer 토큰이 필요해 `<img src>` 로 직접 못 부르므로 blob 으로 받아
`URL.createObjectURL` 로 바꾸고, 콘텐츠가 바뀌거나 언마운트될 때 `revokeObjectURL` 한다.
이 로직은 **페이지가 아니라 훅**(`useMarketingContent`)에 뒀다 — 페이지 테스트는
`ApiClientProvider` 없이 렌더링하므로 페이지에서 `useApiClient()` 를 부르면 기존 테스트 3건이 깨진다.

### 21.2 「시작 전」 배지 — 결과가 다 나오는데도

`TechOpsPage.jsx` 의 배지가 `{ QUEUED, RUNNING, COMPLETED, FAILED }` 만 매핑했다.
백엔드가 주는 것은 **`TaskRunState`** 이고 성공 값은 **`SUCCEEDED`** 다. 매핑에 없으니
기본값 「시작 전」으로 떨어졌다 — 자문 결과가 화면 가득 나오는데 배지는 시작 전이었다.
`TaskRunState` 8종(`QUEUED·READY·RUNNING·SUCCEEDED·NEEDS_INPUT·FAILED·CANCELLED·TIMED_OUT`)을
전부 매핑했다. `COMPLETED` 도 남겨 뒀다 — 다른 화면이 그 이름을 쓸 수 있다.

### 21.3 「출시 준비」의 빈 두 칸은 **다른 모듈**이었다

화면 3단계에는 칸이 셋인데, 내가 돌린 것은 그중 재무뿐이었다.

| 화면의 칸 | 실제 모듈 | 입력 방식 |
|---|---|---|
| 기술 분석 | `LAUNCH_TECHNOLOGY` | **DOCX 업로드** |
| 운영 분석 | `LAUNCH_OPERATIONS` | **DOCX 업로드** |
| 재무 분석 | `FINANCE_ANALYSIS_REPORT` | 재무 준비값 |

`/tech-ops` 의 「기술·운영 자문」(`TECH_OPS_ADVISORY`)은 **이 화면에 링크되지 않는 별도 페이지**다.
마케팅 전략이 `missingSources` 로 정확히 이 둘을 집었던 것이 신호였는데 화면을 안 봐서 놓쳤다.

DOCX 20칸(기술 10·운영 10)을 확정 사실만으로 작성해 채웠다. 템플릿 구조는
표마다 `w:tr[0]` = `fieldKey: xxx`, `w:tr[2]` = 값이다.

> ⚠ **한 번 헛디뎠다.** 값 칸을 찾는 정규식을 `<w:t[^>]*>` 로 썼더니 **`<w:tblPr>` 까지 잡아**
> XML 이 깨졌고 백엔드가 「DOCX 템플릿 형식을 읽을 수 없습니다」로 400 을 냈다.
> `<w:t(?:\s[^>]*)?>` 로 고쳤고, 이후로는 파일을 쓰기 전에 `ET.fromstring` 으로
> **well-formed 여부를 먼저 확인**한다 — 깨진 XML 을 내보내고 나서 서버 오류로 알게 되면 늦다.

### 21.4 결과 · 실측

| 실행 | 소요 | attempt | 결론 |
|---|---|---|---|
| `LAUNCH_TECHNOLOGY_READINESS` | 1:00 | 1 | **보완 후 재검토** · 34점 · P0 3건 |
| `LAUNCH_OPERATIONS_READINESS` | 1:00 | 1 | **조건부 준비** · 62점 · P0 3건 |
| `MARKETING_STRATEGY_GENERATION` (재생성) | 0:44 | 1 | 근거 **9종 전부** 인용 |

기술 34점은 낮은 것이 아니라 **정직한 것**이다 — 입력 문서에 출시 필수 기능 7개를 전부
「미착수」로 적었고(2027-04 출시 계획이므로 사실이다), 평가가 그것을 그대로 P0 차단으로 집었다.

기술·운영이 생기자 마케팅 전략이 **자동으로 `STALE`** 이 됐다. 상위가 늘면 하위가 낡는다는
신호가 제대로 작동한 것이다. 재생성하니 `evidenceRefs` 에 `LAUNCH_TECHNOLOGY`·`LAUNCH_OPERATIONS`
가 들어가고 화면의 「이번 전략에 사용한 현재 자료」 8칸이 전부 ✓ 로 바뀌었다.

> **교훈**: API 200 은 「화면에 보인다」가 아니다. 이번 세 건 모두 **API 는 성공한 상태에서**
> 화면만 어긋나 있었다. 유료 실행을 끝낸 뒤에는 반드시 브라우저로 눈으로 확인한다.

## 22. 모듈 6 완주 — 아무도 안 본 세 번째 타임아웃 층 (2026-08-19)

### 22.1 근거 뭉치 벽은 여기서 이미 막혀 있었다

모듈 3·5 를 막은 그 뭉치가 모듈 6 에서는 **설계로 이미 처리돼 있었다.**

```java
private static final List<String> BULK_SOURCE_KEYS = List.of("evidence", "upstreamReferences");
// ⚠ 목록은 **손질 전 원본**으로 만든다. 그래서 위의 손질이 근거를 하나도 지우지 않는다.
ArrayNode catalog = evidenceCatalog.build(current.sources());
```

`compactSource()` 가 `sources` 에서 두 뭉치를 덜어내되, **인용 목록은 손질 전 원본으로** 만든다.
`BusinessProposalEvidenceCatalog` 에 `MAX_PER_SOURCE = 80` 캡도 있다.
결과 봉투 **715 kB** — 2 MB 상한의 3분의 1 수준이다.

> 모듈 3·5 에서 내가 손으로 넣은 정리가, 모듈 6 에는 처음부터 들어 있었다.
> **같은 문제를 세 번 만났고 세 번째에야 이미 있는 답을 봤다.**

### 22.2 진짜 벽은 타임아웃이었다 — 그리고 3층이었다

`MODEL_DEPENDENCY_UNAVAILABLE` · `retryable=true` · **정확히 60초**.

계획서의 타임아웃 행렬은 두 층만 봤다. 실제로는 **세 층**이다:

| 층 | 값 | 비고 |
|---|---|---|
| 백엔드 워커 BUDGET | 6분 | `FinalBusinessProposalWorker` |
| 백엔드 클라이언트 read-timeout | 9분 | longRunning |
| **AI 프로바이더 타임아웃** | **60초** | `.env:55 AI_PROVIDER_TIMEOUT_SECONDS=60` ← **실제로 끊은 층** |

`ai/app/providers/structured.py:194` 가 기본 60초를 쓰고, 최종 보고서 호출은
`timeout_seconds_override` 를 **안 넘겼다.** 오버라이드를 쓰는 곳은 `tech_ops_advisor`(180)와
`launch_readiness`(180·120) 둘뿐이었다 — **내가 성공시킨 두 모듈이 정확히 그 둘이다.**

실측: 최종 보고서 생성은 **1:02 ~ 1:22** 걸린다. 60초는 **매번 아슬아슬하게 모자란다.**
게다가 실패가 `retryable` 이라 재시도가 같은 유료 실행을 또 태운다 —
`AiServerProperties` 주석이 경고한 그 구조 그대로다.

`final_business_proposal/service.py` 에 300초, `review.py` 에 240초를 넣었다
(워커 BUDGET 6분·5분 안에서 깨끗이 끝나도록).

> **점검할 것**: 남은 태스크 중 `timeout_seconds_override` 없이 60초로 도는 것들이
> 무거워지면 같은 방식으로 죽는다. 타임아웃 행렬에 **AI 프로바이더 층을 반드시 넣는다.**

### 22.3 결과

10개 절 · 근거 누락 0 · 생략 0 · 포함 자료 11종.

```
1 사업 추진 배경 및 목적      6 기술 및 운영 계획      ← 배열 index 5 (테스트가 맞다)
2 사업 개요                 7 재무 계획
3 시장 및 고객 검증          8 법률·규제·리스크
4 사업 모델 및 사업성        9 실행 로드맵
5 마케팅 및 시장 진입 전략   10 최종 의사결정 요청
```

§6 이 `LAUNCH_TECHNOLOGY`·`LAUNCH_OPERATIONS` 를 근거 17건으로 인용하고
「기술 34점 REVISE / 운영 62점 CONDITIONAL / 출시 필수 기능 7개 미착수」를 그대로 옮겼다.

AI 검토(24건)도 정직하다 — 「**시장 인터뷰도 가상 정성 탐색이므로** 실제 구매의향·가격
수용성·문제 경험률을 판단할 근거가 부족하다」고 스스로 적었다. 모듈 4 봉투의 유의사항과 같은 말이다.

### 22.4 내가 낸 사고 — 클릭 하나가 보고서를 낡게 만들었다

보고서 생성 직후 `CURRENT` 였는데 몇 분 뒤 `STALE` 이 됐다. 추적하니
마케팅 콘텐츠에 **`USER_EDITED` 개정 2번**이 생겼고, 내용은 1번과 **같은 1,381 바이트**였다.

`MarketingContentPage` 에는 자동 저장이 없다(`save()` 는 명시적 `onClick` 뿐).
브라우저에서 탭을 전환하려고 여러 번 클릭하다 저장 버튼을 눌렀다.

> **내용이 하나도 안 바뀌어도 개정 번호가 오르면 상위 해시가 바뀌고 최종 보고서가 낡는다.**
> 재생성 1분 22초를 다시 썼다. 화면을 눌러 확인할 때는 **읽기만 하는 경로**로 다녀야 한다.

### 22.5 실측

| 실행 | 소요 | attempt | 비고 |
|---|---|---|---|
| `FINAL_BUSINESS_PROPOSAL_GENERATION` (60초 타임아웃) | 1:00 | 1 | 실패 · `MODEL_DEPENDENCY_UNAVAILABLE` |
| `FINAL_BUSINESS_PROPOSAL_GENERATION` (300초) | **1:02** | 1 | 성공 |
| `FINAL_BUSINESS_PROPOSAL_REVIEW` | 0:17 | 1 | 성공 |
| `FINAL_BUSINESS_PROPOSAL_GENERATION` (내 오조작 후 재생성) | **1:22** | 1 | 성공 |
| `FINAL_BUSINESS_PROPOSAL_REVIEW` (재실행) | 0:20 | 1 | 성공 |

**1→6 전 모듈 완주.** 최종 상태 `CURRENT` · 버전 2 · stale 없음.

## 23. 여정에 「완료」가 안 뜨던 이유 — 하드코딩 세 곳 (2026-08-19)

화면의 6단계 여정에서 **3단계 출시 준비와 6단계 최종 보고서만** 완료 배지가 없었다.
다 돌려 놓고도 안 떴다. 원인은 한 곳이 아니라 **세 곳이 각각 못을 박아** 둔 것이었다.

| # | 자리 | 박아 둔 값 |
|---|---|---|
| ① | `ProjectModuleStatusService:255` | `LAUNCH_READINESS` 를 **무조건 `READY`** 로 응답 |
| ② | `PipelineModuleType` | **`FINAL_REPORT` 가 아예 없음** — 상태를 물어볼 대상 자체가 없다 |
| ③ | `projectJourneyModel.js:87` | `journey.optional ? OPTIONAL : aggregate(...)` — 선택 단계는 **상태 고정** |

실측으로 확인한 응답:

```
TECH_OPS          COMPLETED
FINANCE           COMPLETED
LAUNCH_READINESS  READY      ← 기술·운영 둘 다 SUCCEEDED 인데 READY
(FINAL_REPORT)    없음
```

### 23.1 고친 방법

**①** 두 분석의 실제 `TaskRun` 상태로 판정한다. 하나만 끝난 상태를 `FAILED` 로 접지 않는다 —
나머지 하나는 아직 «할 수 있는» 일이다.

**②** `FINAL_REPORT` 를 모듈 타입에 추가하고 `FINAL_BUSINESS_PROPOSAL_GENERATION` 의
TaskRun 상태로 판정한다(다른 모듈과 같은 방식). 프론트에도 모듈 칸과 `FINAL_REPORT` 매핑을 넣었다.

**③** `statusModuleIds` 라는 «이미 있던» 갈래를 쓴다 —
`(journey.statusModuleIds ?? journey.moduleIds)` 는 진작 있었는데 **아무도 값을 넣지 않았다**
(§17.1 의 `unavailable()`, §21.2 의 배지 매핑과 같은 패턴 — 만들어 두고 안 연결한 자리).

```js
{ id: 'launch',      moduleIds: [], optional: true, statusModuleIds: ['techOps','finance','launchReadiness'] }
{ id: 'finalReport', moduleIds: [], optional: true, statusModuleIds: ['finalReport'] }
```

그리고 상태 고정을 **「아직 손대지 않았을 때만」** 으로 좁혔다. 「손대지 않았다」는
기본 상태(`NOT_READY`·`READY`·`NOT_CONNECTED`)만 있는 경우로 정의한다 —
기본값이 이미 `READY` 라 「비어 있으면」으로는 판정할 수 없다(내 첫 시도가 여기서 틀렸다).

### 23.2 진행률 분모는 «플래그» 로 센다

`getJourneyProgress` 는 `status !== OPTIONAL` 로 필수 단계를 셌다. 상태가 바뀌면
**선택 단계를 끝낸 순간 필수 단계 수가 늘어** 진행률이 뒤로 간다.
분모를 `!journey.optional` (플래그)로 바꿔 4로 고정했다.

> 기존 테스트 3건이 옛 동작을 고정하고 있었다. 다만 **테스트 이름이 말하는 의도**
> (「하위 Journey로 노출하지 않는다」·「진행률에 포함하지 않는다」)는 수정이 그대로 지킨다 —
> 이름과 무관한 곁다리 단언만 옛 값을 붙들고 있었다. 의도를 명시적으로 단언하도록 고치고,
> 「끝내면 완료로 보인다」·「아직 시작 안 했으면 선택 기능으로 남는다」 두 건을 새로 넣었다.

---

## 24. 프론트 전수 점검 — 38개 화면 (2026-08-19)

사용자가 스크린샷 두 장으로 **선별 기준 2가지**를 주었다.

1. **레이아웃이 물리적으로 깨진 것** — 글자가 한 글자씩 세로로 짓눌려 흐르는, 누가 봐도 깨진 렌더링
2. **정제 안 된 원시 정보가 그대로 노출된 것** — `UNVERIFIED` 같은 raw enum, 같은 자리표시자 문구 반복, 영어 원문

### 24.1 검출기 — 눈으로만 보면 놓친다

`javascript_tool` 로 주입해 페이지마다 돌렸다. **잎 텍스트 노드가 3줄 이상 높이인데
폭이 3em 미만**이면 압착으로 본다.

```js
const closed=e=>{for(let p=e;p;p=p.parentElement)if(p.tagName==='DETAILS'&&!p.open)return true;return false};
document.querySelectorAll('body *').forEach(e=>{
  if(e.children.length||closed(e))return;               // ← 닫힌 <details> 제외가 핵심
  const r=e.getBoundingClientRect(); if(!r.width||!r.height)return;
  const c=getComputedStyle(e); if(c.visibility==='hidden'||c.opacity==='0')return;
  const fs=parseFloat(c.fontSize), lh=parseFloat(c.lineHeight)||fs*1.2;
  if(Math.round(r.height/lh)>=3 && r.width<fs*3) hit(e);
});
```

> ⚠ **오탐 2건을 실제로 겪었다. 검출기를 믿기 전에 오탐부터 잡아라.**
> - 첫 판은 `/market-interview` 에서 **360/405 히트**를 보고했다. 전부 **닫힌 `<details>`**
>   안이었고 실폭이 80px 로 계산된 것이었다. 위 `closed()` 를 넣어 해결했다.
> - `/auth/signup` 헤드라인이 빈 칸으로 보여 결함으로 의심했다. 원인은 **탭이
>   백그라운드**(`document.hidden===true`)라 타이핑 애니메이션이 멈춰 있던 것.
>   `useBrandCopyTyping` 이 `document.hidden` 이면 타이머를 걸지 않는다 — 의도된 동작이다.

### 24.2 점검 범위 — AppRouter 전 경로

| 구역 | 화면 | 수 |
|---|---|---|
| 로그인 전 | `/` 랜딩(인트로 포함) · login · signup · password-reset | 4 |
| 워크스페이스 | `/app` · projects · projects/new · settings/profile · settings/security | 5 |
| 프로젝트 2번 | overview·idea·concepts·compare·legal-report·market·business-model·concept-refinement·market-interview·launch-readiness·reports/technology·technology·operations·tech-ops·finance·marketing·marketing/report·final-report·settings | 19 |
| 관리자 | admin·users·projects·projects/2·audit·audit/162·settings·operations·jobs | 9 |
| 오류 | 404 | 1 |

**로그인 전 화면을 보는 법:** 세션 토큰이 `sessionStorage`(탭 단위)에 있으므로
**새 탭을 열면** 기존 로그인을 건드리지 않고 로그아웃 상태를 볼 수 있다. 로그아웃할 필요가 없다.

> ⚠ 관리자 라우트는 `/admin` 이다. `/app/admin` 이 아니다 — 부모 레이아웃이 path 없는
>   라우트라 `/app/admin` 은 404 로 떨어진다. 한 번 헛다리를 짚었다.

### 24.3 기준① 레이아웃 깨짐 — 2건, 둘 다 §21 에서 수정

나머지 36개 화면은 검출기 **0 hit**. 신규 없음.

### 24.4 기준② 미정제 정보 — 신규 6종

| # | 자리 | 내용 |
|---|---|---|
| 1 | **`/admin/users`** | 화면 전체가 에러. `GET /api/v1/admin/users` → **500** |
| 2 | **관리자 프로젝트 상태** | 전부 `DRAFT` 고정. 개요의 「완료」가 영원히 0 |
| 3 | `/auth/password-reset` | 인증 셸도 없는 개발 자리표시자(`AuthPlaceholderPage`) |
| 4 | 404 화면 | 헤더·복귀 링크 없는 맨 카드 하나, 첫 페인트 약 7초 |
| 5 | 영어 원문 | 관리자 사이드바 7 · 페이지 제목 7 · `/admin/settings` 토글 2개가 제목·설명 전부 영어 · Account settings/Profile/Security/DANGER ZONE · New Project |
| 6 | raw enum | `/admin/jobs` **63셀(17종)** · `/admin/audit` **50셀** · projects 상세 · operations |

#### 24.4.1 `/admin/users` 500 — `function lower(bytea) does not exist`

```
org.postgresql.util.PSQLException: ERROR: function lower(bytea) does not exist
  at com.aivle.backend.admin.AdminUserService.list(AdminUserService.java:34)
```

`UserRepository.searchAdminUsers` 의 JPQL 이 `lower(concat('%', :keyword, '%'))` 를 쓴다.
`keyword` 가 null 인 **기본 로드**에서 Postgres 가 `concat` 결과 타입을 `bytea` 로 추론한다.
`:keyword is null` 이 앞에 있어도 **계획 단계에서 식 전체의 타입을 풀어야** 하므로
값과 무관하게 항상 터진다. 즉 **검색어를 넣든 안 넣든 100% 실패**한다.

같은 패턴은 코드 전체에서 이 한 곳뿐이다. 고치려면 `cast(:keyword as string)` 로
파라미터 타입을 못 박는다. **미수정.**

#### 24.4.2 프로젝트 상태가 영원히 `DRAFT`

`Project.java:48` 이 생성 시 `ProjectStatus.DRAFT` 를 박고 **어디서도 바꾸지 않는다.**
`ACTIVE`·`PAUSED`·`COMPLETED` 는 `AdminOverviewService` 의 집계 쿼리에만 등장한다.
결과로 관리자 개요의 「완료」가 항상 0 이고 `/admin/projects` 의 `Status` 필터가 무의미하다. **미수정.**

### 24.5 아직 못 본 것

**입력 전(빈 프로젝트) 상태.** 프로젝트가 2번 하나뿐이고 완전히 채워져 있어
모듈 화면의 빈 상태를 보려면 임시 프로젝트를 만들어야 한다. 데이터를 쓰는 일이라 진행하지 않았다.

---

## 25. 도커 자립 실행 + main 병합 (2026-08-19)

### 25.1 엔진이 안 떴다 — WSL 미설치

Docker Desktop 29.7.2 는 설치돼 있었으나 데몬이 안 떴다.

```
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
wsl --status → Linux용 Windows 하위 시스템이 설치되어 있지 않습니다
```

Windows 11 **Home** 이라 Hyper-V 백엔드가 없고, Docker Desktop 29 는 WSL2 만 지원한다.
가상화 자체는 살아 있었다(`HypervisorPresent=True`). 사용자가 관리자 권한으로
`wsl --install --no-distribution` + 재부팅으로 해결했다.

> 📌 이건 **에이전트가 못 하는 일**이다. Windows 기능 활성화는 UAC 승격이 필요하고,
>   계정이 Administrators 그룹에 있어도 승격 자체를 자동화할 수 없다.

### 25.2 도커에서만 터진 것 3가지

#### (1) `npm ci` 거부 — 락에 리눅스 전용 의존이 빠져 있었다

```
npm error `npm ci` can only install packages when your package.json and
package-lock.json are in sync.
Missing: @emnapi/core@1.11.3 from lock file
Missing: @emnapi/runtime@1.11.3 from lock file
```

`@emnapi/*` 는 `@rolldown/binding-wasm32-wasi` 의 optional 의존이라 **윈도우에서 만든
락에는 안 들어간다.** 반대로 리눅스에서 `--package-lock-only` 로 새로 만들면
**win32 항목이 떨어져 나가** 이번엔 윈도우 로컬이 깨진다(251 → 222개로 줄었다).

해결: **컨테이너 안 격리된 폴더에서 전체 `npm install`** 을 돌려 락만 꺼내 온다.
`--package-lock-only` 가 아니라 진짜 install 이어야 한다.

```sh
docker run --rm -v "<repo>/frontEnd:/src:ro" -v "<out>:/out" node:22-alpine sh -c '
  mkdir -p /w && cp /src/package.json /src/package-lock.json /w/ && cd /w
  npm install --no-audit --no-fund >/dev/null 2>&1
  cp /w/package-lock.json /out/package-lock.linux.json'
```

결과는 **상위집합**이었다 — 251 → 256개. win32 16 · darwin 17 그대로 두고
`@emnapi/*` 5개만 추가. 양쪽 플랫폼이 다 산다.

> ⚠ Git Bash 에서 `docker run ... /out/relock.sh` 는 경로가 윈도우 경로로 변환돼
>   `C:/Program Files/Git/out/relock.sh` 를 찾는다. `MSYS_NO_PATHCONV=1` 를 붙인다.

#### (2) 트윈 뱅크 — 바인드 마운트를 걷어내고 이미지에 구웠다

`ai/.dockerignore` 가 `app/twin/bank` 를 제외하고 compose 가 호스트를 바인드하고 있었다.
그대로 두면 폴더 없는 환경에서 **모듈 4가 「표본 0명」으로 조용히 성공**한다
(`targeting.py` 주석이 경고한 그 함정 — 화면 경고는 0건이다).

- `ai/.dockerignore` 에서 `app/twin/bank`·`research2/runs` 제외를 뺀다
- `ai/Dockerfile` 에 **빌드에서 끊는 가드**를 넣는다

```dockerfile
RUN test -s app/twin/bank/twin_bank_manifest.json \
    || (echo "ERROR: ai/app/twin/bank 이 비어 있습니다." >&2; exit 1)
```

- `compose.yaml` 의 ai-server 바인드 마운트를 주석으로 남기고 제거

> ⚠ **이 이미지는 배포용이 아니다.** 트윈 뱅크는 KISDI 파생 **재배포 금지** 자산이다.
>   이 PC 안에서 재현하는 용도로만 쓴다. 이미지를 남에게 넘기는 것은 재배포다.

#### (3) compose 의 시장조사 타임아웃 기본값이 22m 로 남아 있었다

`application.yaml` 은 이미 63m 인데 `compose.yaml:145` 만 옛 값이었다.
`.env` 가 63m 을 주고 있어 런타임에는 안 드러나지만, `.env` 없는 환경에서 되살아난다.

### 25.3 리포에 **없는** 것 — 남에게 넘길 때 필요한 목록

클론만 받아서는 못 돌린다. 빠지는 것은 셋이다.

| 빠지는 것 | 왜 | 없으면 |
|---|---|---|
| `.env` | gitignore | compose 가 아예 안 뜬다 (`POSTGRES_PASSWORD is required`) |
| `ai/app/twin/bank/` 12MB | KISDI 파생 재배포 금지 | 모듈 4를 **새로 못 돌린다** (이미 돌린 결과는 DB에 있어 화면 확인은 됨) |
| DB | 리포 밖 | **계정·프로젝트·모듈 결과가 전부 빈 상태** |

`.env` 를 그대로 주면 OpenAI·KOSIS·DART·TAVILY 키가 통째로 넘어간다.
상대에 따라 키를 비운 배포용 사본을 만들어야 한다.

> **도커 compose 의 postgres 는 빈 named volume(`postgres-data`)으로 뜬다.**
> 올리기만 하면 DB 가 비어 있다. 로그인은 `BOOTSTRAP_ADMIN_*` 로 되지만 워크스페이스가 텅 빈다.

### 25.4 데이터 이관 절차 — 실제로 밟은 순서

**① DB 덤프** (로컬 postgres 를 켜서)

```sh
pg_dump -h 127.0.0.1 -U aivle -d aivle -Fc -f aivle.dump     # 3,840,856 bytes
```

**② 도커 postgres 에 복원** — 백엔드가 이미 Flyway 를 돌려 놨으므로 DB 를 갈아엎는다

```sh
docker compose stop backend
docker compose cp aivle.dump postgres:/tmp/aivle.dump
docker compose exec -T postgres psql -U aivle -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='aivle' AND pid<>pg_backend_pid();"
docker compose exec -T postgres psql -U aivle -d postgres -c "DROP DATABASE aivle;"
docker compose exec -T postgres psql -U aivle -d postgres -c "CREATE DATABASE aivle OWNER aivle;"
docker compose exec -T postgres pg_restore -U aivle -d aivle --no-owner --no-privileges /tmp/aivle.dump
docker compose start backend
```

> 로컬은 **PG 18**, 도커 이미지는 **PG 17.11** 이다. `pg_restore` 17 이 PG18 덤프
> (Dump Version 1.16-0, TOC 601) 를 문제없이 읽었다. 버전을 맞출 필요는 없었다.

**③ 아티팩트 이관** — 로컬은 파일시스템, 도커는 MinIO 다

로컬 `OBJECT_STORAGE_PROVIDER=local` → `backend/data/objects` (15개, 4.02 MiB).
도커는 `s3`/MinIO. **DB 만 옮기면 이미지·DOCX 가 전부 404 난다.**
`minio/mc` 컨테이너를 compose 네트워크에 붙여 `mc mirror` 로 옮긴다.

### 25.5 검증 결과

| 항목 | 결과 |
|---|---|
| 컨테이너 | postgres·minio·ai-server·backend·frontend **5개 healthy** |
| 이미지 | backend 818MB · ai-server 479MB(트윈 뱅크 포함) · frontend 75.5MB |
| 이미지 내용 | `/app/app/twin/bank` 3파일 11.3MB + research2 run 3종 확인 |
| DB | users 1(`admintest` ADMIN) · projects 2 · task_runs 54 · flyway 40 |
| 재현 | 프로젝트 2번 **6단계 전부 「완료」** |
| 아티팩트 | 15개 이관, **마케팅 생성 이미지 렌더 확인** |
| API 경로 | `/api/v1` 상대경로 → nginx `/api/` → `backend:8080` |

**복원 충실성 검증** — 로컬과 도커의 `final_report_snapshots` 행이 ID·해시까지 동일했다
(시각은 KST/UTC 표기 차이뿐). 최종보고서의 「업데이트 필요」 배지는 **데이터가 그런 것**이지
도커 탓이 아니다. 상태는 저장 컬럼이 아니라 `source_binding_hash` 비교로 파생된다.

> nginx `/api/` 의 `proxy_read_timeout` 은 **90s** 다. 비동기 작업 구조라 지금은 안 걸리지만,
> 동기 호출이 90초를 넘기면 도커에서만 끊긴다. main 과 동일한 설정이라 회귀는 아니다.

### 25.6 병합 — 공통 조상이 없었다

```
$ git merge-base origin/main HEAD
(빈 출력)
$ git merge-tree --write-tree origin/main HEAD
fatal: refusing to merge unrelated histories
```

`main` 의 뿌리는 `a7ae7df8 Initial commit`, `full` 은 `2b4871e2 new ver` 다.
하이브리드 트리를 **손으로 파일을 옮겨** 만들었기 때문에 두 이력이 아예 무관하다.
일반 병합·PR 이 성립하지 않는다.

**택한 방식** — `main` 을 부모로 두고 검증한 트리를 그대로 얹는 커밋 하나:

```sh
TREE=$(git rev-parse HEAD^{tree})
NEW=$(git commit-tree "$TREE" -p origin/main -F msg.txt)
git branch -f integration/module-2-4-port "$NEW"
```

| 커밋 | 뜻 |
|---|---|
| `cf137c48` | `full` 브랜치의 작업 커밋 (443 파일) — 이력 보존용 |
| `191bda1b` | main 을 부모로 한 같은 트리 (766 파일 차이) |
| `6c760f3f` | PR #51 머지 커밋 |

병합 후 `origin/main` 의 트리가 검증한 트리와 **완전 동일**함을 확인했고,
모듈 2·4 불변식(`pipeline/market`·`ai/app/interview` 가 main 과 바이트 동일)도 유지된다.

> `full` 브랜치 푸시는 원격이 앞서 있어 거절됐다(non-fast-forward). 강제로 덮지 않았다.
> 자기 PR 은 GitHub 이 승인을 막는다(422) — 검증 내역은 코멘트로 남기고 병합했다.

### 25.7 CI 는 원래 빨갛다

`ai`(`scripts/audit_env_contract.py`)·`frontend`(`npm run test:baseline`) 두 잡이 실패한다.
**main 에서도 2026-08-14 부터 계속 실패 중**이라 이 병합으로 인한 회귀가 아니다.
빨간 CI 를 무릅쓰고 병합한 것은 사용자의 명시적 판단이다.

### 25.8 병합 직전에 잡은 백엔드 회귀 2건

726개 중 2개가 깨져 있었다. **둘 다 이 세션의 변경을 테스트가 못 따라간 것**이다.

- `ActiveSurfaceCleanupTests` — §23 에서 넣은 `FINAL_REPORT` 가 `containsExactly` 목록에 없었다
- `ConceptPortfolioBuildHandoffMaterializationTests:84` — 서비스가 `save` → `saveAndFlush`
  로 바뀌었는데 `verify(marketSeeds).save(...)` 가 그대로였다

고친 뒤 **726 통과 / 0 실패**. AI 1061/3 · 프론트 749/7 은 `origin/main` 에서 물려받은 기존 실패로,
해당 파일들이 main 과 바이트 동일함을 확인해 회귀가 아님을 증명했다.

### 25.9 열람 전용 배포 — `REQUIRE_TWIN_BANK=false`

§25.2(2) 의 빌드 가드가 **인수인계를 막는다**는 것이 실제로 드러났다. 트윈 뱅크는
넘겨줄 수 없는데, 가드가 켜져 있으면 상대는 `docker compose build` 조차 못 한다.
그렇다고 가드를 없애면 「표본 0명으로 조용히 성공」 함정이 되살아난다.

빌드 인자로 갈랐다. **기본은 `true`**(안전), 열람 전용 배포에서만 끈다.

```dockerfile
ARG REQUIRE_TWIN_BANK=true
RUN if [ "$REQUIRE_TWIN_BANK" = "true" ]; then \
      test -s app/twin/bank/twin_bank_manifest.json || (echo "ERROR: ..." >&2; exit 1); \
    else echo "WARNING: 트윈 뱅크 없이 빌드합니다 — 열람 전용."; fi
```

**세 경로를 실제로 빌드해 확인했다.**

| 조건 | 결과 |
|---|---|
| 가드 ON · 뱅크 있음 | 성공 |
| 가드 ON · 뱅크 없음 | **실패** — 안내 문구와 함께 끊긴다 |
| 가드 OFF · 뱅크 없음 | 성공, 컨테이너 기동 후 `/health/ready` **200** |

마지막 줄이 중요하다. 뱅크가 없어도 ai-server 가 healthy 로 올라오므로
`depends_on: service_healthy` 사슬이 끊기지 않는다 — **상대방 스택 전체가 정상 기동한다.**
이미 DB에 있는 모듈 4 결과는 그대로 보이고, **새로 실행하는 것만 하면 안 된다.**

> ⚠ 런타임에는 여전히 안 막는다. 「누르면 안 되는 것」을 문서로만 막고 있다 —
>   런타임 가드는 남은 일이다.

### 25.10 인수인계 꾸러미

깃허브 코드만으로는 «로그인은 되는데 화면이 텅 빈» 상태가 된다. 빠지는 것을 묶었다.

```
D:\aivle-handoff\  (7.8MB)
├─ .env          유료 API 키만 비운 사본. REQUIRE_TWIN_BANK=false 포함
├─ aivle.dump    DB 전체 (계정·프로젝트·모듈 1~6 결과·실행 이력)
├─ objects/      아티팩트 15개 (마케팅 이미지·DOCX·시장조사 원장)
└─ README.md     복원 절차와 «안 되는 것» 대조표
```

> `AI_API_KEY` 는 비우면 안 된다 — compose 의 `:?` 필수 검사에 걸려 스택이 아예 안 뜬다.
> 자리표시자 문자열을 넣어 두었다. AI 실행만 실패하고 열람은 전부 된다.
