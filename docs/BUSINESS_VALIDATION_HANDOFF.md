# 사업 검증 — 인수인계

작성: **2026-08-13** · 기준 커밋 `ad73047`(작업 트리는 미커밋 상태, §6 참조)
계획서: `C:/Users/User/.claude/plans/wild-purring-aho.md`

---

## 1. 무엇을 만드나

**여정의 「3. 시장 분석」과 「4. BM 분석」 두 칸을 없애고 「사업 검증」 한 칸으로 합친다.**
그 안에서 시장조사 → BM 캔버스 → 컨셉 다듬기가 이어지고 **컨셉 확정**으로 끝난다.
다듬어서 **바뀐 항목만** 법률 델타 검토를 돌고, 통과하면 그것이 최종 컨셉이다.

> **새 기능을 만드는 것이 아니다.** 시장조사도 BM 도 이미 돈다. 컨셉 개정 기계
> (`CONFIRM_HYPOTHESES` → `DELTA_LEGAL` → 법률보고서 재확정)도 이미 있다.
> 이 작업은 **셋을 하나로 잇는 일**이다. 사용자가 이 점을 두 번 강조했다.

완성 모습:

```
왼쪽 여정 (7칸)
  1 아이디어 · 2 사업안 · 3 사업 검증 · 4 기술·운영 · 5 재무 · 6 시장 인터뷰 · 7 마케팅

「3. 사업 검증」 한 화면 안에서
  ① 시장조사 결과   ② BM 캔버스(근거 id 가 기계로 붙음)   ③ 판정 + 걸린 이유
  ④ 컨셉 다듬기(내부 루프) → 바뀐 항목만 법률 델타       ⑤ 최종 컨셉 + 변경 표 + 확정
```

---

## 2. 어디까지 했나

### 끝난 것 — 0판 + 판정 게이트

| | 무엇 | 검증 |
|---|---|---|
| `ai/app/validation/gate.py` | 판정 게이트 G1·G4·G5. **LLM 0회** | 15 테스트 |
| 봉투 `bm.gateReasons` | AI `serialize.py` ↔ Java `MarketResearchContract` 동시 | 계약 테스트 3개 |
| 화면 「아직 상품이 아니다 — 이유 N」 | `BmCanvasPage.jsx` · `marketResult.js` | 실스택 확인 |
| `ai/app/validation/citation.py` | 근거 없이 「확인됨」이면 `UNVERIFIED` 로 강등 | 9 테스트 |
| 0-1 실패 원인 노출 | `_fail()` 이 `detail` 을 버리던 것 수정 + 로그에 `detail=%s` | 5 테스트 · 실경로 확인 |
| 0-2 프롬프트 되돌림 | `bm/prompt.py` 원상복구 (노트북 정본과 분기 제거) | — |
| **1-1** `ai/app/validation/mapping.py` | 근거 id·출처 라벨·상태를 **기계가 확정**(LLM 0회). `pipeline._bm` 이 LLM 결과를 덮는다 | 16 테스트 |

**1-1 에서 내린 결정 — G5 를 캔버스 전체로 읽는다.**
매핑이 라벨을 확정하면서 `demand_evidence` 는 `PAIN` 카드에서만 나오고 `PAIN` 은
**가치 제안 칸**으로 간다(정본 `research2/harness/vocab.json`). 그래서 수익원 칸의 라벨은
어떤 입력에서도 `price_analysis` 뿐이다 — G5 를 자기 칸으로만 재면 **반증 불가능**해지고
`_CEILING` 때문에 BM 판정 상한이 영구 `CONDITIONAL`(PASS 도달 불가)이 된다.
⇒ `gate.py` 의 G5 를 「캔버스 어디에도 수요 근거가 없다」로 고쳤다(사유의 `cell` 은 그대로
`REVENUE_STREAMS`). 대안(b)「`PAIN` 카드를 수익원 칸에도 붙인다」는 칸 표를 vocab 과
갈라놓아서 택하지 않았다. **갈래별 사유 `cause` 는 1-2 몫이다.**

⚠ **골든 픽스처 `bm.json` 은 이제 파이프라인이 낼 수 없는 모양이다 — 1-2 에서 다시 굽는다.**
저장된 `bm.gateReasons` 에 G5 가 남아 있고(재계산하면 G1 만 남는다), `CUSTOMER_SEGMENTS` 는
근거 2건 + 도장 없음인데 `PARTIAL`(파생 규칙은 `VERIFIED`), 관측 칸에 `concept_snapshot`
라벨이 남아 있다(파생은 카드에서만 만든다). 계약상 유효해 3층 테스트가 다 통과하므로
**갈라진 채 굳는다.** 1-2 에서 봉투를 고칠 때 관측 4칸을 파생 규칙대로 다시 굽고
`pytest` · `gradlew test --tests '*MarketResearch*'` · `vitest --dir src/features/market` 를
한 번에 돌린다.

**실스택 실측으로 판정이 바뀌는 것을 확인했다** — 프로젝트 3(HMR), BM 재실행:
`통과(PASS)` → `수정 필요(REVISION_REQUIRED)`, 이유 5건(G1×3 · G4 · G5).

### 1판도 끝났다 (2026-08-13)

| | 무엇 | 검증 |
|---|---|---|
| **1-1** `ai/app/validation/mapping.py` | 칸→근거 id·라벨·상태를 기계가 확정. **LLM 0회** | 13 테스트 |
| 1-1 부수 | **G5 가 구조적으로 상시 발동**하던 것을 잡았다 — 라벨을 기계가 확정하니 수익원 칸은 `demand_evidence` 를 못 갖게 되어 **PASS 도달 불가**였다. 「이 조사가 수요 근거를 아예 못 찾았는가」로 재정의 | 테스트로 못박음 |
| **1-2** `gate.evaluate(cells, scorecard)` | 사유에 **갈래 `cause`** — `UNCOLLECTED`(재수집이 답, 컨셉 수정 무효) / `UNCITED`(찾아 놓고 인용 누락) / `UNMAPPED`(성적표가 안 재는 칸) | 5 테스트 |
| 1-2 봉투 | **BM 모드도 성적표를 싣는다.** 예전엔 `mode=BM` 이면 `scorecard` 를 null 로 강제해 갈래를 못 갈랐다 | AI·Java 양쪽 |
| 1-2 화면 | 사유마다 갈래 배지 + 「컨셉을 고쳐도 안 고쳐져요」 | 3 테스트 |

**골든 픽스처를 다시 구웠다.** G5 재정의로 저장된 `gateReasons`(G1+G5)가 **어떤 실행으로도
재현 불가능**해졌다 — 실제로는 G1+G4 다. 성적표도 실었다. 프론트 기대값도 같이 고쳤다.

### 2-1 도 끝났다 (2026-08-13) — 프론트 단독

**BM 실행 계획 7칸을 BM 화면 앞 국면에서 컨셉단 「가설 확인」 섹션으로 옮겼다.**
저장소는 안 건드렸다 — `PATCH .../business-model/plan` 도 `bm_plan_preparations` 도
`BmPlanPreparationService` 도 그대로다. **누가 언제 채우느냐만** 바뀌었다.

| | 무엇 |
|---|---|
| 이동 | `bmPlan.js`·`bmPlan.test.js`·`BmPlanForm.jsx` → `features/concept-portfolio/` (git mv) |
| 신규 | `hooks/useBmPlanDraft.js` — 초안 읽기·저장. `createMarketApi` 를 그대로 쓴다 |
| 제출 순서 | 「7개 검증 가정 확인」이 **PATCH plan → confirmHypotheses**. `confirm(changes, savePlan)` 이 `savePlan` 을 먼저 await 한다 |
| 버튼 하나 | `BmPlanForm` 의 자체 제출 버튼을 **없앴다** — 둘이면 계획만 저장하고 가설을 안 굳힌 채 넘어갈 수 있다 |
| 제거 | `BmCanvasPage` 의 `PlanPhase`·`useBmPlan` (142줄). 「실행 계획 고치기」는 컨셉 화면으로 보낸다 |
| 테스트 | `market/BmPlanPhase.test.jsx`(173줄) → `concept-portfolio/pages/BmPlanGate.test.jsx` 9건. 순서 계약 2건은 `useConceptPortfolio.test.jsx` 에 (저장 실패 시 확정이 **안 가는지**까지) |

`MarketResearchService.startFull():90` 이 이미 BM 제약을 선읽기하므로 **그대로 두면 저절로 옳아진다.**

**고아 둘을 남겼다** — `market/BmPlanPreview.jsx` 와 `market.css` 의
`.bm-plan__split`·`.bm-plan__preview`. 유일한 소비자였던 `PlanPhase` 가 사라졌다.
2-3 이 재사용할 수 있어 지우지 않고 import 경로만 고쳤다.

### 다음 착수점 — **2판 2-2「여정 8 → 7칸」**

⚠ **아직 남은 갈라짐 하나.** 픽스처의 관측 칸이 `mapping.derive` 로는 나올 수 없는 모양이다
(`CUSTOMER_SEGMENTS` 가 근거 2건인데 `PARTIAL`, 관측 칸에 `concept_snapshot` 라벨).
계약상 유효해 3층 어디서도 안 걸린다. 카드가 있어야 다시 구울 수 있어 미뤘다.

---

## 3. ⚠ 뒤집힌 전제 3개 — 다시 헤매지 말 것

이 세 가지를 모르면 잘못된 설계를 다시 세운다. 실제로 이번 세션에서 계획을 두 번 뒤집었다.

### 3-1. 검증 가정 7개는 「법률 중립」이 **아니다**

`ai/app/concept_portfolio_v2/engine.py:1646-1648`
```python
legal_sensitive = item.hypothesisType in {
    "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS"}
delta_required = edited and value != item.proposedValue and legal_sensitive
```
7개 중 5개가 **고치면 법률 델타를 부르는 면**이다. 중립은 SOM 가설 2개뿐
(테스트가 못박음: `ai/tests/tasks/test_concept_portfolio_v2_selection.py:69-71`).

`candidate_governance.py:13` 의 `DIRECT_CANDIDATE_LOCKS` 가 같은 5개를 담고 있어
**「사용자가 잠글 수 있는 상업 차원 = 법률과 무관」으로 거꾸로 읽기 쉽다.**

그리고 `ConceptPortfolioSelectionService.java:127` 의 `staleDependents()` 는 **무엇을 고쳤든
조건 없이** 불려서 법률보고서와 **시장조사 시드**를 STALE 로 만든다(`:292-293`).

> **결론(사용자 판단):** 우회하지 않는다. *"확정된 컨셉을 시장검토를 거쳐 말이 되는 사업으로
> 바꾸면 실제로 법률검토를 다시 받아야 돼. 어차피 구체화만 시켜주는 거 아닌가."*
> 다듬기 → 델타 → 법률보고서 재확정 → 시드 재확정 → 재검증을 **정상 순서로 통과**한다.

### 3-2. BM 캔버스는 **LLM 이 새로 쓴다** — 문서에 그 말이 없다

`bm/flow.py:2` — *"모델 호출은 정확히 1회다."*
그런데 `docs/architecture/AS_BUILT_ARCHITECTURE.md` §5-1 은 BM 을 3,000자 서술하면서
**LLM 을 한 번도 언급하지 않는다**(파일 전체 grep 0건). 문서만 읽으면 "기계가 표대로 옮겨
캔버스를 만든다"로 읽힌다. **사용자가 이상하게 느낀 원인이 이것이다.**

### 3-3. 기계 매핑은 이미 있는데 **버려진다**

`research2/service/canvas.py` 가 9칸을 LLM 0회로 조립하지만,
`bm_adapter.build_from()` 이 그중 `못_찾은_것` 하나만 가져가고(`bm_adapter.py:326`)
나머지는 버린다. 칸별 카드 묶음 `by_ct` 는 계산해 놓고 **한 번도 안 쓰는 죽은 변수**다
(`bm_adapter.py:302-304`).

> 이력 정정: **"원래 기계였는데 LLM 으로 바뀐" 것이 아니다.** 둘은 같은 커밋
> (`fe3a5a9`, 2026-08-10)에 동시에 들어왔다. 기계층은 애초에 **판단문을 쓰지 않기로**
> 정해져 있어(`canvas.py:11`·`verdict.py:20`·`bm_layer.py:20`) `CONDITIONAL/MEDIUM` 같은
> 판정을 못 냈고, 그래서 노트북 이관본(LLM 1회)이 들어왔다.

---

## 4. 실측 수치 (그대로 인용해도 되는 값)

프로젝트 3 「HMR 1인가구 프리미엄 냉동 간편식」, 2026-08-13:

```
성적표             6/6 FILLED        시장조사는 다 찾았다
1단계 근거          17건
BM 봉투가 실은 것    17건             BM 손에 들어가 있다
캔버스가 인용한 것    0건              ← 끊긴 자리
관측 3칸           VERIFIED(확인됨)   labels=['concept_snapshot']  ev=[]
최종 판정           PASS(통과)
```

`concept_snapshot` 은 **사용자가 쓴 컨셉 서술문**이다. 모델이 자기 입력을 자기가 확인했다고
도장 찍고 통과시켰다 — *"다 패스를 해버려서 상품으로 증명되지 않는 느낌"* 의 정체.

**실행 시간** (2-4 타임아웃 등급의 근거):

| 실행 | 시간 |
|---|---|
| 시장조사 FULL (run 15) | **약 23분** (07:36:53 → 07:59:42) |
| BM (run 17·18·19) | **18 / 18 / 39초** |

현재 `AI_SERVER_LONG_READ_TIMEOUT` 은 **420초**라 한참 모자란다.
`InternalAiExecutionClient.java:168` 주석의 「90~266초」도 실측과 안 맞는다.

---

## 5. 갈래를 나눠야 한다 — 「다 안 찾아지면 어떻게 루프하나」

게이트가 낸 사유는 셋 중 하나이고, **컨셉 다듬기로 고쳐지는 것은 하나뿐**이다.

| | 상태 | 처방 | 다듬기로 되나 |
|---|---|---|---|
| **A. 원장에 없다** | 성적표 MISSING | 재수집. 그래도 없으면 **「미확보」로 확정하고 멈춘다** | ❌ |
| **B. 있는데 인용을 안 했다** | 6/6인데 인용 0건 | **1판 1-1 이 없앤다** | ❌ |
| **C. 인용까지 했는데 안 맞는다** | 가격이 밴드 밖 등 | 컨셉 다듬기 (3판) | ✅ |

A급을 컨셉 수정으로 통과시키면 **우리가 만든 방식의 「다 패스」**가 된다.
갈래 판정에는 성적표가 필요한데, 지금 계약이 `mode=BM` 이면 `scorecard` 를 `null` 로
강제한다 — 그래서 **1판 1-2 에서 성적표를 BM 봉투로 넘긴다.**

---

## 6. 작업 트리 상태 — ⚠ 두 벌이 섞여 있다

**커밋하지 않았다.** 그리고 이 저장소는 `git add -A` 가 위험하다(로컬 원자료가 섞인다).

### (A) 0판 + 1판이 바꾼 것 — **커밋한다면 이것만** (2026-08-13 최종)

```
수정
ai/app/api/executions.py                 실패 로그에 detail=%s  (⚠ 이 파일은 (B)도 건드림)
ai/app/research/pipeline.py              매핑·강등·게이트 배선 + BM 모드 성적표 산출
ai/app/research/runner.py                _fail 이 detail 을 싣도록
ai/app/research/serialize.py             bm() 에 decision·gateReasons · NOTES_BM 에 cause 안내
ai/tests/fixtures/market_research/bm.json   골든 (3층 공용!) — 성적표·gateReasons 다시 구웠다
ai/tests/test_pipeline_envelope.py
backend/.../taskrun/contract/MarketResearchContract.java     GATE_CODES · GATE_CAUSES · BM 성적표
backend/.../taskrun/MarketResearchContractTests.java         +6
frontEnd/src/features/market/BmCanvasPage.jsx                GateReasons + 갈래 배지, 2-1 이 PlanPhase 제거
frontEnd/src/features/market/marketResult.js                 정규화 · GATE_TITLE · GATE_CAUSE_VIEW
frontEnd/src/features/market/marketResult.test.js
frontEnd/vite.config.js                  server.fs.allow (와이어프레임용, dev 전용)

신규
ai/app/validation/{__init__,gate,citation,mapping}.py
ai/tests/test_validation_{gate,citation,mapping}.py
ai/tests/test_research_failure_detail.py
docs/BUSINESS_VALIDATION_HANDOFF.md · docs/mockups/business-validation.html
frontEnd/wireframe.html · frontEnd/src/wireframe/**        ← 제품 라우트 아님
.claude/agents/* · .claude/workflows/business-validation.js

2-1 이 더한 것 (프론트 단독)
이동(git mv)  features/market/{bmPlan.js,bmPlan.test.js,BmPlanForm.jsx}
                → features/concept-portfolio/
삭제          features/market/BmPlanPhase.test.jsx      → BmPlanGate.test.jsx 로 다시 씀
신규          features/concept-portfolio/hooks/useBmPlanDraft.js
              features/concept-portfolio/pages/BmPlanGate.test.jsx
수정          features/concept-portfolio/hooks/useConceptPortfolio{.js,.test.jsx}
              features/concept-portfolio/pages/BusinessProposalWorkspace{.jsx,.test.jsx}
              features/concept-portfolio/styles/business-proposal-polish.css   .bm-plan 규칙
              features/market/{BmPlanPreview.jsx,market.css}                   import 경로·규칙 이사
```

### (B) 이전부터 있던 미커밋 작업 — **내 것이 아니다**

MARKET_INTERVIEW(여정 7번) 도입분. `ProjectModuleStatusService` · `TaskType.java` ·
`InternalAiExecutionClient` · `ProjectJobQueryService` · `TaskRunService` ·
`projectModuleModel.js` · `AppRouter.jsx` · `projectRoutes.js` · `FinancePage.jsx` ·
`journey/MarketInterview*.java` · `ai/app/interview/**` 등.

**섞어서 커밋하지 말 것.** 커밋한다면 (A)만 골라 담는다.

---

## 7. 지뢰

1. **컨테이너는 코드를 굽는다** (`build:` 만 있고 볼륨 마운트 없음).
   소스를 고쳤으면 `docker compose up -d --build ai-server backend frontend`.
   **안 하면 옛 코드로 잰다** — 이번에 유료 실행을 한 번 헛돌렸다
2. **골든 픽스처 `ai/tests/fixtures/market_research/*.json` 은 AI·Java·프론트 3층 공용**이다.
   프론트 테스트가 상대경로로 **직접** 읽는다(사본 아님). 고치면 셋 다 돌린다
3. **화면에 원인 문장을 띄우지 마라.** `MarketResearchService.safeErrorReason:280-287` 이
   계약 어휘만 통과시키고 주석이 의도를 밝힌다 — *"provider details and input text stay
   server-side"*. 원인은 **서버 로그**로 보낸다
4. **프론트 판정은 `test:run` 이 아니라 `test:baseline`**
5. **`app/research/bm/` 은 정본이 담당자 노트북**(저장소에 없음)이다. 우리 층에서 덮는다
6. **`app/research/research2/` 는 동결** (판 ㉝ 이식 그대로). 자체 CLAUDE.md 의 절대 규칙 7개
7. **Flyway 번호 충돌이 상습 사고다.** 새 파일 전에 `ls db/migration/` 로 실제로 센다
8. `python -m pytest` 는 `research2/` 를 안 돈다. Python 한글 출력은 `PYTHONIOENCODING=utf-8`

---

## 8. 검증 기준선 (2026-08-13 — 매번 다시 셀 것)

| | |
|---|---|
| `ai` pytest | **776 passed / 4 failed / 1 skipped** (2026-08-13 재측정. 3-1 드리프트 계약이 10개 늘렸다) — 실패는 `tests/concept_portfolio_v2/` seed `domain` 계약. **기존** |
| `backend` `--tests '*MarketResearch*'` | **BUILD SUCCESSFUL** (1-2 갈래 테스트 3개 포함) |
| `backend` `test` **전체** | **513 tests / 9 failed = 기존** (2026-08-13 재측정. finance 3클래스 · `IdeaBriefControllerTests` · `ConceptFactoryReplacementIntegrationTests`). ⚠ 옛 기준선의 `ProjectModuleStatusServiceTests` 1건은 2-2 가 고쳐서 **빠졌다** — 목록이 줄었다 |
| `redocly lint docs/api/openapi.yaml` | **38 errors / 80 warnings = 기존** (`git status` 상 HEAD 와 바이트 동일) |
| `frontEnd` `vitest --dir src/features/market` | **84 passed** |
| `frontEnd` `vitest run src/features/{market,concept-portfolio}` | **145 passed / 17 파일** (2-1 이후) |
| `frontEnd` `test:baseline` | **이미 빨감** — 예상 밖 **8건** + stale allowlist 1건. **기존** (`a165f0c` 워크트리에서 목록까지 동일. 2판·3판 뒤에도 같은 목록) |
| `frontEnd` lint | `TechOpsPage.jsx` 1건. **기존** (같은 워크트리에서 재현) |
| `frontEnd` `build` | ✓ |
| 계약 픽스처 | `RESULT=PASS` |
| Flyway | V23 까지 |

「기존 실패」라고 말하려면 변경 전 코드에서 **같은 명령**을 돌려 가려내라.

**stash 말고 워크트리를 쓴다.** stash 는 작업 트리를 실제로 되돌리므로 실패 한 번에
미커밋 작업이 사라진다. 워크트리는 **내 트리를 전혀 안 건드린다**:

```powershell
git worktree add --detach C:\wt\chk HEAD          # ⚠ 짧은 경로. 긴 경로면 "Filename too long"
cmd /c mklink /J C:\wt\chk\frontEnd\node_modules "<main>\frontEnd\node_modules"
cd C:\wt\chk\frontEnd ; npm.cmd run test:baseline  # ← 이것이 진짜 기준선
# 끝나면
rm -rf C:\wt\chk\frontEnd\node_modules ; git worktree remove --force C:\wt\chk ; git worktree prune
```

2026-08-13 에 2-1 을 이렇게 판정했다. `App.test.jsx` 12건이 내 탓처럼 보였는데
`a165f0c` 워크트리에서 목록이 **완전히 같았다**.

> ⚠ **테스트를 돌리는 중에 파일을 옮기지 마라.** 같은 날 `test:baseline` 이 도는 동안
> `git mv` 를 걸쳐 실행해 **거짓 기준선(6건)** 을 얻었고, 그 뒤 8건을 「내가 2건 깼다」로
> 오독했다. 기준선은 트리를 멈춰 놓고 잰다.

> ⚠ **stash 를 굳이 쓴다면 `pop` 을 잊지 마라.** 2026-08-13 에 검증 에이전트가 stash 하고
> 안 되돌려 프론트 파일 10개(0판 작업 + **남의 미커밋 MARKET_INTERVIEW 작업**)가 조용히
> HEAD 로 돌아갔다. 에러도 경고도 없다 — 화면이 깨져서 알았다.
> **stash 를 쓴 뒤에는 반드시 `git stash list` 가 비었는지 센다.**

---

## 9. 멀티에이전트 구성

`.claude/agents/README.md` 참조. 에이전트 6개 + 워크플로 1개.

- **조사·검증은 병렬, 구현은 순차.** 이 저장소는 두 곳 동시 수정 짝이 여럿이라
  서로 모르는 에이전트가 한쪽씩 고치면 조용히 깨진다
- **계약 짝은 한 에이전트가 양쪽을 다 고친다** (분배 단계의 규칙)
- 구현 뒤 `bv-contract-auditor` 를 반드시 통과시킨다
- **어떤 에이전트도 유료 실행을 스스로 안 돌린다.** 보고하고 멈춘다

```
Workflow: { name: "business-validation", args: "1-1 칸→근거 매핑을 기계가 확정" }
```

⚠ 에이전트 목록은 **세션 시작 때** 읽힌다. 새 세션에서야 `bv-*` 가 보인다.

---

## 10. 남은 숙제

- **`AS_BUILT_ARCHITECTURE.md` §5-1 에 BM 이 LLM 1회를 쓴다는 사실을 적는다** (§3-2)
- 이번에 찾은 문서-코드 어긋남 3건을 `ppt/99_MISSING_MATERIALS.md` E절에 추가:
  ① AS_BUILT §5-1 의 LLM 누락 ② `CONCEPT_TO_RESEARCH_HANDOFF` §1-1 의 `collect` 서술이
  현재 `pipeline.py:8-14` 와 반대 ③ `INTEGRATION_LOCAL_MERGE_HANDOFF.md:217` 의 9칸 합이 10
- **유료 실행 20번이 왜 실패했는지 아직 모른다.** 0-1 로 다음부터는 원인이 로그에 남는다
- 아는 원장 라벨이 견본 4개(`beauty-noshow`·`hmr-solo`·…)뿐이다 —
  기억의 「견본 폴백」 결함과 같은 자리로 보인다. 1판에서 마주치면 확인할 것

---

## 11. 완성 목표 화면 — `docs/mockups/business-validation.html`

**목업은 «뼈대»다. 시각·배치의 정본이 아니다**(사용자 지정, 2026-08-13).
가져오는 것은 **무엇이 어떤 순서로 나오나**뿐이고, 색·부품·배치는 **우리 사이트 것**을 쓴다.
파일 안의 색은 이미 `frontEnd/src/shared/styles/tokens.css` 값으로 갈아 끼웠다
(원본은 파랑 `#2B5CE6` 이었다 — **파랑은 우리 색이 아니다**).

### 11-1. 우리 사이트의 시각 규칙 (정본: `tokens.css` · `shared/ui/ui.css`)

| | 값 |
|---|---|
| 액센트 | **민트** `--color-action-primary: #22b89a`. 파랑 금지 |
| 민트 위 글씨 | primary 버튼은 민트 배경 + **먹색 글씨**(`.ui-button--primary { color: neutral-800 }`). 흰 글씨 아님 |
| 뉴트럴 | 초록기 도는 회색(`#f7f9f8` · `#26312d` · `#cbd5d1`). 중성 회색 아님 |
| 서체 | Pretendard → Noto Sans KR → Apple SD Gothic Neo |
| 상태색 | success `#2e7d4f` · warning `#b7791f` · danger `#c23b3b` (`--color-status-*`) |
| 모서리 | control `.5rem` · card `.75rem` · panel `1rem` · pill `999px` |
| 다크모드 | `[data-theme="dark"]` 토큰은 있으나 **설정하는 곳이 0곳**이다. 새로 만들지 말 것 |

⚠ 목업에 있던 **법률 보라색은 우리 팔레트에 없다.** 없앴다 — 법률 항목은 상태 배지
(반영됨/확인 필요/문제 없음)로만 구분한다.

### 11-2. 배치는 목업과 다르다 — 셸이 이미 있다

목업은 **상단 sticky 바에 여정 알약**을 그렸다. 우리 셸은 그렇지 않다:

```
.pipeline-shell__header    프로젝트명 · 모듈명 · 액션
.pipeline-shell__body      15rem 사이드바 | 본문 | 17rem 작업센터   (3단)
  └ .pipeline-shell__sidebar  ← 여정 8칸이 여기 있다. 상단바 아니다
.pipeline-shell__main
  └ .pipeline-page-heading    p(단계 번호) · h2(제목) · span(설명)
```

**본문 폭을 목업의 920px 로 잡지 않는다** — 3단 그리드 안의 `minmax(0,1fr)` 이다.
새 화면은 `<section className="market-page">` + `.pipeline-page-heading` 으로 시작하고,
버튼 줄은 `.market-page__actions` 를 쓴다 (`MarketResearchPage.jsx:45-58` 그대로).

### 11-3. 목업 부품 → 우리 부품

| 목업 | 우리 것 |
|---|---|
| `.card` | `<Card>` (`shared/ui`) |
| `.badge b-ok/b-warn/b-bad/b-gray` | `<Badge tone="success/warning/danger/neutral">` |
| `.gate` (빨간 상자) | `<Alert tone="danger">` — 이미 `GateReasons` 가 이렇게 쓴다 |
| `.btn-primary` / `.btn-ghost` | `<Button>` / `<Button variant="ghost">` |
| `.kpi-row` · `.kpi` | `.mr-kpis` · `.mr-kpi` (`market.css`, 이미 있다) |
| `.canvas` 9칸 | `<BmCanvas>` — **다시 만들지 말 것** |
| `.detail` 칸별 근거 | `<BmCellDetails>` |
| `.section-item` 1~7 | `MarketResearchPage` 의 `Section` |
| `.runbar` 진행바 | 지금은 `<Alert tone="info">N초 경과</Alert>` — 진행바는 **새 부품**이다 |
| 상단 여정 알약 | 셸 사이드바가 이미 한다. **만들지 않는다** |

### 11-4. 뼈대에서 가져오는 것 — 순서

**화면 1 「사업 검증」** — 조사 전/중/후 세 상태를 한 화면이 갈아 낀다:
- *전*: 경쟁사 입력(선택) + 「시장조사 시작하기」 + 「20분 넘게 걸려요」
- *중*: 경과 시간 + 진행바 + 「이 화면을 닫아도 조사는 계속돼요」
- *후*: 제목 옆 판정 배지 → KPI 4 → 「이 숫자의 기준」 → 섹션 1~7 →
  **비즈니스 모델** 구역(게이트 → 판정 → 9칸 → 강·약·위험 → 칸별 근거) → 「다듬어진 컨셉 보기」

**화면 2 「다듬어진 컨셉」** — 본문(바뀐 곳 초록 밑줄+번호) → 「무엇이, 왜 바뀌었나요」
(옛값→새값 + 이유 + **근거 보기 링크가 화면 1 의 해당 섹션으로 점프**) → 법률 검토 →
「이 컨셉으로 확정하기」.

### 11-5. 어휘가 다르다 — 코드 상수와 대조

| 자리 | 코드 (`marketResult.js`) | 목업 |
|---|---|---|
| 성적표 `FILLED` | 채워짐 | **확인됨** |
| 성적표 `PARTIAL` | 부분 | **일부만 확인** |
| 성적표 `MISSING` | 미확보 | **비어 있음** |
| 과목 `NOT_FOUND` | 못 찾은 것 | **찾지 못한 것** |
| 칸 `VERIFIED` | 확인됨 | **근거 있음** |
| 칸 `UNVERIFIED` | 미확인 | **근거 필요** |
| 칸 `PLAN` | 계획(근거 없음) | **작성됨** |
| KPI | TAM / SAM | **전체 시장 (TAM) / 노릴 수 있는 시장 (SAM)** |
| 게이트 머리 | 아직 상품이 아니다 — 이유 N | **아직 판매할 수 없어요 — 해결할 문제 N가지** |

⚠ `VERIFIED` 의 「확인됨」이 목업에선 **성적표 쪽으로 옮겨 간다.** 두 표를 같이 안 고치면
한 화면에 「확인됨」이 두 뜻으로 뜬다.

**말투도 다르다.** 목업은 존댓말(「조사를 마쳤어요」), 현재 `features/market/` 은 서술 반말
(「아직 조사한 적이 없다」). 두 화면만 바꾸면 여정의 나머지 칸과 갈린다 — **범위를 먼저 정한다.**

### 11-6. 목업이 요구하는데 지금 데이터에 없는 것

1. **게이트가 규칙 코드(G1·G4·G5) 대신 «다음 행동»을 적는다** —
   *"타깃을 좁히거나 추가 조사가 필요해요"*. `GATE_TITLE` 은 제목만 갖고 있다
2. **「계획」 칸도 내용을 보인다** (핵심 파트너 = 「물류 대행사 · OEM 공장」).
   데이터는 `cell.content` 에 이미 있는데 화면이 안 그린다
3. **근거 배지에 정체가 붙는다** — `ev-03 수요 설문`. 봉투 `evidence[]` 에 그 한 마디가
   있는지 확인해야 한다
4. 머리말이 조사 시점을 말한다(`asOf` 있음) · 반영된 경쟁 씨앗을 되비춘다

---

## 12. 다음 세션 — 첫 다섯 가지

**이 순서로 한다.** ①②는 5분이고, 건너뛰면 옛 코드로 재거나 사라진 작업 위에서 판단하게 된다.

### ① 작업 트리가 온전한지 센다 — **가장 먼저**

```powershell
git stash list                                 # 2026-07-29 것 하나만 있어야 한다
git status --short | Measure-Object -Line      # 64줄 근처 (2-1 뒤)
git worktree list                              # main · aidev-fix · C:/wt/m40 셋만
```

워크플로·에이전트를 돌린 **뒤에도** 이 세 줄을 다시 센다 (§8 의 경고).

### ② 컨테이너를 다시 굽는다

```powershell
docker compose up -d --build     # build: 만 있고 볼륨 마운트가 없다 — 안 하면 옛 코드로 잰다
```

### ③ 읽는다 — §3(뒤집힌 전제) · §6(작업 트리) · §11(완성 목표 화면)

### ④ 화면 작업이면 와이어프레임을 먼저 띄운다

```powershell
cd frontEnd ; npm.cmd run dev    # → http://localhost:5173/wireframe.html
```

2판·3판이 **끝났을 때 어떤 화면이어야 하는지**가 여기 있다. 백엔드도 로그인도 필요 없다.
대응표(뼈대 ↔ 우리 부품)는 §11-3. **새로 만들 부품은 진행바 하나뿐**이다.

### ⑤ 착수 — **유료 실스택 스모크**

구현은 13개가 다 끝났다. 남은 것은 **돈이 드는 확인**이다.

**왜 필요한가.** 다듬기 루프와 확정 버튼은 <b>라운드 데이터가 없으면 화면에 아예 안
그려진다</b>(`RefinementSummary` 가 `NOT_STARTED` 면 `null` 을 낸다). 그래서 무료 구간에서는
「안 깨진다」까지만 확인됐고 「돈다」는 확인되지 않았다.

```
사업 검증 실행 (약 23분·유료)
  → 판정·게이트 사유가 뜨는지
  → 다듬기 라운드 1 이 걸리는지 (REFINE_FROM_MARKET · LLM 1회)
  → 드리프트 계약이 거른 것이 다음 라운드 입력으로 되먹는지
  → DELTA_LEGAL 결과가 라운드에 적히는지 (안 적히면 루프가 조용히 멈춘다)
  → 최종 화면에 변경 표와 「못 푼 것」이 뜨는지
  → 확정 버튼 → 재무 칸이 열리는지
```

⚠ **누르기 전에 컨테이너를 다시 굽는다.** 안 하면 옛 코드로 재고 유료 실행이 헛돈다.
⚠ **`scripts/business-validation-smoke.ps1` 은 아직 없다.** 계획서가 「짜라」고 적은 것이지
있는 것이 아니다. 짜기 전에 **DTO/record 를 먼저 읽는다** — 특히 새 엔드포인트
`POST .../business-validation`(body `{asOf}`) · `GET .../concept-refinement?selectionId=` ·
`POST .../concept-refinement/finalize?selectionId=`(body `{idempotencyKey}`).

**2026-08-13 무료 구간 확인 완료** — V24·V25 실DB 적용, 여정 7칸, `/business-validation`
한 화면(진행바·KPI·성적표 7과목), 옛 `/business-model` 은 개요로 리다이렉트, 콘솔 에러 0.

⚠ **화면을 보고서야 잡힌 결함이 4종 있었다**(테스트 3층은 전부 초록이었다):
「8단계」 하드코딩 7곳 · 화면 제목 번호 어긋남 3곳 · 랜딩 여정 목록 8단계 ·
랜딩이 시장조사를 「외부 모듈」이라 설명. 전부 고쳤다.
**칸을 건드렸으면 반드시 크롬으로 개요·랜딩까지 본다.**

⚠ **워크플로에 한 판을 통째로 던지지 않는다.** 1-1 실측: 구현 17분, 감사→수선 1시간 20분.
사람이 직접 하면 40분이었다. 2026-08-13 에 2-2 도 워크플로로 시작했다가 끊고 직접 했다.

⚠ **검증은 판마다 한 번이다.** 소단계마다 전체 테스트를 돌리면 구현 9분에 검증 30분이 된다
(2-2 실측). 판이 끝난 뒤 백엔드 전체 → `test:baseline` → lint → build 를 한 번 돌린다.

⚠ **`build/test-results` 가 깨지면 `java.io.EOFException` 이 난다.** 테스트가 4초 만에
죽으면서 아무 것도 안 돌면 그 디렉터리를 지운다 — 코드 문제가 아니다.

---|---|
| `ProjectModuleStatusService.java` | 107 · 135-148 (두 블록 → 하나) · **115-118 재무 게이트를 검증 run 으로** |
| `ProjectJobQueryService.java` | 134-155 — **switch 가 exhaustive 라 빠뜨리면 컴파일 에러**(이건 그나마 잡힌다) |
| `projectModuleModel.js` | 23-34 · 38-44 |
| `projectRoutes.js` | 12-26 |
| `AppRouter.jsx` | 76-88 |

같이 고칠 테스트: `ProjectModuleStatusServiceTests.java:60,65,201` ·
`ActiveSurfaceCleanupTests.java:41-45` · `projectModuleModel.test.js:18`(`toHaveLength`) ·
`AppRouter.cutover.test.js:14`.

⚠ **이 다섯 파일 중 넷은 (B) 남의 미커밋 MARKET_INTERVIEW 작업이 이미 건드린 것이다**
(§6-B). 줄 번호가 계획서와 어긋나 있을 수 있다 — **읽고 나서 고친다.**

⚠ **워크플로에 한 판을 통째로 던지지 않는다.** 1-1 실측: 구현 17분, 그 뒤 감사→수선 루프가
**1시간 20분**. 원래 1-2 몫이던 픽스처 재굽기까지 범위 안으로 끌려 들어왔다.
반면 1-2 를 사람이 직접 하니 **40분**, 2-1 도 직접 해서 비슷했다.
⇒ **워크플로는 조사·감사까지만 시키고, 구현·수선은 사람이 판단해서 잇는다.**
(스킬 `business-validation` 은 6단계 워크플로를 부르라고 지시한다 — 계약 짝이 여럿인 판에서만 따른다.
2-2 는 Java·JS 두 층이 **enum 이름으로 묶인** 짝이라 감사는 값어치가 있다.)

---

## 13. 진행 — **13개 전부 구현 완료** (2026-08-13)

```
0판  0-1 ✅ 실패 원인 노출         0-2 ✅ 프롬프트 되돌림
1판  1-1 ✅ 칸→근거 기계 확정      1-2 ✅ 성적표·갈래(cause)
2판  2-1 ✅ BM 입력 → 컨셉단       2-2 ✅ 여정 8→7칸
     2-3 ✅ 화면 통합              2-4 ✅ TaskType BUSINESS_VALIDATION
3판  3-1 ✅ 드리프트 계약          3-2 ✅ 라운드 상태 V25
     3-3 ✅ REFINE_FROM_MARKET     3-4 ✅ 루프(Spring)
     3-5 ✅ 최종 확정 + 확정 버튼
```

⚠ **계획서에 4판은 없다.** 13개가 전부다.
⚠ **전부 미커밋이다.** 트리에 2판·3판이 통째로 얹혀 있다 — `git add -A` 금지.

### 13-1. 2026-08-13 에 들어간 것 — 파일 지도

| 판 | 핵심 파일 |
|---|---|
| 2-2 | `ProjectModuleStatusService`(validationStatus·재무 게이트) · `projectModuleModel.js` · `projectRoutes.js` · `AppRouter.jsx` |
| 2-3 | **`BusinessValidationPage.jsx`**(신규) · `MarketResearchPage.jsx`→`MarketResultBody.jsx` · `BmCanvasPage.jsx`→`BmResultBody.jsx` (git mv) |
| 2-4 | `TaskType.BUSINESS_VALIDATION` · `ai/app/validation/runner.py`(신규) · `BusinessValidationWorker`(신규) · `V24__business_validation_kind.sql` · 타임아웃 등급 `validation-read-timeout:2100s` |
| 3-1 | **`ai/app/validation/drift.py`(정본)** · `ConceptDriftContract.java`(사본) · `ai/tests/test_drift_contract_alignment.py` |
| 3-2 | `V25__concept_refinement_rounds.sql` · `ConceptRefinementRound{,Repository}` |
| 3-3 | `REFINE_FROM_MARKET` 액션(`selection_models.py`) · `ai/app/tasks/concept_refinement.py` |
| 3-4 | `ConceptRefinementService` · `ConceptRefinementWorker` · **`ConceptRefinementApplyService`** · materialization 에 라운드 기록·법률 결과 기록 |
| 3-5 | `ConceptRefinementController` · `RefinementSummary.jsx` |

**설계 결정 셋 (다시 헤매지 말 것)**
1. **봉투를 안 늘렸다.** `MarketResearchContract.ENVELOPE` 는 `exact()` 라 칸을 늘리면 결과
   전체가 거부된다. 늘린 것은 `mode` 값 하나(`VALIDATION`)뿐이고, 두 걸음의 산출이 원래
   비어 있던 칸에 들어간다
2. **단일 TaskRun 을 택했다.** 계획서가 요구한 전제(시드 재발급에도 `conceptId` 가 같은가)를
   확인했다 — `conceptIdOf()` = `portfolioConceptId` 라 같다. 그래서 BM 이 죽어도 다시 누르면
   원장을 재사용해 수집을 다시 사지 않는다
3. **적용할 문이 없는 칸이 둘 있다** — `featureSet`·`targetUsers`. 가설도 BM 계획도 아니고
   컨셉 원본은 덮지 않으므로(캐노니컬 해시·계보) 「못 적용한 것」으로 돌려준다

---
**커밋이 하나도 안 됐다.** 0판·1판 작업과 남의 시장 인터뷰 작업이 전부 워킹트리에만 있다.
2판 들어가기 전에 **(A)만 골라 담아** 커밋하는 것을 권한다 — §6 의 목록을 쓴다.

### 1판이 못 닫은 것 하나

픽스처의 관측 칸이 `mapping.derive` 로는 나올 수 없는 모양이다 — `CUSTOMER_SEGMENTS` 가
근거 2건인데 `PARTIAL`(파생 규칙상 `VERIFIED`), 관측 칸에 `concept_snapshot` 라벨이 남아 있다.
**계약상 유효해 3층 어디서도 안 걸린다.** 제대로 다시 구우려면 카드가 필요하다.
