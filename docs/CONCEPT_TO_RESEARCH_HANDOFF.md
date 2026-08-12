# 사업안 → 시장분석·BM 배선 인수인계

작성: **2026-08-11** · 브랜치 `market-research-v2` (= `origin/main` `f500258`, PR #39 머지됨)
**1~4단계 코드 완료(2026-08-11).** 완료 기록은 **§10**(컨셉 다리) · **§11**(수집 배선) · **§12**(배선·경쟁 씨앗).

새 세션이 이어받기 위한 문서다. 사실만 적는다. 추정에는 「추정」이라고 쓴다.
계획서 원본: `~/.claude/plans/https-github-com-junwoooooooo-aivle-big-wobbly-cook.md`

---

## 0. 지금 한 줄

「2. 사업안」(concept portfolio v2)을 확정하면 Market Seed 가 고정되고 「시장 분석으로
이동」이 열린다. **그런데 넘어가서 실제로 도는 것은 그 사업안이 아니라 견본 컨셉 셋 중
하나다.** 견본은 컨셉 모듈이 없던 시절의 대역이었다. 이제 실제 사업안으로 태우는 작업.

제품 기준은 **대기업 신사업**, 조사 계열은 **A(고객=사업체) 한 줄기**로 좁혔다.

---

## 1. 조사로 확인한 사실 — 여기부터 읽으면 다시 안 파도 된다

> **2026-08-11 코드로 재확인됨.** §1·§5 의 파일·줄 번호와 키 이름을 전부 다시 읽어 대조했고
> **어긋난 것이 없었다.** 다음 세션은 이 절을 다시 파지 말 것 — 아래 §10 이 그때 확정한
> 소비자 키 목록이고, 그것이 §1 보다 구체적이다.

### 1-1. 관은 이미 깔려 있고 물만 안 흐른다

- `MarketResearchPage.jsx:46` 이 `startMarketResearch(conceptKey, today(), **null**)` —
  컨셉 JSON 자리에 **항상 null** 을 보낸다
- 백엔드는 그걸 `"null"` 문자열로 직렬화해 나른다 (`MarketResearchInputFactory.java:58`)
- **AI 는 그 텍스트를 아예 안 읽는다.** 실제 컨셉은 `conceptId` 이름표가 100% 정한다
  (`pipeline.py:223-235`)
- 즉 `taskInput.concept` 은 **지금 죽은 필드**다. 새로 만들 게 아니라 살리면 된다

### 1-2. 견본 컨셉 표는 셋뿐이다

`ai/app/research/pipeline.py:57-61`
```python
CONCEPTS = {
    "beauty-noshow":    ("data/concept_beauty-noshow.json",    "beauty-13"),
    "household-ledger": ("data/concept_household-ledger.json", "ledger-05"),
    "pet-treat":        ("data/concept_pet-treat.json",        "pet-treat-15"),
}
```
표에 없고 `sourceRun` 도 없으면 `pipeline.py:226` 에서 실패,
`runs/<sourceRun>/` 디렉터리가 없으면 `:229` 에서 실패한다.

### 1-3. 가장 큰 빠진 조각은 원장이다

`pipeline.py:9` 헤더 그대로: **「`collect`(A1~A3 수집)는 아직 이 오케스트레이터가 돌리지
않는다. 지금 배선은 `--from a4`(저장된 수집 재채점) 위에 서 있다.」**

- FULL·BM·RESCORE **세 모드 어느 것도 수집을 안 돌린다**
- 재채점은 `runs/<id>/` 의 **세 파일이 전부 있어야** 한다 —
  `result.json`(필수) · `run.jsonl` · `a3_bodies.json` (`run.py:57-113`)
- **수집기 코드는 있다.** 없는 것은 오케스트레이터 배선이다. 지금은 CLI 4개를 사람이 순서대로:
  1. `tools/preflight.py` — 키·크레딧 확인
  2. `harness/slot_harness.py --concept … --tag …` — 슬롯·식 설계 (LLM ≤3)
  3. `tools/slot_dryrun.py --tag … --from-harness …` — **무료**, `stat_code` 가 서는지
  4. `run.py --id <원장> --concept … --slots … --formulas …` — **유료 수집** (LLM ≈80회·3.5분+)
- 절차의 정본: `시장조사/문서/설계/표준검사세트_v1.1.md`

### 1-4. Market Seed 는 재료로 충분하다 (지금은 배지 색깔에만 쓰인다)

`snapshotJson` 의 키 (`ai/app/concept_portfolio_v2/adapters.py:279-284`):

| 키 | 내용 |
|---|---|
| `selectedConcept` | `identity{7} · solution{3} · operation{13} · valueSemantics[31] · canonicalHash` |
| `finalHypotheses` | 7종 각각 `{value, source, decisionStatus, proposalVersion, legalImpact, legalReviewStatus}` |
| `legalResult` | `{legalStatus, safeSummary, requiredControls, …, deltaLegalReviews}` |
| `originalSeed` | `ideaOverview` + 필수3 및 LOCKED 필드만 |
| `aiInterpretation` | 아이디어브리프 AI 해석 |

저장 게이트가 강하다 — `compatibility=PASS` · 해시 재계산 · 7가정 전부 accepted 를 통과한
뒤에만 저장된다 (`ConceptPortfolioSelectionMaterializationService.java:93-107`).

**그런데 실행 경로가 한 글자도 안 읽는다.** `ProjectModuleStatusService.researchOrGate`
(`:192-195`)에서 「아직 안 눌렀을 때의 색깔」만 정하고, run 이 하나라도 생기면 판정에서
완전히 빠진다.

### 1-5. 7가정 ↔ 입구계약서가 거의 1:1로 맞는다

입구계약서(`시장조사/문서/계약/2_입구계약서_컨셉생성담당자용_v0.1.md`)는 이미
「컨셉 JSON 1개 → 캔버스 1개」로 설계돼 있다.

| concept.json | Market Seed |
|---|---|
| 필수 5 (`concept_id·name·problem·target·solution`) | `selectedConcept.identity` / `.solution` |
| `region` | `finalHypotheses.targetRegion` |
| `price_hypothesis_krw` | `finalHypotheses.price` (문자열 → 파싱 필요) |
| `constraint{budget_krw,months,team}` | **`BmPlanPreparation` 비용 3칸 — 이미 있다** |
| `_hypotheses_v2` 6·7·8·9 | `revenueModel` / `channels` / `differentiators` / `preMarketSomShare` |
| `_다듬기5.3_핵심_가치` | `identity.coreValue` |
| `_경쟁_씨앗` | **없음** — 새로 받아야 한다 |
| `_계열.계열` | **없음** — 새로 정해야 한다 |

### 1-6. 계열 A 를 고른 근거 — 성적표  ⛔ **폐기됨 (2026-08-12 · §18 로 대체)**

> **⛔ 아래 표를 근거로 계열을 되돌리지 말 것.** 이 측정은 **계열 C 가 자기 계량을 표현조차
> 못 하던 때** 것이다. `harness/vocab.json` `metric.catalog.판매가._계보` 가 그대로 적고 있다 —
> *「계열 C 가 낱말이 없어 막힌 **세 번째** 자리다 — 「거래액」(백로그 45) · 「추정점유율」
> (백로그 57) · 「판매가」(판 ㉚). 셋 다 통제 어휘가 strict enum 이라 **표현 자체가
> 불가능**했던 것이지 자료 부재가 아니었다.」*
> 즉 C 의 `3·0·3` 은 **자료가 없어서가 아니라 물어볼 낱말이 없어서** 나온 점수다.
> 셋 다 그 뒤에 신설됐고, **판 ㉜ 에서 계열 C 가 6/6 을 냈다**(§17.1).
> **제품 고정값은 2026-08-12 부로 A → C 다. 근거는 §18.**

`ai/app/research/research2/expected.md:5030-5034` (6과목, 채워짐·부분·미확보)

| 계열 | 원장 | 성적 | 비는 과목 |
|---|---|---|---|
| **A** 사업체 | `beauty-13b` | **5·1·0** | — |
| B 개인 | `ledger-05` | 4·0·2 | 가격 · 수요 |
| C 제품 | `pet-treat-18` | 3·0·3 | 경쟁사 · 가격 · 수요 |
| D 신시장 | — | 3·0·3 | |
| E 글로벌 | — | **보류** | 백로그 46 |

슬롯 두께도 같은 방향(A 19 / B 10 / C 12). 폴백 사다리 2단이 실증된 것도 A뿐
(`expected.md:5224` — A만 경계 보유 카드 2건).

⚠ **계열 계산 구조 자체는 A~E 다 있다.** `rules/series_unit.v1.json` 이 계열별 고객 단위를
못박고 있다(A=사업체, B=개인, C=거래, D=항상 선언, E=개인+거래). 없는 것은 **「이 컨셉이
어느 계열인가」를 정하는 사람**이다 — 지금은 `concept.json._계열.계열` 에 손으로 적는다.

---

## 2. 사용자가 확정한 것 — 다시 묻지 말 것

| 항목 | 결정 |
|---|---|
| 계열 | **A 한 줄기 · 상수로 박는다.** ~~AI 제안 + 사용자 확인~~ — 2026-08-11 사용자 결정으로 **판별 관문 자체를 뺐다**. 어차피 A 만 지원하므로 관문이 하는 일이 없다(§10) |
| 견본 주제 | 대기업 신사업 × 계열 A = **소상공인 매장 운영 SaaS** (외식업 대상 주문·재고·직원 관리 통합 구독) |
| 원장 저장 | **새 원장은 별도 볼륨** `runs-generated/`. 기존 `runs/` 는 `:ro` 유지 |
| 수집 | **파이프라인에 넣는다** |
| 경쟁 씨앗 | 사업안 화면에서 받는다 (단 순서는 맨 뒤 — §3 참조) |
| 견본 셋 | 회귀 기준선으로 **유지** |

---

## 3. 순서 — 「컨셉 다리만 놓고 멈추기」는 안 된다

독립 판정 결과다. 새 사업안에는 원장이 없고 `pipeline.py:228-229` 가 없으면 실패시킨다.
컨셉 다리만 놓고 멈추면 결과는 **둘 중 하나뿐**:

- 실패한다 → 사용자가 얻는 값 **0**
- 견본 원장을 붙여 성공한다 → **관측은 미용실, 잣대는 내 사업안.** 읽는 사람은 구분 못 한다.
  코드가 이미 이름 붙여 둔 사고와 같은 종류다 (`pipeline.py:47-53` — 미용실 원장에 카페
  컨셉 `CPT-CAFE-INV` 이 붙어 조용히 다른 잣대로 판정한 원장이 4개 있었다)

**따라서 1·2·3 은 한 덩어리다.** 나눌 곳이 있다면 2 안쪽이다 — 드라이런까지(무료) / 수집부터(유료).
**4(경쟁 씨앗)는 맨 뒤다** — 씨앗을 읽는 유일한 코드가 하네스이고, 하네스는 2 에서만 돈다.

---

## 4. 착수 순서 (상세는 계획서 §1~4)

1. ~~**컨셉 다리 + 계열 관문**~~ — **2026-08-11 완료.** 기록은 §10.
   계열 관문은 **사용자 결정으로 통째로 뺐다**(계열 = `"A"` 상수). 마이그레이션을 안 썼다
2. ~~**수집 배선**~~ — **2026-08-11 완료.** 기록은 §11.
   **유료 실측은 아직 안 했다** — 코드는 서고 무료 경로까지만 확인됐다
3. ~~**배선**~~ — **2026-08-11 완료.** 기록은 §12
4. ~~**경쟁 씨앗**~~ — **2026-08-11 완료.** `V21` (⚠ 계획서의 V18 이 아니다 — 세션 중 병합으로 밀렸다)

---

## 5. 착수 전에 알아야 할 지뢰 — 전부 실측이다

| # | 지뢰 | 근거 |
|---|---|---|
| 1 | ✅**1에서 막음** — `ResearchConceptFactoryTests.topLevelKeysNeverExceedTheEngineDataclass` 가 9개를 못박는다. `revenueModel` 같은 이름을 최상위에 넣으면 `TypeError`(런타임에만 터진다) | `run.py:37`, `schema.py:53-64`, `bm_adapter.py:198-208` |
| 2 | ✅**1에서 처리함** — 실행별 임시 디렉터리에 떨구고 끝나면 지운다(`data/` 밖이라 되짚기와 안 부딪친다) | `verdict.py:733`, `cards.py:54`, `bm_adapter.py:190` |
| 3 | ✅**2에서 막음** — `research2/runpath.py` 가 「읽기는 둘 다, 쓰기는 `runs-generated/` 만」을 한 곳에서 답한다 | `compose.yaml` · `runpath.py` |
| 4 | ✅**1에서 막음** — `pipeline._assert_same_concept`. 기존 검사는 `CONCEPTS` 표 3줄만 본다 | `test_market_research.py:188` |
| 5 | ✅**1에서 재사용함** — `public static Long priceKrw(String)`. 재구현 금지 | `TwinSurveyStimulusDraftService.java:177-188` |
| 6 | ✅**1에서 `[]` 로 둠.** 엔진은 `[{축, 우리_값}]` 을 요구하는데 `differentiators.value` 는 한 문장 | `verdict.py:652-671` |
| 7 | ✅**1에서 빈 배열로 고정 + 검사.** 채우면 수집 프롬프트로 들어가 자기확인 회로가 된다 | 입구계약서 §1 |
| 8 | ✅**확인함(2026-08-11)** — OPENAI·KOSIS·DART 있음, TAVILY 없음. **TAVILY 는 수집에 안 쓰인다**(web 어댑터는 OpenAI `web_search` 툴이다 — `adapters/web.py:121`). Tavily 는 `harness/tavily_intake.py` 적재기 전용. ⚠ 그런데 `tools/preflight.py` 는 tavily 를 필수로 세어 **「진입 금지」를 낸다** — 이 판에는 해당 없는 사유다 | `adapters/web.py` · `tools/preflight.py:144` |
| 9 | ✅**2에서 올림** — `llmBudget` 3→90 · long-read 420s→1320s · 워커 BUDGET 6분→20분. ⚠ 대가: 재채점이 매달리면 예전보다 오래 문다 | `MarketResearchWorker` · `application.yaml:51` |
| 10 | **하네스 게이트가 새 컨셉에서 막힐 수 있다.** 필라테스 실측 **3회 시도 전부 통과 못 함**. 원인은 씨앗이 아니라 DART corp_name 상수 요구(백로그 39). 4에서 씨앗을 받게 했으나 **막힐 수 있는 것은 그대로다** — 그때는 `HARNESS_GATE_FAILED` 로 남고 결과가 안 나온다 | 입구계약서 §4 H5 |
| 11 | ✅**2에서 갈랐다 — 우려보다 작았다.** `main()` 이 `Namespace a` 를 만든 뒤 전부 `a.<필드>` 읽기라, `CollectOptions` dataclass 로 바꾸니 본체는 그대로였다. 진짜 걸림돌은 `ap.error` 2곳뿐 | `run.py` |

### 병합이 남긴 구멍 — 3 에서 같이 막는다

`TwinSurveyStimulusDraftService.java:129-132` 가 **레거시 선택 경로**
(`ConceptSelectionRepository.findByProjectIdAndCurrentSelectionTrue…`)로 시드를 찾는다.
**사업안(포트폴리오) 기반 시드를 못 본다** — 조용히 견본 이름표로 떨어진다.
`ProjectModuleStatusService.java:70-75` 가 쓰는
`findByPortfolioSelectionIdAndStaleAtIsNull…` 로 같이 봐야 한다.

---

## 6. 검증 — **2단으로 나눈다**

전체 스위트를 매번 돌리지 않는다. 2026-08-11 실측 — 백엔드 `test` 전체는 **16분 39초**
(457건), 같은 날 `--tests '*ResearchConceptFactoryTests*'` 로 좁히면 **33초**다.
느린 이유는 테스트 파일 119개 중 `@SpringBootTest` 18개가 설정마다 컨텍스트를 새로 띄우는 것.

**작업 중 — 바꾼 것만.**
```powershell
cd backend  ; .\gradlew.bat test --tests '*ResearchConceptFactoryTests*'
cd ai       ; python -X utf8 -m pytest tests/test_pipeline_envelope.py -q
cd frontEnd ; npm.cmd run test:run -- <바꾼 파일>
```

**커밋 직전 — 한 번만.**
```powershell
cd backend  ; .\gradlew.bat clean test ; .\gradlew.bat postgresTest
cd frontEnd ; npm.cmd run lint; npm.cmd run test:baseline; npm.cmd run build
cd ai       ; python -X utf8 -m pytest -q
```

- 프론트 판정은 `test:run` 이 아니라 **`test:baseline`**
- **물려받은 실패 7건**(ai 4 · 백엔드 2 · 프론트 1)은 병합 전부터 있던 것 —
  **늘어나지만 않으면 된다**. 목록은 §7
- ⚠ **「실패가 늘었나」를 세려고 로컬 전체를 돌리지 말 것.** `.github/workflows/ci.yml` 이
  PR·main push 마다 프론트·AI·백엔드 전체를 돌리고 개수를 낸다. 그 일은 CI 몫이다
  (물려받은 실패 때문에 CI 는 지금도 빨간불이다 — 새 실패가 아니다)
- ⚠ Windows 콘솔은 CP949 라 파이썬 출력에 `—` 가 있으면 `UnicodeEncodeError` 가 난다.
  **`python -X utf8`** 로 부를 것. 파이프라인은 멀쩡한데 출력만 죽어 오진하기 쉽다

### 별건으로 빼둔 것 — 백엔드 스위트 10분

`@SpringBootTest` 18개의 설정을 통일하면 Spring 이 컨텍스트를 **한 번만** 띄우고 재사용한다.
지금은 설정이 제각각이라 매번 새로 켠다. **이 계획과 분리된 리팩터링이다** — 시장조사 배선을
다 끝낸 뒤에 별도로 잡는다.
- 골든 픽스처는 키 집합만 대조하므로 봉투가 안 바뀌면 통과한다. 대신
  **`degradations` 가 3건→0건으로 줄어드는 것을 확인하는 검사를 새로 넣는다** — 지금 없다

**실스택 왕복 — 이 작업은 여기서만 진짜로 확인된다:**
1. 견본 하나로 **수집부터** 완주(유료 1회) → `runs-generated/` 에 3파일, `input.slots` 비지 않음
2. 새 사업안으로 아이디어 → 사업안 → 7가정 + 계열 확인 → Seed 고정
3. 시장분석 실행 → 원장 생성 → 7과목 성적표
4. **사람 눈 검사 1건** — 컨셉 `target` 과 TAM 슬롯 `subject` 가 같은 것을 가리키는가
   (표준검사세트 검사 8). 기계가 못 잡는다
5. BM 분석 → 계획 4칸 → 캔버스 9칸
6. 트윈 조사까지 이어지는지 (§5 구멍이 막혔는지)

---

## 7. 물려받은 실패 — 이 작업이 만든 게 아니다

전부 팀원 브랜치 워크트리에서 재현해 확인했다(2026-08-11).

**1단계 직후 실측(2026-08-11):** 백엔드 `457 tests / 2 failed` · AI `575 passed / 4 failed`.
**둘 다 아래 목록과 같은 개수다 — 늘지 않았다.**

| 어디 | 무엇 |
|---|---|
| ai (4) | `test_generic_portfolio_engine.py::test_production_entrypoint_uses_same_engine_and_contract`, `test_production_path_multi_domain_readiness.py` 3건 — `concept_portfolio_v2` production 진입점 입력 스키마 불일치 |
| 백엔드 (2) | `ConceptFactoryReplacementIntegrationTests` 2건 · `IdeaBriefControllerTests` 1건 — 실제로는 3건이나 병합 전 HEAD 에서도 동일 |
| 프론트 (1) | `useProjectJobs` needs-input notice |
| 프론트 게이트 | `test-debt-baseline.json` allowlist 가 22개인데 실제 실패는 24개 — **병합 전부터 빨간불**. `AuthProjectFlow`·`LandingPage` 2건이 목록에 없다 |

---

## 8. 지금 저장소 상태

- 브랜치 `market-research-v2` = `origin/main` = **`f500258`** (PR #39 머지됨)
- 작업 트리 깨끗. 미푸시 커밋 0
- ⚠ **마이그레이션 번호를 이 문서에서 읽지 말 것.** 2026-08-11 세션 중에 `ec1d163`
  (origin/main 병합 · PR #40 재무분석)이 들어와 **V1~V16 이 V1~V20 으로 늘었다**
  (V12 가 다른 것으로 바뀌고 V17~V19 재무 · V20 BM 계획). 착수할 때마다
  `ls backend/src/main/resources/db/migration/` 로 **다시 셀 것**
- 여정은 **8칸** (개요 / 1.아이디어 / 2.사업안 / 3.시장 분석 / 4.BM 분석 / 5.기술·운영 /
  6.재무 / 7.패널 트윈 조사 / 8.마케팅 콘텐츠 / 설정)
- 미추적 로컬 원자료: `model/` · `문서/` · `법률/` · `시장조사/` · `front+back_renew/` ·
  `ai/legal/` · `valid-mvp-v11.html` · `frontEnd/src/features/feasibility/`
  → **커밋에 섞지 말 것.** `git add -A` 금지

---

## 9. 열어야 할 문서

| 무엇 | 어디 |
|---|---|
| 입구 계약서 (컨셉 JSON 필드 정본) | `시장조사/문서/계약/2_입구계약서_컨셉생성담당자용_v0.1.md` |
| 새 컨셉 여는 절차 | `시장조사/문서/설계/표준검사세트_v1.1.md` |
| 계열별 고객 단위 규칙 | `ai/app/research/research2/rules/series_unit.v1.json` |
| 성적표·측정 이력 | `ai/app/research/research2/expected.md` |
| 시장조사 지도 | `시장조사/CLAUDE.md` |
| 계획서 원본 | `~/.claude/plans/https-github-com-junwoooooooo-aivle-big-wobbly-cook.md` |

---

## 10. 1단계 완료 기록 — 2026-08-11

**한 줄:** 사업안을 `concept.json` 으로 바꾸는 변환기를 만들고, AI 가 **실린 컨셉을 실제로
쓰게** 했다. 아직 **아무도 변환기를 부르지 않는다** — 배선은 3단계다.

### 만든 것

| 파일 | 무엇 |
|---|---|
| `backend/.../journey/ResearchConceptFactory.java` | Market Seed → `concept.json`. 순수 변환이라 DB·AI 를 안 탄다 |
| `backend/.../journey/ResearchConceptFactoryTests.java` | 12건 |
| `ai/app/research/pipeline.py` | `_inline_concept` · `_assert_same_concept` 신규. 실린 컨셉이 **이기고**, 이름표는 원장만 정한다 |
| `ai/tests/test_pipeline_envelope.py` | 5건 추가 |

### 계열 관문은 만들지 않았다 — 사용자 결정

계열은 `ResearchConceptFactory.SERIES = "A"` **상수**다. 판별도, 확인 화면도, `V17` 도 없다.
제품 범위가 「대기업 신사업 × 계열 A」 한 줄기이기 때문이다.

> ⚠ **알고 두는 위험.** 사업안의 고객이 실제로는 개인(계열 B)이어도 값은 A 다.
> `harness/gate.py:581` 와 `slot_harness.py:113,354` 가 이 값으로 **고객 단위를 사업체로
> 못박으므로**, 개인 고객 사업안이 들어오면 잣대가 어긋난 채 조사가 돈다.
> 계열 B~E 를 지원하는 날 이 상수가 첫 번째로 없어져야 한다. 근거는 코드 주석
> (`ResearchConceptFactory.SERIES_NOTE`)에도 남겼다.

### 소비자 키 — **실측으로 고정했다. 다시 파지 말 것**

변환기가 채우는 언더스코어 칸을 **누가 어느 키로 읽는가**. 이름이 어긋나면 예외도 로그도
없이 **조용히 안 실린다** — 이 표가 그것을 막는다.

| 컨셉의 키 | 읽는 곳 |
|---|---|
| `_계열.계열` · `_계열.왜` | `harness/gate.py:346,581` · `slot_harness.py:113,124,354` |
| `_다듬기5.3_핵심_가치` | `canvas.py:200` · `bm_adapter.py:258` |
| `_다듬기5.4_업종_분류` | `slot_harness.py:369` (프롬프트 `[업종 분류]` 블록) |
| `_hypotheses_v2.6_수익_가격.수익_방식` | `canvas.py:232` |
| `_hypotheses_v2.6_수익_가격.제안값_krw_월` | `verdict.py:390,531,681` |
| `_hypotheses_v2.7_채널.주_채널_가정` | `verdict.py:616` |
| `_hypotheses_v2.8_차별점.비교축` | `verdict.py:656` |
| `_hypotheses_v2.9_SOM_초기점유.가정_침투율` | `verdict.py:681` |
| `_bm_plan.{revenue_model,channel,differentiation,key_activities,key_resources,key_partners,customer_relationship}` | `bm_adapter.PLAN_FIELDS`(:65) · `plan_material_of`(:229) |
| `_경쟁_씨앗.seeds` | `gate.py:450` · `slot_harness.py:294,332,442` — **4단계에서 채운다** |

`_bm_plan` 의 파생 대응은 AI 쪽 `_CONCEPT_TO_PLAN`(`bm_adapter.py:198-208`)을 **그대로
옮긴 것**이다. 그쪽은 `revenueModel`·`channels`·`operatingModel` 같은 **camelCase 최상위
키**에서 파생하는데, 그 이름을 최상위에 두면 수집이 `TypeError` 로 죽는다. 그래서 같은
대응을 백엔드가 미리 적용해 `_bm_plan` 에 담는다.

`customer_relationship` 은 **의도적으로 비운다** — 사업안에 대응 필드가 없다.
사용자가 BM 앞 화면에서 채우면 `_user_bm_plan` 이 이긴다(`bm_adapter.py:213`).

### 지어내지 않기로 한 자리 — 전부 검사로 고정

| 자리 | 결정 |
|---|---|
| 가격 | `priceKrw` 재사용. 「월 3만원 수준」은 `null`(「3만원」을 3 으로 읽지 않는다) |
| 비교축 | `[]` — 한 문장을 축으로 쪼개지 않는다. 도장이 `축_부재` 로 나가는 것이 정직하다 |
| KSIC 코드 | 만들지 않는다. `명칭` 만 나른다. 코드는 드라이런이 무료로 확인할 자리다 |
| `hypotheses` | 빈 배열 고정 (`research_view()` 가 수집 프롬프트로 그대로 넘긴다) |
| `constraint` | `BmPlanPreparation` 의 정수 세 칸만. 없으면 `{}` |

### 새 그물 — 실린 컨셉 ↔ 원장 대조

`pipeline._assert_same_concept`. 기존 짝 검사(`test_market_research.py:188`)는 `CONCEPTS` 표
세 줄만 보므로 **실린 컨셉이 오는 새 길은 그 검사를 통째로 비껴간다.** 원장을 못 읽거나
원장에 `concept_id` 가 없어도 **통과시키지 않는다** — 대조할 수 없다는 것은 짝이 맞다는
뜻이 아니다.

### 실측으로 확인한 것

- 불일치 컨셉(`CPT-CAFE-INV` + `beauty-13` 원장) → `FIELD_CONSTRAINT_VIOLATION` 으로 막힘
- **실린 컨셉 파일을 엔진이 실제로 읽는다** — `_hypotheses_v2` 를 dict 아닌 값으로 바꾸니
  그 자리에서 실패했다
- 실린 컨셉이 없으면 견본 이름표 경로가 그대로 돈다(회귀 기준선 유지)
- ⚠ **RESCORE 봉투에는 컨셉 차이가 드러나지 않는다.** `_계열` 을 A→B 로 바꿔도 결과가
  `generatedAt` 빼고 동일했다. 재채점은 원장이 정하기 때문이다 — 그래서 「컨셉이 실렸나」를
  **결과로 확인할 수 없고**, 검사를 경로·그물 수준에서 잡았다. 2단계(수집)가 붙어야
  컨셉 차이가 결과에 나타난다

### 다음 — 2단계(수집)

계획서 §2 그대로다. 착수할 때 **`run.py:151-493` 의 493줄짜리 `main()` 을 먼저 재고**
나서 진행할 것(작업량을 아직 모른다 — §5 지뢰 11).

---

## 11. 2단계 완료 기록 — 2026-08-11 (수집 배선)

**한 줄:** `harness → dryrun → collect` 가 오케스트레이터 안에서 돈다. 원장이 없는 사업안은
이제 **실패하지 않고 원장을 만든다**. ⚠ **유료 실측은 아직 안 했다** — §11-6.

### 계획서보다 컸던 것 하나 — 원장 자리

계획서는 「`runs-generated/` 볼륨을 붙이고 파이프라인은 둘 다 읽는다」로 한 줄이었는데,
실제로는 **읽기 경로가 환경변수를 안 탔다**:

- 쓰기 `runlog.RUNS_DIR` 은 env 로 옮길 수 있었다
- 읽기는 **하드코딩** — `bm_scorer.py:36`(verdict·cards 가 원장을 읽는 유일한 문) ·
  `bm_layer.py:71` · `run.py`(재사용·코퍼스 색인) · `pipeline` · `runner`
- 게다가 **하네스와 드라이런도 `runs/` 밑에 썼다** — `:ro` 자리라 컨테이너에서
  **그 자리에서 죽었을 것**이다

→ **`research2/runpath.py` 신규.** 「읽기는 둘 다(생성분이 먼저), 쓰기는 `runs-generated/` 만」을
한 곳에서 답한다. `os` 밖에 아무것도 import 하지 않는 **잎 모듈**이라 유리벽을 안 넘는다 —
`tests/test_verdict_canvas.py:52` 가 `runlog` 를 이름으로 막고 있어서 그렇게 할 수밖에 없었다.

### 만든 것

| 파일 | 무엇 |
|---|---|
| `research2/runpath.py` (신규) | 원장 위치의 유일한 답 |
| `research2/run.py` | `collect(CollectOptions) -> dict` 로 가름. `main()` 은 argparse 껍데기 |
| `research2/harness/slot_harness.py` | `run_harness(HarnessOptions) -> dict`. `SystemExit` 3곳 → `HarnessError` |
| `research2/tools/slot_dryrun.py` | `dryrun(DryrunOptions) -> dict`. 산출 자리 → `runs-generated/` |
| `app/research/pipeline.py` | `_collect()` 신규 · `_full(collect=…)` · 원장 없으면 만든다 |
| `compose.yaml` · `.gitignore` | `runs-generated` 바인드 + env, git 에서 전부 제외 |
| `MarketResearchWorker` · `application.yaml` · `MarketResearchInputFactory` | 예산·타임아웃 |

### `run.py` 실사 결과 — **우려보다 작았다**

계획서가 「493줄짜리 `main()` 을 가르는 작업량을 모른다」고 남긴 자리다. 읽어 보니
`main()` 은 argparse 로 `Namespace a` 를 만든 뒤 **전부 `a.<필드>` 읽기**였고
`_finish(a, …)` 도 `a` 를 통째로 받았다. `CollectOptions` dataclass 로 바꾸니
**본체는 한 줄도 안 바꿔도 됐다.** 진짜 걸림돌은 `ap.error()` 2곳뿐이었다.

### 멈추는 자리를 값으로 남긴다

| 어디서 멈추나 | 남기는 것 |
|---|---|
| 예산이 83회(하네스3+수집80)에 못 미침 | `BUDGET_EXHAUSTED` — **시작조차 안 한다**. 반쯤 사고 멈추면 돈만 나간다 |
| 하네스 게이트 미통과 | `HARNESS_GATE_FAILED` + 미통과 검사 이름. 스냅샷이 없어 수집할 슬롯이 없다 |
| kosis 슬롯이 있는데 stat_code 0개 | `STAT_CODE_UNRESOLVED` — **유료 수집을 안 태운다.** 무료로 알 수 있는 것을 유료로 알아내지 않는다 |
| KOSIS 키가 없어 대조 자체를 못 함 | `STAT_CODE_UNCHECKED` — 막지는 않되 조용히 넘기지 않는다 |

### 재수집하지 않는다

원장 이름 = **이름표**(`conceptId`)다. 같은 사업안을 다시 누르면 만들어 둔 원장을
**재채점**한다 — 수집은 유료다. BM 모드에서는 수집을 **절대** 시작하지 않는다(1단계 원장이
없는데 캔버스를 내면 근거 없는 캔버스가 된다).

### 실측으로 확인한 것 (전부 무료)

- `run.collect(CollectOptions(from_stage='a4', source_run='beauty-13', …))` 완주 —
  **씨앗 `runs/` 에서 읽고 `runs-generated/` 에 썼다.** CLI 도 그대로 돌고 사용법 오류
  문구도 종전과 같다
- `slot_dryrun.dryrun(...)` 함수 호출 완주 — 19슬롯 · 경로 `{kosis 4, web 14, dart 1}`
- 예산 관문이 **대역 없이 진짜로** 발동 — `llmBudget=5` 면 LLM 호출 0회로 멈춘다
- 검사: AI **592 passed / 4 failed**(물려받은 그대로) · research2 유리벽·하네스
  76·32·43·31·107 전부 통과 · 백엔드 타겟 41건 통과
- 부수 효과: 테스트가 만들던 원장이 더 이상 측정 원장 폴더를 오염시키지 않는다

### 11-6. ⚠ 아직 안 한 것 — **유료 실측 1회**

코드는 서지만 **「진짜로 수집이 도는가」는 확인되지 않았다.** 확인하려면 LLM ≈80회 +
KOSIS·DART·Tavily 호출이 필요하고, 그건 **돈이 나가는 결정**이라 사용자 승인이 있어야 한다.

실측 전에 반드시 볼 것:
1. `OPENAI_API_KEY`·`KOSIS_API_KEY`·`DART_API_KEY`·`TAVILY_API_KEY` 가 **실제로 들어 있는가**
   (`compose.yaml` 이 뒤 셋을 `:-` 로 두어 없어도 뜬다 — 없으면 조용히 `not_configured`)
2. `tools/preflight.py` 로 키·크레딧 확인
3. 확인할 것: `runs-generated/<이름표>/` 에 세 파일(`result.json`·`run.jsonl`·`a3_bodies.json`),
   `result.json.input.slots` 가 비지 않음, `degradations` 가 3건(NOT_WIRED)에서 0건으로 줄어듦

### 다음 — 3단계(배선)

`MarketResearchService.startFull` 이 Market Seed 를 읽어 `ResearchConceptFactory`(1단계)를
부르게 한다. **지금은 아직 아무도 변환기를 부르지 않는다.** 트윈 시드 조회 구멍(§5)도 같이 막는다.

---

## 12. 3·4단계 완료 기록 — 2026-08-11 (배선 · 경쟁 씨앗)

### 3단계 — 시장조사가 사업안을 태운다

```java
var seed = seeds.current(projectId).orElse(null);
if (seed != null) {
    label   = seeds.conceptIdOf(seed);                       // 원장 이름 = DB 식별자
    payload = concepts.build(label, snapshot, 비용3칸, 경쟁씨앗);
}
```

화면은 **여전히** 컨셉 자리에 `null` 과 견본 이름표를 보낸다. 시드가 있으면 **그것이 진다** —
안 그러면 「관측은 견본, 잣대는 내 사업안」이 된다.

**⚠ 이름표를 UUID 로 바꿨다.** 스냅샷 본문의 `conceptId` 는 **AI 후보 id**(「C1」)라
다른 프로젝트와 겹친다. 그것이 원장 디렉터리 이름이 되면 프로젝트끼리 원장을 덮어쓴다.
정본은 `ConceptPortfolioSelection.conceptId`(UUID)이고, 변환기가 이름을 **밖에서 받는다**.

**⚠ 시드 조회를 한 곳으로 모았다** — `MarketAnalysisSeedLookup`. 병합이 남긴 트윈 구멍의
원인이 **답이 두 벌**이었던 것이라, 세 번째 소비자를 붙이면서 합쳤다(사업안 우선 · 없으면 레거시).

### 4단계 — 경쟁 씨앗

`V21__research_competitor_seeds.sql` (⚠ 계획서의 V18 이 아니다) · 엔티티·리포·서비스 ·
`GET/PUT /api/v2/projects/{id}/competitor-seeds` · `CompetitorSeedForm.jsx`.

- **씨앗 0개를 막지 않는다.** 경고만 돌려준다 — 백로그 39 가 「수리 대상」으로 남긴 자리다
- **씨앗이 없으면 `_경쟁_씨앗` 칸 자체를 안 만든다.** 빈 블록을 실으면 하네스가
  「씨앗이 있다」로 읽어 `corp_name` 을 요구하고 모델이 없는 회사를 지어낸다
- 화면 위치는 **조사 실행 위**다 — 하네스가 슬롯 설계 때 읽으므로 조사 뒤에 적으면
  그 판에는 반영되지 않는다

### ⚠ 유료 실측에서 잡힌 진짜 버그 — 「베낀 값은 갈라진다」

첫 유료 시도가 **2초 만에** `harness 실패 — OPENAI_API_KEY 없음` 으로 죽었다(돈은 안 나갔다).
그런데 같은 순간 `tools/preflight.py` 는 `openai ok` 라고 했다. **두 답이 정반대였다.**

원인: 같은 일을 하는 코드가 두 벌인데 하나만 고쳐졌다.

| | `.env` 탐색 깊이 |
|---|---|
| `adapters/base.py:load_env_key` | **6단계** — 판 ㉝ 이식으로 두 단계 깊어진 것을 반영했고 주석까지 남겼다 |
| `harness/slot_harness.py:_env_key` | **4단계** — 「base.py 와 같은 탐색 순서」라 적어 놓고 **안 고쳐졌다** |

저장소 루트 `.env` 는 research2 기준 4단계 위라 **범위 밖**이었다. 컨테이너에서는
compose 가 환경변수로 넣어 주므로 **드러나지 않는 버그**였고, 하네스를 파이프라인에
붙이는 순간 처음 드러났다. 2026-08-11 수정 — 깊이를 고칠 일이 생기면 **두 곳을 같이 고친다.**

### ⚠ preflight 가 tavily 로 「진입 금지」를 낸다

`tools/preflight.py:144` 가 tavily 를 필수로 세는데 **수집 경로는 tavily 를 안 쓴다** —
web 어댑터는 OpenAI `web_search` 툴이다(`adapters/web.py:121`). Tavily 는
`harness/tavily_intake.py` 적재기 전용이다. 이 판에는 해당 없는 사유로 막는 것이라
사용자 결정으로 넘어갔다. **preflight 를 고치는 것은 별건이다.**

### 그 밖에 `:ro` 자리에 쓰던 것들 (2단계 해결자로 같이 옮김)

`slot_harness`(하네스 산출) · `slot_dryrun`(드라이런 산출) · `preflight`(사전점검 기록).
전부 `runs/` 밑이라 **컨테이너에서 그 자리에서 죽었을 것**이다.

---

## 13. 하네스 게이트 해체 분석과 수리 — 2026-08-11

첫 유료 실측에서 하네스가 **3/3 미통과**했다(계획서 §남는 위험이 예고한 자리).
세 판본을 무료로 다시 판정해 원인을 갈랐다.

### 13-1. 무엇이 일어났나 — 되먹임이 진동했다

| 시도 | 슬롯 | 미통과 |
|---|---|---|
| 1 | 16 | **3건** — `F_GROWTH` claim_type ×2 · `value_range [0,0]` · web 계량+corp_name |
| 2 | 15 | **1건** — `value_range [0,0]` 만 |
| 3 | 13 | **2건** — `F_GROWTH` ×2 · web 계량+corp_name **부활** |

**시도 2 가 1건 차이로 가장 가까웠는데 버려졌다.** 루프가 끝나면 `slots`·`report` 가
마지막 판본의 것이기 때문이다.

진동한 이유는 되먹임의 모양이다 — `violations` 는 **「이번에 실패한 것」만** 담고
「이미 맞춘 것」은 안 담는데, 모델은 슬롯을 고치는 게 아니라 **매번 새로 짠다**
(16→15→13개, slot_id 도 이동). 통과했던 제약이 프롬프트에 없으니 다시 어긴다.

### 13-2. 진짜 원인 — 세 위반 모두 「판단」이 아니라 「배선」이었다

| 위반 | 답이 하나인가 | 코드가 아는가 |
|---|---|---|
| `F_GROWTH` → `claim_type=GROWTH` | 예 | 예 (`vocab.식_목록.claim_type_강제`) |
| web 계량에 `corp_name` 금지 | 예 | 예 (`vocab.metric.catalog[…].route`) |
| `value_range [0,0]` | 예(상한>하한) | **아니다** — 상한이 얼마인지는 도메인 판단 |

그리고 **같은 종류를 코드가 이미 하나 잡고 있었다.** `wire()` 의 기존 주석:

> 가격 계량의 칸·claim_type 은 **세상에 대한 판단이 아니라 배선**이다 — 계량이 정해지면
> 답이 하나뿐이다. **모델은 식의 target(TAM/SAM)을 자꾸 따라 적었다(실측 3판).**

이번 실패가 정확히 그 문장이 적어 둔 양상이다. 가격 계량에서는 잡고 성장률에서는 안 잡았다.

### 13-3. 수리

1. **`wire()` 에 배선 2종 추가.** 규칙은 **베끼지 않고** 게이트가 읽는 `vocab.json` 의
   같은 자리를 읽는다([[copied-lookups-diverge]] 의 교훈).
   → 저장된 세 판본 무료 재판정: **시도 3 이 통과**(2건→0건), 시도 1 은 3건→1건.
2. **최선 판본 채택(best-of-N).** 시도마다 위반 수를 세어 최소인 판본으로 마감하고
   `_decide("최선 판본 채택", …)` 로 남긴다. 재시도는 개선을 **보장하지 않는다**.
3. **프롬프트 규칙 6 에 한 줄.** 게이트는 「상한>하한」을 요구하는데 프롬프트가 그 말을
   안 했다 — 코드가 스스로 「채울 수 없는 칸을 강제한 덫」(판 ⑫ ②)이라 이름 붙인 구조다.
   **완화가 아니라 정합이다.** ⚠ 저장된 판본으로는 못 잰다(옛 프롬프트 산물).

### 13-4. 모델 탓이 아니었다

`slot_harness.MODEL = "gpt-4o"` 가 기본이고 주석이 「gpt-4o-mini 는 형식 예시를 베끼거나
통제 어휘를 어겼다(2회 폐기)」라 적고 있다. `.env` 의 `AI_MODEL`(mini)은 **제품 AI 서버**용이라
하네스와 무관하다. 첫 보고에서 「모델 급이 원인일 수 있다」고 추정한 것은 **틀렸다.**

### 13-5. 곁가지로 잡힌 것 — 검사가 옛 자리를 보고 있었다

`test_failopen.py` 가 `runs/harness/<tag>` 를 봤는데 산출은 `runs-generated/` 로 옮겼다.
**옛 자리에 남은 산물 때문에 검사가 통과처럼 읽혔다.** 같이 고쳤다(19/19).
그때 `slot_harness` 의 `runpath` import 가 **CLI 경로에서만** 죽는 것도 드러났다 —
파이프라인에서는 부르는 쪽이 ROOT 를 이미 `sys.path` 에 넣어 두어 가려져 있었다.

### 13-6. 두 번째 유료 실측에서 잡힌 것 — **상대 경로는 CWD 기준이다**

배선 수리 뒤 하네스는 **통과했다**(시도 3, 위반 0). 드라이런도 통과(kosis 4개 중
stat_code 3개 해결). 그런데 **수집이 시작하자마자 죽고 원장 디렉터리만 빈 채로 남았다.**

원인: 하네스는 CLI 관례대로 `data/slots_<tag>.json` 이라는 **ROOT 상대 경로**를 돌려주는데
`run.py` 는 그것을 `json.load(io.open(a.slots))` 로 **CWD 기준**으로 연다. CLI 는
research2 에서 돌아 맞아떨어졌지만 **함수로 부르면 CWD 가 다르다** —
`runner.py` 가 서브프로세스에 `cwd=RESEARCH_HOME` 을 주던 것이 그 사실을 가리고 있었다.
**컨테이너의 CWD 는 `/app` 이라 프로덕션에서도 터졌을 자리다.**

수리: `pipeline._collect` 가 하네스 산출 경로와 컨셉 경로를 **절대 경로로 만들어** 넘긴다
(「엔진 함수는 경로를 받는다」는 규율의 연장). `run.py` 의 경로 해석은 건드리지 않았다 —
CLI 사용자가 CWD 기준으로 넘기는 관례가 있다.

### 13-7. 첫 두 판의 성적 (참고)

| 판 | 하네스 | 드라이런 | 수집 |
|---|---|---|---|
| 1차 | 3/3 미통과 (배선 없음) | 안 돔 | 안 돔 |
| 2차 | **통과**(시도 3) | **통과** 3/4 stat_code | 경로 버그로 죽음 |

두 판 모두 **유료 수집은 안 탔다** — 게이트가 앞에서 막았고, 그것이 설계 의도다.

### 13-8. 세 번째로 놓친 읽기 자리 — **`head -30` 이 전수인 줄 알았다**

`tools/scorecard.py` 도 원장을 `ROOT/runs/<id>` 로 하드코딩해 읽고 있었다. 2단계에서
읽기 자리를 모을 때 `grep … | head -30` 으로 세었는데 **목록이 30줄에서 잘려 있었고**
그것을 전수로 착각했다. 실제로는 **32곳**이다.

자르지 않고 다시 센 결과:

```
grep -rn 'os.path.join(ROOT, "runs"' --include=*.py . | grep -v /tests/   → 32곳
```

그중 **제품 경로(파이프라인이 import 하는 것)는 `tools/scorecard.py` 뿐**이고
나머지는 연구용 분석 CLI(`eval_search`·`golden_probe`·`grade_audit`·`render_report` …)다.
`service/canvas.py:52` 의 `runs/userdocs-*` 전역 glob 은 계획서가 「이번 범위 밖」으로
적어 둔 자리라 그대로 두었다 — 생성 원장에는 `userdocs-*` 가 없으므로 씨앗 쪽 문서
적재 기록을 읽는 **기존 동작 그대로**다.

> **교훈: 목록을 세는 명령에 `head` 를 붙이면 안 된다.** 「몇 곳인가」를 묻는 자리에서는
> 자르지 말고 `nl`·`wc -l` 로 개수를 확인한다. 이번에 그것 때문에 유료 실행을 한 번 더 썼다.

### 13-9. 서비스 층이 생성 원장을 읽는다 — 확인됨

`RESCORE`(LLM 0회)로 `runs-generated/smoke-collect-01` 을 재채점:
`verdict`·`cards`·`scorecard` 전부 `OK`, 7과목 성적표가 나왔다. **성적 3·0·3**
(견본 `beauty-13` 은 5·1·0 — 여러 판을 거친 원장이라 두껍다).

TAM 값이 견본과 같은 것은 같은 KOSIS 표(`101/DT_1K52F01`)에서 같은 사업체 수를 관측하고
침투율·단가가 같은 컨셉 파일에서 오기 때문이다. 확인됨이 3건 vs 5건으로 달라 **별개 원장이 맞다.**

---

## 14. 판 ㉛ — 깔때기 계측과 수리 (2026-08-11, 미커밋)

> **상태: 0~6단계 완료 · 7단계(유료 실측)만 남았다.** 아래 수치는 전부 `LLM 0회`로 잰 것이다.

### 14.1 왜 했나 — 진단이 우선순위를 뒤집었다

착수 시 계획은 A4 격리와 성적표 문턱을 손보는 것이었다. 원장을 전수로 세니 **병목이 두 단계
위**였다. `runs-generated/smoke-collect-01` 깔때기:

| 단계 | 들어감 | 나옴 | 잃음 |
|---|---|---|---|
| 검색 후보 | 79 | 76 | 3 (URL 정규식) |
| 본문 판정 | 81 | 57 | 24 (empty 8 · js_shell 7 · mojibake 9) |
| 상한 절단 | 53 | 44 | 9 (`extract_max_docs=5`) |
| 본문 절단 | 203,452자 | 110,122자 | **45.9%** (`extract_doc_chars=6000`) |
| **발췌** | 44 | **1** | **43** |
| A4 채택 | 5 | 3 | 2 (`must_contain`) |

경로별 수율 — **폴백을 감안해 「실제로 값을 낸 경로」로 묶는다**(S4는 kosis 라우팅이지만 web 발췌로 폴백):
`kosis` 3/3(100%) · `dart` 1/1(100%) · `extract`(web) **1/44 = 2.3%**.

성공한 슬롯은 전부 「문서 1건 · 구조화 응답」이고, 실패한 12개는 전부 「문서 여러 건 · HTML 본문」이다.

### 14.2 무엇을 고쳤나

**0. 계측 — `tools/funnel.py` 신설** (`scorecard.py` 와 같은 수: LLM 0회 · 원장 쓰기 0회)
`a3_extract` 원장 노드를 신설해 잘린 문서·절단량을 **값으로** 남긴다(예전엔 `note` 문자열 안에만
있어 셀 수 없었다). 기준선은 `runs-generated/smoke-collect-01/funnel_before.json` 에 얼려 뒀다.
⚠ 도구가 손 계산 두 개를 바로잡았다 — 절단 손실은 발췌를 탄 44건에만 적용해야 하고(DART 공시
121,640자는 발췌를 안 탄다), 수율은 `a2_route` 가 아니라 finding 의 `trace_id` 접미사로 묶어야 한다.

**1. 발췌 재설계** (`adapters/web.py`)
문서 5개 묶어 1회 → **문서 1개당 1회**(`extract_mode=per_doc`). `extract_doc_chars` 6000→20000 ·
`extract_max_docs` 5→12 · 코드 상수였던 `MAX_CANDIDATES`(6→12)·`MAX_ITEMS`(3→8) 규칙 파일로 이관.
`_doc_index` 는 **지웠다** — 문서가 하나라 인용 소속이 자명해져 옛 탈락 지점 D5가 구조적으로 사라졌다.
문서 하나의 LLM 실패는 슬롯을 안 죽이고, **전멸하면 예외를 올린다**(키 고갈이 「자료 부재」로
둔갑하면 §7이 거짓이 된다). 잘린 문서는 §7 `extract_capped` 로 나간다.

**2. 본문 판정 — 증거가 계획을 뒤집었다**
- `mojibake` 9건은 **문서가 깨진 게 아니라 우리가 깨뜨린 것**이었다. `requests` 는 Content-Type 에
  charset 이 없으면 ISO-8859-1 로 가정한다(HTTP 1.1 유산). `fetch` 가 `apparent_encoding` 으로
  디코딩하도록 고쳤고 실측 재수집에서 3건 전부 usable 복구 — `steppay.kr/pricing` 은 자릿수
  409개짜리 요금표였다(PRICE 슬롯 재료). **판정 규칙은 그대로 뒀다. 고친 것은 입력이다.**
- `js_shell` **완화는 하지 않았다.** 7건 전수 확인 결과 전부 진짜 껍데기(오탐 0건). 판단 근거를
  `rules/scoring.v1.json` 에 남겼다 — 다음 판이 같은 가설을 다시 세우지 않게.
- `empty` 8건은 §7 `fetch_empty` 로 분리. 「걸렀다」가 아니라 「못 가져왔다」다.

**3. A4 격리** (`blocks/a_desk.py`)
`stat_code`/`corp_name` 으로 라우팅된 슬롯은 `must_contain` 을 면제하고 근거를 `Fact.슬롯_보증` 에
값으로 남긴다. 나머지 5겹(`must_not_contain`·단위·계정·`value_range`·기간)은 무변경.
그리고 하드코딩 대신 **슬롯이 `subject_aliases` 를 들고 온다** — 하네스가 설계 때 LLM 으로 한 번
뽑고 **A4 는 여전히 LLM 0회 결정론**이라 `--from` 재실행이 안 갈린다. 게이트 `G23 표기 변종` 신설.

> **근인:** corpcode 사전의 키는 **`NAVER`** 이고 **`네이버` 는 사전에 없다.** 견본이
> `이름="네이버 예약"` / `운영사="NAVER"` 라 `corp_name=NAVER` 로 DART 조회는 성공했는데(12조 매출
> fetch) `subject` 가 「네이버 예약」이라 `must_contain=["네이버"]` 가 됐다 — **파이프라인이 스스로
> 만든 모순**이다.

**4. 성적표** (`rules/fill.v2.json` · `tools/scorecard.py`)
`3_경쟁사`·`4_가격` 채워짐 2→1. ⚠ **가격 밴드 규칙은 무변경** — 1건으로 밴드를 그리면 밴드가
거짓이 된다. 규약 위반 둘도 정리: `2_성장률`·`6_계산` 이 「문턱은 규칙 파일에서만 온다」고 선언해
놓고 코드 안 조건문으로 판정하고 있었다. `5_수요` 의 `... or True` 상시참 버그는 **근인이
따로 있었다** — `result.json` 에 `facts` 키가 아예 없어 필터가 항상 거짓이었다(값은 `a4_facts`
노드에 있다). 이제 정량/정성이 실제로 갈린다.

**5. Tavily** (`harness/tavily_intake.py`)
네트워크 0회로 끝까지 확인: 적재 → 코퍼스 색인 적중 → `channel=tavily` 보존.
**산출 위치 버그 수정** — 씨앗 원장 `runs/`(컨테이너에서 `:ro`)에 쓰고 있었다. `runpath.write_dir()` 로.
자동 배선(파이프라인이 스스로 호출)은 **이번 판에서 안 한다** — 효과를 분리해 재야 한다.

**6. HMR 견본** — `data/concept_hmr-solo.json` (계열 C · T7 거래액×점유율).
최상위 비-언더스코어 키 9개 정확, `hypotheses` 빈 배열, 경쟁 씨앗 법인명 5개 전부
`adapters/_cache_corpcode.json` 에서 실증(CJ제일제당 00635134 · 오뚜기 00141529 ·
풀무원식품 00684732 · 프레시지 01372700 · 신세계푸드 00274933). `pipeline.CONCEPTS` 에 `hmr-solo` 등록.

**곁다리:** `tools/scorecard.py` 의 `_run` 이 이 환경에서 **원래 깨져 있었다.** 자식 파이썬을
`-X utf8` 없이 띄워 한글 JSON 출력이 CP949 로 죽고, 부모의 stderr 디코딩까지 터져 `out.stderr` 가
`None` 이 되면서 실패가 `TypeError` 로 둔갑했다. 검증을 막고 있어 고쳤다.

### 14.3 잰 것 — 전부 LLM 0회, 옛 문서 그대로

`python -X utf8 run.py --id regrade-31 --from a4 --source-run smoke-collect-01 --concept data/concept_beauty-noshow.json`

| | before | after |
|---|---|---|
| 성적표 | **3·0·3** | **4·0·2** (COMPETITOR 열림) |
| 확인됨(채택) | 3 | **4** |
| 충족 슬롯 | 3/16 | **4/16** |
| 본문 도달률 | 54.1% | **87.3%** |

**발췌 수율 2.3% 는 그대로다 — 당연하다.** 재채점은 저장된 발췌 결과를 다시 쓰므로 문서별 호출의
효과가 나타나지 않는다. 그건 유료 판에서만 잰다.

검사: `test_harness 107` · `test_failopen 19` · `test_verdict_canvas 76` 전부 통과.
`cd ai; pytest -q` → **591 passed · 1 skipped · 4 failed**.
- 실패 4건은 **물려받은 것**이다(전부 `concept_portfolio_v2`, `seed.fields` 검증 — 이 작업과 무관).
- skip 1건은 `tests/test_market_research.py:202` — `CONCEPTS` 에 `hmr-solo` 를 등록했는데 아직
  `runs/hmr-01` 원장이 없어서다. **유료 판을 돌리면 해소된다.**

### 14.4 다음 세션이 할 것 — 7단계(유료 실측) 하나

**착수 전 결정 둘:**
1. `.env` 에 `TAVILY_API_KEY` — 없으면 `tools/preflight.py` 가 `bad_key` 로 「진입 금지」를 낸다.
2. **`llmBudget`.** 기준선으로 계산하면 발췌 12회 → **53회**, 검색 24회를 더해 **77회**이고 한도가 90이다.
   견본이 바뀌고 Tavily 가 문서를 더 물어오면 **넘긴다.** 유료 판 **전에** 드라이런으로 실제 호출 수를
   세고, 넘치면 예산을 올릴지 `extract_max_docs` 를 낮출지 정한다 — 터진 뒤에 알면 판을 통째로 버린다.

**순서:**
```powershell
cd ai/app/research/research2
python -X utf8 tools/preflight.py                      # 4개 다 ok 여야 한다
python -X utf8 tools/slot_dryrun.py ...                # 슬롯 설계 + 호출 수 예측 (LLM 0회)
python -X utf8 harness/tavily_intake.py --slots data/slots_hmr-01.json --tag tavily-hmr-01
# 그 뒤 ai/ 에서 FULL 1회
```
```python
task_input = {"mode":"FULL","conceptId":"hmr-01","asOf":"2026-08-11","llmBudget":90,
              "textContents":[{"contentKey":"concept","chunks":[{"index":0,"text":<concept json>}]}]}
await pipeline.run_market_research(task_input, "<runId>", 3000)
```

**볼 것** — `tools/funnel.py --run <runId>` 로 재고 `funnel_before.json` 과 대조한다.
주지표는 **발췌 수율**(지금 2.3%)이다. 성적표는 `tools/scorecard.py` 로.

⚠ **견본과 규칙을 동시에 바꿨으므로 유료 판 하나로는 원인이 분리되지 않는다.** 위 무료 재채점이
각 단계의 단독 효과를 이미 잡아 뒀다 — 유료 판은 **합계**를 재는 것이지 원인을 가르는 것이 아니다.

### 14.5 알고 두는 위험

- **제품 경로는 여전히 계열 A 하드코딩**이다(`ResearchConceptFactory.java:55`). HMR 견본이 성공해도
  실제 B2C 사업안이 들어오면 계열 A 로 간다. 이번 판에서 안 고쳤다.
- `ai/app/research/research2/data/slots_smoke-collect-01.json` · `formulas_smoke-collect-01.json` 은
  유료 실측 산물이다. **커밋에 넣지 말고 지우거나 gitignore 한다.**
- 이 작업 전체가 **미커밋**이다(브랜치 `market-research-v2`, 마지막 커밋 `ec1d163`).
  `git add -A` 금지 — 로컬 원자료가 섞인다.

---

## 15. 판 ㉛ 7단계 — 유료 실측 (2026-08-11, 미커밋)

> **결과: 입력 쪽 수리는 전부 먹혔고, 주지표인 발췌 수율은 안 움직였다.**
> 그리고 **§14.1 의 진단이 틀렸다** — 병목은 발췌가 아니라 검색이다. 아래는 그 근거다.

### 15.1 무엇을 돌렸나 — 견본이 바뀌었다

§14.4 는 HMR 견본으로 돌라고 했다. **못 돌렸다.** 드라이런에서 kosis 슬롯 3개(S1 TAM ·
S4 GROWTH · S5)의 `stat_code` 가 하나도 서지 않았고, `pipeline.py:548` 이 「kosis 슬롯이
있는데 하나도 안 서면 유료 수집을 태우지 않는다」로 막는다. **돈 쓰기 전에 멈추라고 넣어 둔
문이 제대로 작동한 것이다.**

근인은 pet-treat 때와 같은 표기 불일치다(백로그 41·42 · `rules/adapters.v1.json` 판⑬).
KOSIS `101/DT_1KE10041` 의 상품군 축을 **눈으로 열어 확인했다**:

```
합계 · 컴퓨터 및 주변기기 · 가전·전자·통신기기 · 서적 · 사무·문구 · 의복 · 신발 · 가방 ·
패션용품 및 액세서리 · 스포츠·레저용품 · 화장품 · 아동·유아용품 · 음·식료품 · 농축수산물 ·
생활용품 · 자동차 및 자동차용품 · 가구 · 애완용품 · 여행 및 교통서비스 ·
문화 및 레저서비스 · 이쿠폰서비스 · 음식서비스 · 기타서비스 · 기타
```

**「냉동 간편식」은 국가통계에 없다.** DART 경쟁사 4슬롯(비비고·오뚜기·풀무원·신세계푸드)과
web 6슬롯은 멀쩡하다 — 막힌 것은 TAM·성장률의 밑동뿐이다.

그래서 **beauty-noshow 로 돌렸다.** 이 선택이 오히려 옳다: 얼려 둔 기준선
`funnel_before.json` 이 바로 이 견본이라 발췌 수율 before/after 가 1대1로 비교된다.
HMR 로 돌았으면 견본과 규칙이 동시에 바뀌어 숫자가 무슨 뜻인지 말할 수 없었다(§14.4 의 경고).

원장 `paid31-beauty` · 슬롯 13(kosis 3 · web 9 · dart 1) · `stat_code` 3/3 · **LLM 58회**(예산 90).
Tavily 씨앗은 **넣지 않았다** — 기준선이 Tavily 없이 잰 것이라 씨앗을 넣으면 비교가 오염된다.

### 15.2 잰 것

| | 기준선 `funnel_before` | `paid31-beauty` |
|---|---|---|
| 본문 판정 손실 | 24 (empty 8 · js_shell 7 · **mojibake 9**) | 12 (empty 9 · js_shell 3 · **mojibake 0**) |
| 본문 도달률 | 54.1% | **91.8%** |
| 상한 절단 손실 | 9건 (`extract_max_docs=5`) | **0건** (`=12`) |
| 글자 절단 손실 | 45.9% | **8.2%** (`extract_doc_chars=20000`) |
| **발췌 수율(web)** | **2.27%** (44 → 인용 1) | **2.5%** (40 → 인용 1) |
| 확인됨(채택) | 3 | **5** |
| 성적표 | 3·0·3 | **4·0·2** |

입력 쪽 수리(§14.2 의 2번 = `apparent_encoding`, 1번의 상한 완화)는 **전부 먹혔다.**
그런데 문서를 묶지 않고 하나씩 부르고, 잘린 본문을 온전히 넣어 줬는데도 **web 발췌가 낸
인용은 여전히 1건이다.**

### 15.3 왜 안 움직였나 — 진단이 뒤집혔다

`tools/extract_triage.py` 로 실패 8건을 갈랐다 (LLM 0회 · 저장 문서만 읽는다):

| 분류 | 건수 | 슬롯 |
|---|---|---|
| (a) 발췌가 못 읽었다 | 2 | S11 · S13 — **거짓 양성이다** |
| (b) 질의-문서 불일치 | 3 | S3 · S10 · S12 |
| (c) 조건 만족 값 없음 | 3 | S6 · S7 · S8 |

**(a) 2건은 도구의 잣대가 틀린 것이다.** S11 의 「값=96112」는 `S96112두발 미용업` —
**KSIC 산업분류 코드**고, S13 의 「값=5」는 네이버플러스 멤버십 안내문의 파편이다.
둘 다 슬롯의 `value_range` 하한이 `0` 이고 `must_contain` 이 비어 있어 통과했다.
즉 **발췌 LLM 이 놓친 값은 0건**이고, 오히려 이 쓰레기를 안 집은 것이 옳았다.

> **결론: 8건 전부 문서 쪽 문제다.** §14.1 이 「병목은 발췌였다(2.3%)」로 읽은 것은 손실이
> 발췌 **단계**에서 집계된다는 뜻이었지, 발췌 **행위**가 원인이라는 뜻이 아니었다.
> 같은 자리에 두 가지가 겹쳐 있었고 우리는 뒤엣것을 고쳤다. **다음 지렛대는 발췌 방식이
> 아니라 「검색이 무엇을 물어오는가」다.**

곁다리: 이번 판 하네스가 만든 슬롯 13개의 `must_contain` 이 **전부 비었다**(드라이런
「가드 경고 슬롯 13/13」). 하한 0 짜리 `value_range` 와 겹치면 슬롯이 아무 숫자나 받는다 —
위 (a) 거짓 양성이 그 증상이다.

### 15.4 고친 것 (1줄)

`tools/extract_triage.py` — `runs/` 만 보고 있어서 새로 수집한 원장을 「없다」고 했다.
`runpath.read_dir()` 로 바꿨다. `tavily_intake`·`scorecard` 에서 이미 한 번씩 고친 것과
**같은 병**이다(베낀 조회는 갈라진다).

### 15.5 §14 문서가 틀린 곳 셋 — 다음 판이 같은 데서 헛돌지 않게

1. **§14.4 의 예시 `task_input` 은 수집을 안 켠다.** `conceptId` 가 `CONCEPTS` 프리셋에 있으면
   저장된 원장 **재채점**으로 빠진다(`pipeline.py:250` — 수집은 `inline` 컨셉이 있고 그 이름의
   원장이 없을 때만 켜진다). 컨셉을 `textContents` 로 **인라인으로 실어야** 한다.
   실측: 첫 시도가 여기서 헛돌았다(단계 3개 `SKIPPED`, 비용은 요약 3회).
2. **§14.4 의 `tavily_intake` 단계는 FULL 에 닿지 않는다.** `pipeline.py:562` 가 `direct_urls` 를
   넘기지 않는다 — 적재물은 CLI `run.py --direct-urls` 경로에서만 들어간다(§14.2-5 「자동 배선은
   안 한다」가 여기 그대로 드러난 것이다).
3. **하네스 게이트 「stat_code 실재 대조」는 `stat_code` 가 채워진 슬롯만 본다.** hmr-01 은 3개 다
   `null` 이라 **공허하게 통과**했고 그다음 드라이런에서 죽었다. 게이트 23개 전통과가 유료 판
   진입 가능을 뜻하지 않는다.

### 15.6 A 단계 — HMR 수리 (완료, 미커밋)

**결정(사용자):** 다리는 **재는 데까지만** 놓는다 · 울타리는 **치환한 자리에서** 기록한다.

**장치는 이미 있었다.** 판 ⑰ 이 `subject_별칭` 다리 + `상위_카테고리.상한_울타리` + 경계를
만들어 뒀고 등록된 항목은 「애완용품」 하나였다. `expected.md:2264` 에 「사다리 거래액 축 2단
발동 — **미도달**」로 남아 있던 것을 **이번에 처음 발동시켰다.**

**구멍 하나를 먼저 막아야 했다.** 울타리는 `off_slot_reason` 의 다리 갈래에서만 붙는데
그 갈래는 **`must_contain` 이 있어야** 실행된다. 판 ㉛ 유료 실측에서 하네스가 슬롯 13개의
`must_contain` 을 **전부 비웠으므로**(§15.3 곁다리) 그 상태로는 38조가 **경계 없이** TAM 에
앉는다 — **울타리 없는 2단**이고 이 파이프라인이 없애려는 실패 그 자체다.

| 파일 | 무엇 |
|---|---|
| `rules/adapters.v1.json` | 「냉동 간편식」→「음·식료품」 별칭 · 「음·식료품」 `상한_울타리` + 경계 2줄 |
| `schema.py` | `Finding.표기_치환` 신설 — 치환을 `note` 문자열이 아니라 **값**으로 |
| `adapters/kosis.py` | `resolve_axes` 가 치환을 **4번째 반환값**으로 내고 `collect` 이 Finding 에 싣는다 |
| `blocks/a_desk.py` | 울타리 조회를 `울타리()` **한 곳**으로 모으고, `normalize` 가 사실이 태어날 때 붙인다 |
| `tests/test_step15.py` | 신설 22개 (반례 4개 — 다리가 문을 **여는** 게 아니라 **넓히는** 것의 증명) |

> ⚠ **「음식서비스」에는 다리를 놓지 않았다.** 같은 표에 있지만 배달이라 **다른 시장**이다.

**관통 확인 — 실제 KOSIS 호출 · LLM 0회:**

```
슬롯 S1 · subject='냉동 간편식' · must_contain=[]      ← 비어 있다
표기_치환 = [{슬롯_표기: 냉동 간편식, 통계_표기: 음·식료품}]
값 = 38,041,110,000,000원 (2025) · off_slot = None
표기_다리 = [{상한_울타리: true, 경계: 2줄}]           ← 그래도 붙었다
기대_밖  = 자릿수 차이 1.88 (≤3) 이라 통과 · 값으로 기록
```

**검사:** `test_step15` 22/22 · 회귀 `test_harness 107`·`test_failopen 19`·
`test_verdict_canvas 76` 전부 통과 · `cd ai; pytest -q` → **591 passed · 1 skipped · 4 failed**
(실패 4건은 §14.3 과 **같은 물려받은 것** — `concept_portfolio_v2` `seed.fields`. 새로 깨진 것 0).
**hmr-01 드라이런 `stat_code` 0/3 → 3/3** — `pipeline.py:548` 하드 게이트를 넘는다.

### 15.6a A 단계의 한계 — 다음 판이 속지 않게

**별칭은 정확 일치다.** 유료 판은 하네스를 **새로 돌려** 슬롯을 다시 설계하므로, LLM 이 이번에
「냉동 간편식」이라 쓴 자리에 다음엔 「프리미엄 냉동 간편식」이라 쓰면 **벽이 되돌아온다.**
규칙 파일이 스스로 적어 둔 그대로다(`_별칭은_정확일치다`) — 이번 수리는 **이 스냅샷을 끝까지
태우기 위한 손 채움**이지 해법이 아니다. 근본 경로는 여전히 백로그 5(업종 사전 자동 구축)다.

**그리고 38조는 헐거운 상한이다.** 울타리가 제대로 붙어도 TAM 숫자로서 쓸모는 거의 없다 —
실질 수확은 **성장률**(전년 대비 비율이라 상위 카테고리라도 대리로 쓸 만하다)이다.
경계 문구가 이것을 값으로 말하고 있어야 하고, 지금 그렇게 적혀 있다.

### 15.7 B 단계 — 발췌 입력 창 고르기 (측정했고 **안 켰다**)

**왜 했나.** `paid31a-hmr` 본문 도달률 **16.2%**(230만 자 중 37만 자). `extract_doc_chars` 는
문서 **앞에서** 자르는데, 요금표·통계표의 숫자는 중반 이후에 나온다. 상한을 올리면 토큰만
배로 들고 큰 문서에서는 비율이 그대로다.

**무엇을 만들었나.** `adapters/doc_window.py` — 닻(계량·주제 낱말) 주변 창을 골라 담는다.
닻 위치는 `tools/extract_triage.py` 와 **같은 곳**을 본다(같은 물음을 두 곳이 각자 풀지 않게).
⚠ **판정 조건은 공유하지 않는다** — triage 잣대는 주제+단위+범위를 전부 요구하는 엄격한
것이라 그대로 쓰면 표현이 다른 문서를 통째로 버린다. 창은 거르는 게 아니라 **고르는** 것이라
그 신호들을 **순위**로 쓴다.

**측정 — `paid31a-hmr` 문서 53건 위에서 발췌만 재실행(수집 0회), 네 판:**

| 판 | 조건 | 보낸 글자 | 인용 | **확인됨** |
|---|---|---|---|---|
| win-off | 앞자르기 | 395,144 | 10 | **3** |
| win-on | 창 r=1500 | 315,915 | 11 | 1 |
| win-r3000 | 창 r=3000 | 345,969 | 10 | 0 |
| win-fill | 창 r=1500 + 예산 채움 | **395,144** | **13** | **0** |

마지막 판은 보낸 글자가 앞자르기와 **정확히 같은데**(공정 대조) 인용을 더 캐고도 확인됨이 0
이었다. 창 방식 고유의 실패인 **「인용 대조 실패」가 0 → 2** 로 새로 생겼다 — 구간을 이어
붙이면 모델이 창 경계를 넘어 문장을 잇고 `quote_verified` 가 그것을 잡는다.

> **결론: `enabled:false`.** ⚠ 「창이 틀렸다」가 아니라 **「켤 근거가 없다」**다 — 판마다
> 한 쌍씩이라 3 대 0 차이가 실행 요동보다 크다고 단정할 수 없다. 반복 측정은 안 했다.
> 코드·검사(`tests/test_step16.py` 28개)·계측기 개선은 남겼다. 사유는
> `rules/adapters.v1.json` `extract_window._왜_껐나` 에 수치째로 있다.

**도중에 잡은 계측기 결함 — 이게 더 중요할 수 있다.**
`tools/funnel.py` 가 `chars_sent` 를 원장에서 **읽지 않고** `min(문서길이, 상한)` 으로 다시
계산했다. 그것은 「앞에서 자른다」를 **가정한 식**이다. 그래서 창을 켠 판과 끈 판을
**글자 하나까지 같게**(둘 다 395,144자) 보고했다 — 실제로는 315,915 대 395,144.
**재는 도구가 방식을 가정하면 그 방식을 바꾼 판을 못 잰다.** 적힌 값을 읽도록 고치고,
`고른_방식`(head·window·whole·head_fallback)을 함께 찍고, **도달률은 「양」이지 「적중」이
아니라는 경고**를 붙였다.

### 15.8 C 단계 — 고정 스냅샷 반복으로 **6/6 달성** (9회차)

**착수 판단(사용자):** 대기업 신상품 기준이면 슬롯 **모양**은 계열 C·식 템플릿이 정하므로
거의 고정이다. 흔들리는 것은 **내용**(subject 표기·`must_contain`·`value_range`)뿐이다.
매번 새로 설계할 이유가 없다. → 스냅샷을 고정하고(`run.py --slots`) 안 되는 칸만 바꿔 반복.

> **제품은 이미 그렇게 돈다.** `pipeline.py:253` 이 「원장이 없을 때만 수집」이라 같은 사업안을
> 다시 누르면 재채점만 한다 — 설계는 컨셉당 한 번이다. 판 ㉛ 에서 본 변동은 **원장 이름을
> 매번 새로 만든 측정 방식** 탓이고 제품 경로에서는 안 일어난다.
> ⚠ **고정의 진짜 위험은 반대쪽이다** — 첫 설계가 나쁘면 그 컨셉은 영영 그것을 쓴다
> (`paid31a` 의 `must_contain=["성장"]` 이 실물 예시다). 「재설계 후 재수집」 경로가 **없다.**

| 회차 | 바꾼 것 | 성적표 | 배운 것 |
|---|---|---|---|
| pin-01 | (고정만) | 4/6 | **결과가 값까지 재현**된다(`paid31b` ↔ `pin-01`, 성장률 15.1464% 동일) — 변동 원인은 슬롯 설계였다 |
| pin-02 | 가격·수요 표적 교체 | 4/6 | S14 가 값을 찾았으나 격리 — 표적을 옮기며 **무관한 가드까지 옮긴** 내 실수 |
| pin-03 | 무관 가드 제거 | 4/6 | S13 격리는 **가드가 옳게 일한 것**(「1인가구 비율 36.6%」 ≠ 문제 경험률) |
| pin-04 | 가격을 계열 C 계량 `판매가` 로 | 4/6 | **후퇴.** 검색이 상거래 도메인을 하나도 안 물어왔다 |
| **pin-05** | **서식지별로 슬롯 분산**(14→17) | **5/6** | 가격 열림. 이것이 핵심 수였다 |
| pin-06 | 수요 표적 분산(→19) | 6/6 | **거짓 6/6** — 아래 참조 |
| pin-07 | 수요 가드를 **종류로** 조임 | 4/6 | 오답을 막았다. **깨지는 것이 정직한 결과** |
| pin-08 | 수요를 「그 계량이 발행되는 자리」로 | 5/6 | 수요가 **관련 있는 값**으로 찼다(혼자 식사 41.7%) |
| **pin-09** | 가격 서식지 2개 추가(→21) | **6/6** | 인용 전수 검사 통과 |
| pin-09b | (재현 확인) | **6/6** | **다른 슬롯이 채웠다** — 우연이 아니다 |

**핵심 수는 「한 칸에 표적 하나」를 버린 것이다.** 값이 사는 곳이 표적마다 다르므로 하나에
걸면 그 서식지를 검색이 못 물어온 판은 칸이 통째로 빈다. `paid31a` 가 가격을 채운 것도
표적이 좋아서가 아니라 그 서식지(정부 보도자료)를 그날 물어왔기 때문이다.

**⚠ 5회차의 함정 — 분산인 줄 알았는데 중복이었다.** pin-05 의 수요 두 슬롯이 subject 가 둘 다
「1인 가구」이고 `must_contain` 만 달랐다. `plan_query` 는 **subject·metric·period·region** 으로
검색어를 만들므로(`web.py:63`) 두 슬롯이 **같은 검색어**를 던졌다. **분산은 subject 로 한다.**

#### 15.8.1 가장 중요한 발견 — 성적표 문턱은 **종류를 안 본다**

pin-06 은 성적표 6/6 을 냈다. 그런데 수요를 채운 사실이 이것이었다:

```
S13  8.9%  등급=추정
"특히, 70대 이상 1인 가구의 우울증상유병률은 8.9%로…"   질병관리청
```

이 컨셉의 수요는 「25~44세 1인 가구가 혼자 저녁을 해결하며 재료를 버리는 문제」다.
**인구만 맞고 문제의 종류가 다르다 — 문턱을 넘긴 것이지 답을 찾은 것이 아니다.**

넘은 경로: subject 가 인구만 지목(「1인 가구」) · `must_contain` 이 **아무 데나 있는 낱말**
(「문제」 → 「정책지원이 필요한 **문제**」에 걸린다) · `value_range` 를 [1,90] 으로 넓힘.
셋 다 **내가 회차를 거듭하며 느슨하게 만든 것**이고, 마지막 것은 이 저장소가 규칙 파일에
적어 둔 「크기 필터로 종류 오류를 거르지 마라」와 정반대였다.

가드를 종류로 조이자(pin-07) **즉시 4/6 으로 내려갔다.** 그것이 정직한 결과다.

> **`must_contain` 은 `any()` 다**(`a_desk.py:678`) — 낱말을 늘리면 **조여지는 게 아니라
> 느슨해진다.** 종류를 가르려면 **영역 낱말 하나**로 둔다. 이것을 반대로 이해하고 있었다.

#### 15.8.2 최종 6/6 의 채택 값 — 전수 검사

```
가격  1,900 / 2,400 / 3,400원  배달비        농식품부 보도자료 · 경향신문
      4,200원                 편의점 도시락가  매일경제            (pin-09b)
수요  41.7%  혼자 식사 비율    통계청 (등급 확정)
      85%    배달 이용경험률   컨슈머인사이트
      29.8%  아침 결식률       농식품부            (pin-09b)
```

전부 이 사업안의 **대체재 가격과 타깃 행동**이다. pin-09 와 pin-09b 가 **서로 다른 슬롯으로**
6/6 을 냈다 — 여러 갈래 중 무엇이 걸려도 채워지는 구조가 섰다는 뜻이다.

⚠ 가장 약한 고리: S13 의 「배달 이용경험률 85%」는 `문제 경험률` 칸에 앉은 **이용률**이다.
값은 관련 있지만 계량 이름과 종류가 어긋난다. 다음 판이 여기를 조일 여지가 있다.

#### 15.8.3 산출물

`data/slots_hmr-pin09.json` — **6/6 스냅샷**(슬롯 21개). `_수정이력` 에 9회차가 전부 값으로
있다(무엇을 왜 바꿨는지 · 안 바꾼 것 · 각 회차의 실측 근거).
원장 `runs-generated/pin-01`~`pin-09b`.

> **⚠ 이것은 손 조립이다.** 「파이프라인이 스스로 6/6 을 낸다」가 **아니고**
> 「6/6 이 가능한 설계가 무엇인지 밝혔다」다. 그 구분을 흐리지 말 것.

### 15.9 다음 세션의 일 — 하네스가 그 설계를 **스스로** 내게 한다

`slots_hmr-pin09.json` 이 **목표 설계의 실물 견본**이다. 지금 하네스는 이것을 못 만든다.
못 만드는 이유가 회차마다 값으로 남아 있다:

1. **칸마다 표적을 하나만 낸다.** 서식지 분산 개념이 프롬프트에 없다.
2. **서비스 낱말을 고른다.** 계열 C 인데 가격 계량으로 `월 구독료`·`이용 요금` 을 쓴다 —
   `판매가` 가 판 ㉚ 에서 **바로 이 실패 때문에** 신설돼 있는데도 안 쓴다.
3. **발행되지 않는 값을 묻는다.** 「프레시지 월 구독료」처럼 회사를 지목한 표적은 빈손이다.
4. **`must_contain` 을 아무 데나 있는 낱말로 쓴다**(「문제」·「성장」). `any()` 라서
   여러 개를 적으면 느슨해지는데 그 성질이 프롬프트에 안 적혀 있다.
5. **통과 불가능한 벽을 만든다.** `metric=거래액` 슬롯에 `must_contain=["성장"]`
   (§15.6 에서 심사 층으로 우회했지만 **설계 층의 병은 남아 있다**).

**착수 전에 읽을 것:** 이 문서 §15.8(회차 표) · `data/slots_hmr-pin09.json` 의 `_수정이력` ·
`harness/vocab.json` 의 `metric.catalog.판매가._왜_신설했나`.

**측정 수단은 이미 있다:** `tools/harness_variance.py`(같은 컨셉 N회 설계 → 어느 칸이 자주
비는지) · `tools/slot_dryrun.py`(LLM 0회 라우팅 확인) · `tools/scorecard.py` + 인용 전수 검사.
**성적표만 보면 안 된다** — pin-06 이 6/6 이면서 오답이었다.

### 15.10 미커밋 산물 — 커밋에 넣지 말 것

`data/slots_paid31-beauty.json` · `formulas_paid31-beauty.json` ·
`data/slots_hmr-01.json` · `formulas_hmr-01.json` ·
`data/slots_paid31a-hmr.json` · `slots_paid31b-hmr.json` · `formulas_paid31[ab]-hmr.json`
(§14.5 와 같은 이유 — 유료 실측 산물이다).

> **예외: `data/slots_hmr-pin09.json` 은 커밋 후보다.** 나머지와 성격이 다르다 —
> 유료 실측의 부산물이 아니라 **9회차 측정의 결론이고 다음 판의 목표 설계**다.
> `_수정이력` 이 그 근거를 전부 들고 있다. 커밋할 때 §15.8.3 의 「손 조립」 경계를 같이 남길 것.

---

## 16. 판 ㉜ — 하네스가 스스로 설계하게 만들기 (2026-08-11, 미커밋)

§15.9 가 남긴 일을 했다. **무료 구간을 먼저 세우고 유료를 1회 태웠다.**

### 16.1 만든 것

| 무엇 | 자리 | 성격 |
|---|---|---|
| `tools/design_score.py` | 신규 | 설계를 **기준 스냅샷과 대조**. LLM 0회 |
| `tests/test_design_score.py` | 신규 | 재는 자를 pin-01~09 로 검증. 12 통과 |
| `metric._가격_계량` 에 `판매가` | `harness/vocab.json` | **누락 버그였다** (아래 16.2) |
| `요구.서식지_분산` · `metric._계열C_기피_가격계량` | `harness/vocab.json` | 새 규칙 값 |
| 프롬프트 규칙 7 개정 · 7-0 신설 · 분량 개정 | `harness/slot_harness.py` | 9회차의 배움 |
| `check_must_contain` · `check_habitat_spread` | `harness/gate.py` | **권고**(막지 않는다) |
| 권고 되먹임 · best-of-N 둘째 열쇠 | `harness/slot_harness.py` | 배선 |

검사: `test_harness.py` 107 → **113 통과 / 0 실패**, `test_design_score.py` **12 / 0**.

> ⚠ **`python -m pytest` 는 이 시험들을 안 돌린다.** `ai/pytest.ini` 가
> `norecursedirs = app/research/research2` 로 **명시적으로 뺐다**. research2 시험은
> **파일별로** 돌린다: `python tests/test_harness.py`. §15 이전 문서의 pytest 안내는 틀렸다.

### 16.2 착수 탐색에서 잡힌 데이터 버그 — `판매가` 가 표에 없었다

`판매가` 는 판 ㉚ 에서 `metric.catalog` 에만 신설되고 **`metric._가격_계량` 에는 안 들어갔다.**
그 표를 읽는 곳이 둘이다 — `slot_harness.wire()`(칸·claim_type 을 (수익원, PRICE)로 **강제**) ·
`gate.check_price_cell`(**검사**). 즉 **계열 C 의 가격 계량만 강제도 검사도 안 받고 있었다.**
§15.9 이유 ②의 절반은 프롬프트가 아니라 여기였다. 고친 뒤 pin-09 의 `판매가` 슬롯 3개
(S15·S16·S21)가 처음으로 실제 검사를 받고 통과했다.

### 16.3 재는 자가 성적표와 **갈린다** — 그것이 존재 이유다

pin-01~09 는 **공짜 라벨 데이터**다. 전부 대조한 결과:

| 판본 | 성적표(§15.8) | 설계 점수 | 서식지_분산 |
|---|---|---|---|
| pin-01(=`paid31b`) | 4/6 | **0.278** | 0.333 |
| pin-02~04 | 4/6 | 0.708 | 0.333 |
| pin-05 | 5/6 | 0.817 | 0.667 |
| **pin-06** | **6/6** | **0.964** | 1.0 |
| **pin-07** | **4/6** | **1.000** | 1.0 |
| pin-09 | 6/6 | 1.000 | 1.0 |

**pin-07(4/6) 이 pin-06(6/6) 보다 높다.** 성적표와 반대다 — 그리고 그것이 옳다.
pin-06 의 6/6 은 「70대 이상 1인 가구 우울증상유병률」이 만든 **거짓 6/6** 이었고(§15.8.1),
재는 자는 그 S13(`must_contain=["문제"]` / subject 「1인 가구」)을 정확히 짚었다.
`tests/test_design_score.py` 가 이 역전을 **시험으로 못박아** 두었다.

곁가지: pin-05 의 「분산인 줄 알았는데 중복이었다」함정도 도구가 독립적으로 재발견했다 —
슬롯은 14→17 로 늘었는데 PAIN 의 **서로 다른 subject 는 1** 그대로였다.

### 16.4 권고로 건 이유 — 옛 골든이 19건 걸린다

승인된 골든 `slots_beauty-noshow.json` 이 `check_must_contain` 에 **19건** 걸린다.
전부 낱말 2개짜리 일반어다(「경쟁상황」·「대체재」·「차별」·「가치」·「수익」).
**막는 검사로 걸었다면 그 골든이 소급해서 무효가 됐을 것이다.** 경고로 둔 결정이 옳았다.

배선의 실제 내용:
- 권고 검사는 `passed=True` 를 낸다 → **권고만으로는 재시도하지 않는다.**
  다른 이유로 재시도가 일어날 때 **덤으로** 되먹임에 실린다(호출이 안 는다).
- `요약` 은 **통과/실패 이분법 그대로** 두고 권고는 `권고_요약`·`권고_수` 로 뺐다 —
  `tools/harness_variance.py` 가 「"통과" 가 아닌 것 = 미통과」로 세기 때문이다.
  섞었으면 그 도구의 통계에 권고가 조용히 실패로 합류했다.
- best-of-N 은 `(위반, 권고)` **튜플**로 고른다. 임의 가중치를 지어내지 않으려고.

### 16.5 유료 실측 — 설계 1회 + 수집 1회 (`p32-auto01`)

설계는 **시도 2/3 에서 통과**(권고 5→4건, 되먹임이 실제로 붙었다). 슬롯 15개.

무료 판정 → **가격은 배웠고 수요는 못 배웠다**:
- PRICE 3갈래(편의점 도시락·배달 음식·외식) — **pin-09 가 6/6 을 낸 바로 그 표적들**이고,
  `월 구독료` 0건 · 회사 지목 0건. §15.9 이유 ②③은 닫혔다.
- PAIN 1슬롯(1 subject) — 분산 못 함. 설계 점수 **0.810**(pin-01 0.278 → 큰 개선, pin-09 미달).

**수집 결과: 성적표 2/6** (가격·수요만 채워짐).

### 16.6 ⚠ 이 판의 가장 값비싼 발견 — **아무도 `value_range` 의 자릿수를 안 본다**

미확보 4과목의 원인이 하나였다. **검색은 옳은 문서와 옳은 값을 물어왔는데
`value_range` 가 4자릿수 작아서 전부 격리됐다.**

```
S1 거래액   3.80e13 ∉ [1e8, 2e9]   자릿수차 4.3 > 3.0   KOSIS DT_1KE10041
S5 거래액   3.48e13 ∉ [1e8, 2e9]   자릿수차 4.2         (성장률 2년치)
S6 거래액   3.02e13 ∉ [8e7, 1.8e9] 자릿수차 4.2
S7 매출액   2.73e13 ∉ [1e8, 1.5e9] 자릿수차 4.3         DART CJ제일제당
S8 매출액   3.67e12 ∉ [8e7, 1.3e9] 자릿수차 3.5
S9 매출액   2.53e12 ∉ [6e7, 1.2e9] 자릿수차 3.3
```

즉 **①시장크기 ②성장률 ③경쟁사 ⑥계산이 한 원인으로 같이 죽었다.** 자료 부재가 아니다.

**어느 검사도 이것을 못 본다** — 전부 통과시켰다:
- `gate.check_value_range` : 상한 > 하한 만 본다
- `slot_dryrun.check_guards` : 폭이 10배 미만인지만 본다 ([1e8, 2e9] 는 20배라 통과)
- `design_score` : 자릿수를 **안 본다**(내가 안 넣었다)

**왜 pin 회차가 이걸 못 가르쳐 줬나.** 9판 전부 TAM·GROWTH·COMP 의 `value_range` 를
사람이 적었고 자릿수가 맞았다 — **라벨 데이터에 이 축의 신호가 아예 없었다.**
재는 자를 라벨에서만 뽑으면 라벨이 안 흔든 축은 구조적으로 못 만든다.

> **⚠ 그리고 pin-09 자신도 여기서 아슬아슬하다.** S1 의 `value_range` 상한이 `5e10` 인데
> 실제 값은 `3.8e13` 이다 — **760배 작다.** 자릿수차 **2.88 < 3.0** 이라 **간신히** 통과한
> 것이지 맞아서 통과한 게 아니다. **6/6 기준 설계의 ①시장크기는 0.12 자릿수 차이로 서 있다.**

### 16.7 인용 전수 검사 — **채워짐 2개도 못 믿는다**

§15.8.1 의 교훈대로 성적표를 안 믿고 인용을 봤다. 채택 3행 전부 약하다:

| 슬롯 | 값 | 인용 | 판정 |
|---|---|---|---|
| S12 수요 | 30.2% | 「식사 해결의 어려움(30.2%)」 금천구 | **관련 있다.** 다만 등급 추정 · 출처가 구청 게시판 |
| S13 가격 | 1,000원 | 「도시락 1,000원」 농식품부 | **오답.** 원문은 「쌀의 날」 기념 **편의점 아침 도시락 할인 행사**다 — 시장가가 아니라 행사 할인가 |
| S14 가격 | (없음) | 「2,400원∼3,400원」 | **값이 안 잡혔다**(범위 인용). 인용만 남았다 |

**즉 성적표 2/6 중 ④가격은 인용 검사에서 무너진다.** 사용자가 실제로 쓸 값은 수요 1건뿐이다.

⚠ S13 은 `must_contain=["도시락"]` 이고 「도시락」은 subject 「편의점 도시락」 안에 있다 —
**새 규율을 지키고도 오답이 통과했다.** 이 규율은 **필요조건이지 충분조건이 아니다.**
`tests/test_design_score.py` 의 ⑦번 검사가 그 한계를 미리 못박아 뒀다(pin-07 이 네 축
만점인데 성적표는 4/6 이었다).

### 16.8 다음 판이 할 것 — 순서대로

1. **`value_range` 자릿수 검사** (16.6). 가장 크고, 무료로 만들 수 있다.
   계량별 기대 자릿수를 `vocab.metric.catalog` 에 값으로 두고(거래액·매출액은 조 단위)
   설계 시점에 대조한다. **pin-09 도 이 검사에 걸려야 한다** — 걸리지 않으면 검사가 무르다.
   ⚠ 「크기 필터로 종류 오류를 거르지 마라」와 충돌하지 않는다. 이건 **필터가 아니라
   필터의 자릿수를 보는 것**이다.
2. **PAIN 분산이 왜 프롬프트로 안 됐나.** 규칙 7-0 과 분량(F_PAIN 3~5개)을 둘 다 적었는데
   모델이 1개를 냈다. 권고는 떴지만 `passed=True` 라 재시도가 안 걸렸다.
   먼저 `tools/harness_variance.py` 로 **반복 3회**를 재라 — 요동인지 체계적 무시인지.
3. **`must_contain` 을 metric 이름으로 쓰는 새 버릇** — S1·S5·S6 이 subject 「냉동 간편식」에
   `must_contain=["거래액"]` 을 달았다. pin-09 의 답은 「TAM 은 **비워 둔다**」였다.
   규칙 7 에 「가를 것이 없으면 빈 배열」이라 적었는데도 채웠다.

### 16.9 미커밋 산물 — 커밋에 넣지 말 것

`data/slots_p32-auto01.json` · `data/formulas_p32-auto01.json` · `runs-generated/p32-auto01/`
(§14.5·§15.10 과 같은 이유 — 유료 실측 산물이다).
`runs-generated/` 는 `.gitignore` 에 있어 자동으로 빠진다.

**커밋 대상은 코드·규칙·시험뿐이다**: `tools/design_score.py` · `tests/test_design_score.py` ·
`harness/vocab.json` · `harness/slot_harness.py` · `harness/gate.py` · `tests/test_harness.py`.
`data/slots_hmr-pin09.json` 은 §15.10 의 예외대로 커밋 후보이며, **16.6 의 단서**
(①시장크기가 0.12 자릿수로 서 있다)를 경계로 같이 남길 것.

---

## 17. 판 ㉜ 수리 — 문제 6개 (2026-08-12, 미커밋)

§16 이 남긴 문제 여섯을 전부 손봤다. **가장 큰 증거는 유료 0원으로 나왔다.**

### 17.1 한 줄 요약 — 찾아놓고 버린 것을 되찾았다

같은 원장(`p32-auto01`)을 **LLM 0회**로 재채점한 결과:

| | 판 ㉜ | 수리 후 |
|---|---|---|
| 성적표 | **2/6** | **6/6** |
| 확인됨 | 0 | **6** |
| 격리 | 13 | **3** |
| 충족 슬롯 | 3/15 | **9/15** |
| blocker 위반 | 1 (R9) | **0** |

**새로 산 자료는 한 건도 없다.** 수집은 원래 맞는 값을 물어왔고 우리가 버렸을 뿐이다.

### 17.2 문제 1 — `value_range` 자릿수 (해결)

`rules/guards.v1.json` 에 **계량 전형 밴드** 표를 두고 두 층이 같이 읽는다.

- **수집 층**(`blocks/a_desk.off_slot_reason`): 슬롯 밴드 밖이어도 **값이 계량 전형 밴드
  안이면 격리하지 않는다.** `기대_밖.구조됨` 으로 남긴다. 되살아난 값 6개:
  KOSIS 거래액 38.0조·34.8조·30.2조 · DART 매출 27.3조·3.67조·2.53조.
- **설계 층**(`harness/gate.check_range_band`): 슬롯 밴드가 전형 밴드와 안 겹치면 **권고**.

> **왜 `vocab.json` 이 아니라 `rules/` 인가.** `blocks/` 는 유리벽 안이라 `harness/vocab.json`
> 을 안 읽는다. 반대로 하네스는 `rules/` 를 직접 읽는다. **양쪽이 볼 수 있는 자리는 여기뿐**
> 이고, 표가 갈리면 「설계는 통과인데 수집이 버린다」가 생긴다. (계획서는 vocab 이라고 적었고
> 그것이 틀렸다.)

⚠ **비대칭이 의도다** — 밴드 안이면 **살리고**, 밴드 밖이어도 **차단하지 않고 표시만** 한다.
차단까지 하면 이 표가 새 검열자가 되고, 그것이 이 파일 첫 줄이 금하는 것이다.

⚠ **알고 남긴 구멍**: 슬롯 밴드 자체가 틀리면 자릿수 그물이 헛돈다 — 「38조를 100만 배
축소한 3.8e7」은 틀린 밴드 `[1e8, 2e9]` 옆에 붙어 **자릿수 차이 0.42** 로 그냥 지난다.
`_currency._why` 의 카페24 사고와 같은 모양이다. **막지 않고 `전형_밴드_밖` 으로 표시만** 한다.

### 17.3 문제 2 — 발췌의 수·단위 (해결)

두 결함이 **한 인용에서** 같이 터졌다: 「중개수수료 **7.8%**에 배달비 **2,400~3,400원**」.

- **범위 쪼개기** — `a_desk.split_range()` 신설. `parse_number` 의 **시그니처는 안 건드렸다**
  (`cases_numbers.json` 50건이 그 계약 위에 있다). 하한·상한을 **각각 사실로** 낸다 —
  대표값(중간값)을 만들면 원문에 없는 수를 지어내는 것이다.
  ⚠ 원래 **갈래마다 다르게** 틀렸다: 범위가 `number_raw` 면 조용히 **하한**(플래그도 없다),
  `unit_raw` 면 **null**. 조용한 쪽이 더 위험했다.
  ⚠ 한국어 배수(만·억)는 **수의 일부**다 — 「10만~30만」에서 「만」을 단위로 떼면 양쪽 다 죽는다.
- **단위 재선택** — `a_desk.reread_for_unit()`. 모델이 고른 수의 단위가 슬롯과 안 맞으면
  **인용 한 문장 안에서** 맞는 수를 다시 읽고, `Fact.수_재선택` 에 **덮었다는 사실을 남긴다.**
  프롬프트는 그대로 뒀다(사용자 결정) — 「슬롯과 맞는지 판단하지 마라」는 모델이 조용히
  버리는 것을 막는 규칙이라 유지할 값이 있다.
  ⚠ **인용 밖은 안 본다.** 문서를 뒤지면 `quote_verified` 가 뜻을 잃는다.

결과: S14 가 배달비 밴드를 통째로 냈다 — 1,900 / 2,100 / 2,130 / 2,400 / 2,900 / 3,100 /
3,130 / 3,400원. **pin-09 가 6/6 에 채택한 그 값들**이다(§15.8.2).

### 17.4 곁가지 — pin-09 도 위반하던 R11 (해결)

범위 쪼개기가 밴드 값을 늘리자 **R11(같은 지표 두 값, blocker)** 이 떴다. 파 보니
**`slots_hmr-pin09.json` 의 실행도 원래 R11 을 위반하고 있었다**
(`배달 음식|이용 요금|대한민국|2025: [1900.0, 2400.0]`). **아무도 못 본 이유는 성적표가
체인 위반을 안 보여주기 때문**이다 — §15.8.1 「성적표만 보면 안 된다」의 다른 얼굴이다.

**가격은 모순이 아니라 밴드다.** R7 의 이름이 「우리 가격이 **대체재 밴드**와 비교 가능」이다.
`consistency.v1.json` 에 `밴드_claim_type: [PRICE, ALT]` 를 두고 그 축만 뺐다 —
TAM·SAM 은 그대로다(시장 규모가 38조이면서 11조일 수는 없다). 면제분은 사유에
**「밴드(모순 아님)」로 적어 남긴다** — 판정에서 빼는 것이지 기록에서 지우는 것이 아니다.

### 17.5 문제 3 — PAIN 분산은 **요동이었다** (측정 후 미수정)

`tools/harness_variance.py` 3회(설계만, 6시도). F_PAIN 관측: **1, 2, 2, 3, 3, 3, 3**(7판).
규칙 7-0 은 대체로 먹힌다 — 판 ㉜ 이 1을 쓴 것은 **꼬리를 뽑은 것**이다.

왜 하필 1이 실렸나: **시도1 이 `F_PAIN=3변수/3subj` 였는데 게이트 위반 2건으로 버려졌고,
시도2(`F_PAIN=1`)가 통과하자 루프가 멈췄다.** best-of-N 은 `(위반 0, 권고 4) < (위반 2, 권고 5)`
로 **옳게** 골랐다 — 게이트 위반은 스냅샷 자체를 못 만들기 때문이다. 계획대로 **구조 강제는
하지 않았다**(체계적이 아니므로).

> **「경고만」 결정의 실제 대가가 여기 값으로 보인다** — 통과한 첫 판본이 권고에서 나빠도
> 더 안 돌린다. 권고는 **다른 이유로 재시도가 일어날 때만** 되먹임에 실린다.

### 17.6 문제 4·5 — 재는 자의 사각과 기준의 취약함 (해결)

`tools/design_score.py` 에 **다섯째 축 `value_range_자릿수`** 를 더했다. **기준(`--ref`)과
무관한 절대 축**이라 기준의 흠을 안 따라간다 — **`pin-09` 가 자기 자신 대조에서도 감점된다**
(S1 `[1e9, 5e10]` vs 거래액 전형 `1e11~1e14`). 그것이 문제 5의 답이다: 9회차 측정 기록인
`pin-09` 를 고쳐 역사를 다시 쓰지 않고, 절대 축이 그 흠을 잡게 한다.

**그리고 방법론을 고쳤다.** 이 사각이 생긴 이유는 축을 **라벨(pin-01~09)에서만** 뽑았기
때문이다 — 아홉 판 전부 그 칸의 밴드를 사람이 옳게 적어 **신호가 0이었다.**
그래서 `--runs` 를 더해 **지난 실행의 `off_slot_reason` 을 세어 리포트에 싣는다.**
판 ㉜ 원장을 세어 보면 이렇게 나온다:

```
7  단위 불일치      예: 단위 불일치: 슬롯 '%' vs 사실 'None'
6  값범위 밖        예: 값범위 밖(자릿수 차이 4.3 > 3.0): 3.80411e+13 ∉ [1e+08, 2e+09]
```

**이 세 줄이 이번 판에서 고친 두 문제 그 자체다.** 첫날 세었으면 바로 잡았다.
→ **다음 판의 규율: 축을 정하기 전에 원장의 `off_slot_reason` 부터 센다.**

### 17.7 문제 6 — 재수집 (AI 층까지, 사용자 결정)

`taskInput.recollect` 를 받아 `ENGINE.CollectOptions` 로 넘긴다
(`from_stage`·`source_run`·`collect_slots`·`slots_from`). **엔진에는 이미 다 있었고**
(`run.py --from a4 --collect-slots`) 오케스트레이터가 안 넘겼을 뿐이다.

- **지시가 없으면 동작이 한 줄도 안 바뀐다.** 모양이 아니면 조용히 기본 경로다.
- 내부 계약 v1·`MarketResearchInputFactory`·`openapi.yaml`·Java **무변경** →
  **화면에서는 못 누른다. CLI·시험에서만 닿는다.**
- 무엇을 다시 샀는지 `RECOLLECTED` degradation 으로 남긴다.

### 17.8 곁가지로 고친 것 셋

1. **`tools/harness_variance.py` 가 옛 원장 자리를 하드코딩**하고 있었다 —
   `runs/harness/` 를 읽는데 하네스는 `runs-generated/harness/` 에 쓴다. **`gate.json` 을
   한 번도 못 찾았을 것**이고, 유료 설계를 3회 돌리고 표는 비었을 것이다. `runpath` 로 고쳤다.
   (「베낀 조회는 갈라진다」의 또 한 건.)
2. **fail-open 이 예외로 죽었다.** `check_hypothesis_leak` 은 위반을 **문자열**로 내는데
   (슬롯이 아니라 값이 위반이라 옳은 모양이다) 집계가 `.get("slot_id")` 를 불렀다 —
   **하필 「어떤 입력에도 출력은 나온다」를 지키라고 있는 자리**다. 분산 측정에서 실제로 터졌다.
3. **`tests/test_step6.py` 의 7개 실패는 낡은 시험이었다.** `adapters/web.py` 의 per_doc
   재작성은 완료돼 있었고 시험이 **일부러 없앤 배치 동작**을 검사하고 있었다. per_doc 기준으로
   다시 썼다(47/7 → 58/0). `_FakeMeter` 도 고쳤다 — 프롬프트를 하나만 들고 있어서
   `ThreadPoolExecutor` 아래서 경합했다.

### 17.9 ⚠ 아직 안 된 것 — 설계는 여전히 스스로 못 고친다

수리 후 설계를 새로 뽑았다(`p33-auto`, 18슬롯, 시도 2/3 통과, **권고 8건**):

| 축 | 점수 | |
|---|---|---|
| 서식지_분산 | **1.0** | 좋아졌다(0.667 → 1.0) |
| 발행_가능성 · 계열_가격어휘 | 1.0 | 유지 |
| **value_range_자릿수** | **0.69** | 거래액 밴드를 **여전히** 틀리게 적는다 |
| **must_contain_규율** | **0.33** | TAM 4칸에 「냉동 간편식」(**공백 포함**) — 백로그 35 |

**권고가 8건 떴는데 설계는 안 바뀌었다.** 권고는 `passed=True` 라 재시도를 안 걸고,
시도2 가 통과하면 거기서 멈춘다. 즉 **「경고만」은 기록을 남기지 설계를 고치지 않는다.**

그래서 **유료 수집(ⓒ)을 태우지 않았다** — §16 이 세운 「(b)·(c) 가 나쁘면 (d) 를 태우지
않는다」를 지킨다. 지금 6/6 을 만든 것은 **설계가 좋아져서가 아니라 수집 층이 구조하기
때문**이다. 그 구분을 흐리지 말 것.

**다음 판이 정할 것:** 이 두 축(전형 밴드·must_contain 공백)을 **막는 검사**로 올릴지.
올리면 설계가 강제로 고쳐지지만 판 ⑧ 처럼 만족 불가능해질 위험이 있다 —
`harness_variance` 로 **먼저 재고** 정할 일이다.

### 17.10 검사 상태

research2 는 **파일별로** 돌린다(`ai/pytest.ini` 가 `norecursedirs` 로 뺐다):
`test_harness 120` · `test_step8 99` · `test_step2 69` · `test_step6 58` ·
`test_design_score 19` · `test_failopen 19` — **전부 0 실패.**
`cd ai && python -m pytest tests -q` → **594 통과 / 4 실패**(4건은 `concept_portfolio_v2`
모듈이고 이 작업과 무관하다 — 착수 전부터 빨갰다).

물려받은 실패 둘은 그대로다: `test_step9`(디스크에 없는 씨앗 원장 `runs/t9-merge`) ·
`concept_portfolio_v2` 4건.

### 17.11 미커밋 산물 — 커밋에 넣지 말 것

`data/slots_p32-auto01.json` · `slots_p33-auto.json` · `formulas_p3*.json` ·
`runs-generated/`(gitignore 됨). **커밋 대상은 코드·규칙·시험뿐이다.**
`git add -A` 를 쓰지 않는다.

---

## 18. 제품 계열 고정을 A → C 로 바꿨다 (2026-08-12, 미커밋)

`backend/.../journey/ResearchConceptFactory.java:55` 의 `SERIES` 상수. **사용자 결정.**

### 18.1 왜 바꿨나 — 「식품이라서」가 아니라 **「채울 수 있어서」**다

계열이 정하는 것은 F_TAM·F_SAM 의 **템플릿**이고, 템플릿이 정하는 것은 **채워야 할 자리 수**다.
코드로 확인한 값이다(`targets()` 실행):

| 계열 | F_TAM 템플릿 | 채워야 할 자리 |
|---|---|---|
| A 사업체 | T2 | 사업체수 · 세그먼트비중 · 침투율 · 단가 · 연환산 — **5칸** |
| **B 개인** | **T2** | **위와 같다 — 5칸** |
| **C 거래** | **T7** | 시장거래액 · 추정점유율 — **2칸** |
| E 개인·거래 | T2 | (분기 미선언이면 기본값) — 5칸 |

**「고객이 개인」만 보면 B 가 곧다. 그런데 B 도 T2 라 A 와 같은 자리에서 죽는다.**
`vocab.식_목록.계열_템플릿.E._왜` 가 그 죽음을 값으로 들고 있다 —
*「override 가 없어 계열 A 의 T2 를 받았고 **자리 4개(세그먼트비중·침투율·단가·연환산)를
못 채우고 죽었다**(판 ㉔ 확정 · 백로그 71)」*. 침투율(도입률)은 web 계량이라 만성적으로 빈다.

반대로 T7 의 두 칸은 판 ㉜ 에서 **실제로 찼다**:
- `시장거래액` → KOSIS `DT_1KE10041` 냉동 간편식 **38.0조 · 등급 확정**
- `추정점유율` → 아직 없는 브랜드라 관측 불가 → `observable=false` 가정
  (어휘가 이미 그렇게 정해 두었다 — 「아직 없는 브랜드의 점유율은 **관측될 수 없다**」)

### 18.2 E 는 왜 안 되나

E(허용: 개인·거래)가 가장 넓어 보이지만, **분기 근거를 컨셉의 `_계열.왜` 에서 읽는다.**
제품은 그 문장이 **상수**라 「구조는 C」를 박아야 하고, 그러면 C 를 고정하는 것과 같다.
안 박으면 기본 T2 로 떨어지는데, T2 는 사업체를 세므로 **E 의 허용(개인·거래)과 어긋나
게이트에서 죽는다.** 넓은 척하고 실제로는 못 쓰는 값이다.

### 18.3 ⚠ 대가 — 방향이 반대인 같은 위험

옛 주석이 안고 있던 위험: *「사업안의 고객이 개인이면 고객 단위 잣대가 어긋난다」*
새 위험: **신사업이 「개인 대상 **서비스**」(구독 앱 등)면 그 시장의 거래액 통계가 없어
TAM 이 미확보로 남는다.** 위험이 사라진 게 아니라 **자리를 옮겼다.**

우리 제품 범위(대기업 신사업)에서 어느 쪽이 잦은지가 판단 근거이고,
지금 근거는 「제품을 파는 신사업이 더 잦다 + T7 이 실증됐다」다.

### 18.4 §1-6 을 폐기 표시했다 — **되돌리지 말라는 뜻이다**

§1-6 이 계열 A 의 근거로 성적표를 들고 있고 거기서 **C 는 3·0·3 으로 꼴찌**다.
그 측정(`pet-treat-18`)은 **계열 C 가 자기 계량 셋을 표현조차 못 하던 때** 것이다 —
「거래액」·「추정점유율」·「판매가」가 전부 통제 어휘에 없었고, strict enum 이라
**표현 자체가 불가능**했다(`vocab.metric.catalog.판매가._계보`).
셋 다 그 뒤 신설됐고 판 ㉜ 에서 C 가 6/6 을 냈다. **낡은 측정으로 되돌리지 말 것.**

> 이 저장소가 반복해 밟는 함정이다 — **문서의 성적표가 코드보다 여러 판 뒤에 있다.**
> §16.6(문서가 「collect 는 아직 안 돌린다」라고 두 판 늦게 적혀 있던 것)과 같은 계보다.

### 18.5 바뀐 것 · 검사

- `ResearchConceptFactory.java` — `SERIES` `"A"` → `"C"`, `SERIES_WHY`·`SERIES_NOTE` 재작성
  (대가와 GMV≠매출 경계를 문구에 실었다)
- `ResearchConceptFactoryTests.java` — `seriesIsPinnedToA` → `seriesIsPinnedToC`.
  **고정의 대가가 `_고정_사유` 에 적혀 있는지까지 검사한다** — 근거 없는 상수를 막는다.
- `./gradlew.bat test --tests "...ResearchConceptFactoryTests" --rerun-tasks`
  → **12 tests / 0 failures** (캐시가 아니라 실제로 돌렸다)
- 백엔드에 계열 A 를 전제하던 다른 자리는 **없다**(grep 0건).

### 18.6 아직 안 된 것

계열만 고쳤다. **제품 경로 전 구간(사용자 입력 → 사업안 생성 → 시장분석)은 아직 안 돌렸다.**
남은 간극은 §17.9 와 함께 본다:
- `_경쟁_씨앗` 은 사용자가 화면에서 넣어야 한다. 없으면 `씨앗_없으면_제외` 가 매출액·영업이익을
  선택지에서 빼서 **판 ㉜ 에서 되살린 DART 3건이 안 나온다.**
- 컨셉 9칸 본문이 사업안 생성물로 바뀌면 `추출_힌트`(컨셉 유래 필수)가 달라진다.
- **캔버스(BM 9칸)는 한 번도 안 세워 봤다** — 6/6 원장 위에서 서는지 미확인.

---

## §19 판 ㉝ — 설계 자가수리, 그리고 제품 경로 전 구간 (2026-08-12, 미커밋)

목표는 하나다. **예시 제품을 HMR 하나로 확정하고 `/idea` 자유 입력에서부터 화면으로
전 구간을 왕복시켜, 자동 설계가 만든 슬롯으로 성적표 6/6 을 낸다.**

사용자 결정: ① 자동 설계를 고쳐 **진짜** 6/6 ② 예시는 HMR 견본 그대로 ③ 화면 왕복 전체.

### 19.1 권고 8건과 두 축의 감점은 **같은 사건**이었다

§17.9 가 「권고 8건이 떴는데 설계는 안 바뀌었다」로 남긴 자리를 다시 세어 보니,
그 8건이 `design_score` 두 축의 감점과 **정확히 같은 수**였다:

| 축 | 실측 | 위반 | 사유 |
|---|---|---|---|
| `must_contain_규율` | 2/6 = **0.33** | S1~S4 | `["냉동 간편식"]` — 낱말에 **공백 하나** |
| `value_range_자릿수` | 9/13 = **0.69** | S1·S3·S5·S6 | 전부 **거래액**. 전형 밴드 `[1e11, 1e14]` 와 안 겹친다 |

게이트의 권고 술어(`gate.check_must_contain`·`check_range_band`)와 `design_score` 의 두 축
판정은 **문자 그대로 같은 코드**다. 그래서 이 판의 일은 새 검사를 만드는 것이 아니라,
**이미 정확히 짚고 있는 8건을 소비하는 층**을 만드는 것이었다.

### 19.2 왜 재시도가 아니라 결정론적 교정인가

밴드 4건이 틀린 것은 **모델에게 전형 크기를 한 번도 안 알려줬기 때문**이다. 프롬프트 규칙 6은
「넓게 잡아라」·「상한>하한」만 말했다. 모르는 것을 못 맞춘 것이고, 이 저장소는 그 병을 이미
두 번 진단했다(백로그 59 — 판 ⑩ 의 허용 계량 목록). **정본 처방은 재시도가 아니라 지시 정합이다.**

그리고 **답이 표에 값으로 있다.** `rules/guards.v1.json` 의 `계량_전형_밴드` 는 계량 이름 →
`[lo, hi]` 인 평평한 조회표이고, 같은 파일이 이미 이렇게 정해 두었다 — 「수집 층은 슬롯 밴드
밖이어도 값이 **전형 밴드 안이면 격리하지 않는다**」. 즉 **설계 밴드를 전형 밴드로 갈아끼우는
것은 수집 층이 실제로 하는 일을 설계 시점에 앞당겨 적는 것**일 뿐, 관측 범위를 넓히지도
좁히지도 않는다.

`slot_harness.py:814` 의 `break` 조건을 좁히는 안(권고→재시도 승격)은 **채택하지 않았다.**
예산 회계상 공짜이고(`pipeline.py` 가 실제 시도 수와 무관하게 3을 charge) fail-open 위험도
없다고 확인했지만, 실비 3배를 쓰고 수렴 보장이 없다 — 그리고 위 이유로 재시도의 대상이 아니다.

> **버린 대안:** 10의 거듭제곱 배율로 밴드를 옮기는 안. TAM>SAM 폭은 보존되지만
> **부분 겹침만 만들어 축은 통과시키면서 참값 38.0조를 여전히 격리한다** — 축을
> 만족시키려고 목적을 배신하는 모양이다.

### 19.3 바뀐 것

- **`harness/vocab.json`** — `재시도` 옆에 형제로 **`설계_교정`** 신설.
  전역 끄개 + `value_range_밴드` + `must_contain_낱말`. 규칙 값이 코드가 아니라 파일에 있다.
- **`harness/slot_harness.py`**
  - `repair_design(slots, vocab, guards)` 신설 (+ `_교정_value_range`·`_교정_must_contain`).
    **순수 함수 · LLM 0회 · 입력이 「슬롯 dict 목록」**이라 저장된 스냅샷을 그대로 먹일 수 있다
    — 그것이 유료 실행 0회로 증명이 되는 이유다.
    (이름이 `repair` 가 아닌 것은 이 파일에서 `raw["repair"]` 가 **JSON 파싱 복구**를 뜻해서다.)
  - `judge()` 가 `wire()` 뒤·`run_gate()` **앞**에서 부른다. 게이트가 자기가 판정할 것을 고치면
    판정이 사라지므로, 교정은 설계 층에 두고 게이트는 교정된 결과를 그대로 잰다.
  - `build_prompt` 가 계량마다 **`전형 크기 [lo, hi]`** 를 보여주고, 규칙 6에 한 줄을 더했다.
  - 원안은 `_value_range_원안`·`_must_contain_원안` 에 남는다 — **밑줄 접두라 `run.py:243`
    이 걸러내 엔진 `Slot` 에는 안 들어간다.**
- **`tests/test_harness.py`** — `[28] 설계 교정` 신설.

### 19.4 실측

```
python tests/test_harness.py       136 통과 / 0 실패   (기존 [26][27] 무회귀 포함)
python tests/test_design_score.py   19 통과 / 0 실패   (손 안 댔다는 증거)
python tests/test_failopen.py       19 통과 / 0 실패
python tests/test_step12.py         45 통과 / 0 실패
```

`data/slots_p33-auto` 에 교정을 먹인 뒤 재채점(LLM 0회):

```
== p33auto-repaired ==  설계 18슬롯 · 기준 21슬롯 · 구조 일치 0.8669
  서식지_분산 1.0 · must_contain_규율 1.0 · 발행_가능성 1.0
  계열_가격어휘 1.0 · value_range_자릿수 1.0        잰 축 평균 1.0
```

교정 **8칸**(value_range 4 · must_contain 4) — 권고 8건과 같은 수다.
`must_contain` 교정 결과가 `["간편식"]` 으로, **손 조립 6/6 판본(pin-09)의 S15·S16 과 같은
값으로 수렴했다** — 코드가 답을 지어낸 것이 아니라는 증거다.

### 19.5 ⚠ 이 판이 약속하지 않는 것

- **축이 1.0 인 것은 모델이 잘 적어서가 아니라 코드가 고쳤기 때문이다.** 교정이 게이트 권고
  술어와 같은 코드를 지우므로 두 축은 **구조적으로** 1.0 이 된다. 그래서 그 축은 더 이상
  「모델이 잘했는가」를 재지 않는다 — 그것을 재는 값은 **`gate.json` 의 `교정_수`** 이고,
  `무인_기록.결정` 에 「코드가 몇 칸을 고쳐 썼는가」가 값으로 남는다. **보고할 때 섞지 말 것.**
- **설계 점수 만점은 필요조건이지 충분조건이 아니다** — 네 축 만점이어도 오답이 통과한
  실측이 있다(`must_contain=["도시락"]` 이 「쌀의 날 행사 할인가 1,000원」을 통과시켰다).
- 서식지 분산처럼 **코드가 답을 지어낼 수 없는** 권고는 손대지 않았다(지금 1.0 이라 대상이 없다).
- `gate.py`·`design_score.py`·`slot_dryrun.py`·`pipeline.py` 는 한 글자도 안 고쳤다.

### 19.6 제품 경로 — `/idea` 입력문 (정본)

`/idea` 의 선택 10칸은 **값을 넣으면 LOCKED** 가 된다(`IdeaBriefService.java:362`) →
7가정 중 5개가 잠긴다. 그래서 사업안 생성 LLM 이 흔들 수 있는 자리는 본문 서술과 SOM 둘뿐이다.
아래 13칸을 **그대로** 넣는다.

**필수 3**

| 칸 | 값 |
|---|---|
| 아이디어 개요 | 1인 가구를 위한 프리미엄 냉동 간편식 브랜드를 만든다. 1인분 정량으로 설계해 남기지 않고, 조리 10분 이내, 급속냉동으로 보존료를 줄인다. 자사몰 정기구독과 대형 이커머스 입점을 함께 운영하는 대기업 신사업이다. |
| 해결하려는 문제 | 1인 가구는 한 끼를 위해 재료를 사면 남기고 버린다. 기존 냉동 간편식은 2~4인 기준 용량이거나, 1인분이어도 저가 편의점 제품에 몰려 있어 「혼자 먹지만 제대로 먹고 싶다」는 수요가 갈 곳이 없다. 배달은 1인분 최소주문금액과 배달비가 붙어 한 끼 단가가 크게 오른다. |
| 예상 사용자 | 대한민국 수도권에 거주하며 주 3회 이상 집에서 혼자 저녁을 해결하는 25~44세 1인 가구 |

**선택 10 — 넣는 순간 LOCKED 다**

| 칸 | 값 | 왜 이 값인가 |
|---|---|---|
| 대상 지역 | 대한민국 | `region`. 가공식품 소매판매액·1인가구 수가 전국 단위로 발행된다 |
| 알려진 경쟁자 | 비비고, 오뚜기 냉동식품, 풀무원 간편식, 프레시지, 신세계푸드 간편식 | ⚠ **이 칸은 `_경쟁_씨앗` 이 아니다.** 씨앗은 `/market` 의 별도 화면에서 다시 넣어야 한다(§19.7) |
| 수익 모델 | 제품 판매 + 자사몰 정기구독(주 단위 묶음 배송) | `_bm_plan.revenue_model` · `_hypotheses_v2.6.수익_방식` |
| 가격 | 1팩 8,900원 | ⚠ **숫자가 읽히는 표기여야 한다** — `TwinSurveyStimulusDraftService.priceKrw` 가 못 읽으면 `price_hypothesis_krw` 가 `null` 이 된다 |
| 채널 | 자사몰 정기구독, 대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑), 편의점·기업형 슈퍼마켓 냉동 매대 | 주 채널 가정은 맨 앞 |
| 차별점 | 1인분 정량 설계, 편의점 저가와 배달 사이의 빈 가격 구간, 10분 이내 단일 조리, 급속냉동으로 보존료 최소화 | `_bm_plan.differentiation` |
| 예산 제약 | 30억원 | `constraint.budget_krw` — 대기업 신사업 규모 |
| 팀 제약 | 12명 | `constraint.team` |
| 일정 제약 | 18개월 | `constraint.months` |
| 기타 제약 | 그룹의 냉동 생산·물류 인프라와 기존 이커머스 입점 채널을 활용한다 | `_bm_plan.key_resources` |

⚠ **`constraint` 의 실제 출처는 BM 계획 화면의 비용 3칸**이다
(`BmPlanPreparationService.current(projectId).constraints()`) — `/idea` 의 제약 3칸과 별개다.
**둘 다** 채운다.

### 19.7 `/market` 경쟁 씨앗 (별도 화면, 5줄)

없으면 `씨앗_없으면_제외` 가 매출액·영업이익을 선택지에서 빼서 **DART 3건이 안 나오고
③경쟁사 과목이 죽는다.** 운영사는 **법인명**이어야 DART 가 열린다.

| 이름 | 왜 경쟁인가 | 운영사 |
|---|---|---|
| 비비고 | 냉동 간편식 1위 브랜드 — 가격·점유율 관측의 기준선 | CJ제일제당 |
| 오뚜기 냉동식품 | 중가대 대량 유통 — 가격 밴드의 하한 | 오뚜기 |
| 풀무원 간편식 | 프리미엄·건강 축의 직접 경쟁 | 풀무원식품 |
| 프레시지 | 밀키트 대체재 — 1인분 정량 축의 직접 경쟁 | 프레시지 |
| 신세계푸드 간편식 | 유통 계열 PB — 채널 경쟁 | 신세계푸드 |

5곳 전부 `adapters/_cache_corpcode.json` 에서 실재 확인됨
(CJ제일제당 00635134 · 오뚜기 00141529 · 풀무원식품 00684732 · 프레시지 01372700 · 신세계푸드 00274933).

### 19.8 다음 순서 (유료 앞에 무료가 온다)

1. 화면 왕복 앞부분 — `/idea` → `/concepts` 사업안 생성·7가정 확정 → BM 비용 3칸 → 경쟁 씨앗
2. **유료 수집 앞의 마지막 무료 관문** — 고정된 시드로 만들어질 컨셉을 꺼내
   `run_harness`(LLM≤3) → `design_score --ref data/slots_hmr-pin09.json` → `slot_dryrun --no-net`.
   관문: 잰 축 평균 ≥0.9 · 어느 축도 <0.8 · `stat_code_해결` >0 · PAIN·PRICE subject 각 3 이상.
   **못 넘으면 유료를 태우지 않는다.**
3. 시장조사 실행(≈83회) → `scorecard.py` **6/6**
4. **인용 전수 검사** — 종류·자릿수·경계·출처. 문턱은 개수를 세지 종류를 안 본다(pin-06 사례)
5. BM 캔버스 9칸

⚠ **예산이 빠듯하다.** `llmBudget=90` 인데 `_collect` 가 `3+80=83` 을 예약한다. 판 ㉛ 수리로
발췌가 12→53회가 되어 검색 24회와 합치면 실측 77회다. 넘기면 요약이 `BUDGET_EXHAUSTED` 로
조용히 빈다 — 6과목과는 무관하지만 결과를 읽을 때 그 칸을 확인한다.

### 19.9 제품 경로 유료 실측 — 4판 (2026-08-12)

프로젝트 3번 · 컨셉 `0c54ffb5-b7bf-46b0-adc2-be284fed6acb` · 원장 4개를
`runs-generated/hmr-product-r1..r3` 로 보존(r4 는 현재 이름 그대로).

| 판 | 슬롯 | 성적표 | 죽은 과목 | 진단 |
|---|---|---|---|---|
| r1 | 17 | **5/6** | ④가격 | **설계** — PRICE 표적 3개가 전부 회사(`발행_가능성 0.5`) |
| r2 | 13 | **4/6** | ④가격·⑤수요 | **설계** — PAIN 1 · PRICE 1, 서식지 분산 미달 |
| r3 | 18 | **5/6** | ⑤수요 | **깔때기** — 설계는 다섯 축 1.0, 문서가 안 잡혔다 |
| r4 | 17 | **5/6** | ⑤수요 | 같음. 가격은 오히려 9건 |

**r1·r2 의 원인은 코드로 막았다**(§19.10). r3·r4 는 설계가 좋은 채로 수요만 죽었다.

#### 수요가 죽는 자리 — 발췌가 아니라 **그 앞**이다

r4 실측(`tools/funnel.py`):

```
S12 1인 가구 혼자 식사   문서 8 · usable 5 · 발췌 5 · 인용 0
S13 혼밥 외식 수요       문서 4 · usable 1 · 발췌 1 · 인용 0
S14 배달비 부담         문서 5 · usable 2 · 발췌 2 · 인용 0
web 발췌 성공률 21.9% (슬롯 11 · 문서 71 · 인용 9)
```

발췌기의 not_found 문안과 창 크기를 열면 원인이 보인다:

```
khan.co.kr   본문 1,492자  "해당 문서에서 … 수치를 찾을 수 없었습니다"
yna.co.kr    본문 2,229자  "해당 문서에서 관련된 수치를 찾을 수 없습니다"
```

**검색이 PAIN 을 통계가 아니라 뉴스로 보내고, 그 뉴스 본문이 1.5~2.2k자 토막이라 안에
수치가 없다.** 발췌기는 정직하게 못 찾았다고 답한 것이다. 6/6 을 낸 `pin-09` 가 혼자 식사
41.7% 를 채운 출처는 **통계청**이었다. 즉 남은 간극은 설계 층이 아니라 **수집 층**이고,
판 ㉛ 이 「발췌가 병목」이라 부른 것의 **한 단계 위**(검색 표적·본문 도달)다.

#### 인용 전수 검사 — 「거짓 6/6」은 아니다

r4 의 채택 인용을 전수로 읽었다. 강한 값은 실재한다:

```
S1  TAM     38.041조  kosis DT_1KE10041 prdDe=2025   확인됨·확정
S5  GROWTH  34.805조  같은 표 2024                    확인됨·확정
S6  GROWTH  30.227조  같은 표 2023                    확인됨·확정
     → 34.805 / 30.227 = 15.146%  (성적표 15.1464% 와 일치)
S7  COMP    27.343조  DART CJ제일제당                 확인됨·확정
S8  COMP     3.675조  DART 오뚜기                     확인됨·확정
S9  COMP     2.526조  DART 풀무원식품                  확인됨·확정
S16 PRICE    4,200원 / 5,500원 / 3,500원 / 5,900원   미확인(편의점 도시락)
```

⚠ **TAM 11.412조 = 38.041조 × 0.3 이고 0.3 은 가정이다.** 판정 층이 그 사실을 값으로
달아 두었다 — 「추정점유율 0.3 · 판정 **가정** · 출처 0건 · 경계 「관측이 아니라 가정이다」 ·
반증 「상위 N사 점유율이 20% 미만이면 분모가 무의미해짐」」. 그리고 상위 카테고리 문제도
스스로 적었다 — 「⚠ 상위 카테고리 거래액을 밑동으로 쓰면 **상한으로만** 읽어야 한다 —
우리 상품은 그 안의 일부다(사다리 2단)」. **경계 표시가 제 일을 했다.**

⚠ 다만 그 0.3 은 컨셉의 침투율 가정(0.008)이 아니라 `_est` 의 기본 가정이다.
「⑥계산 채워짐」은 **가정 1개 위에 서 있다**는 뜻이지 관측으로 TAM 이 섰다는 뜻이 아니다.

### 19.10 이 판에서 고친 것 (전부 미커밋)

| 자리 | 무엇 | 왜 |
|---|---|---|
| `harness/slot_harness.py` | `repair_design()` 신설 + `judge` 배선 + `_decide` 기록 | 답이 표에 있는 권고를 코드가 고친다(LLM 0회) |
| `harness/slot_harness.py` | `build_prompt` 가 계량마다 **전형 크기**를 보여준다 | 지시 정합 — 실측에서 `value_range` 교정 **0건**이 됐다 |
| `harness/slot_harness.py` | `권고_재시도` 갈래 (`passed` 불변) | 지시가 있는데 모델이 요동하는 자리만 다시 뽑는다 |
| `harness/gate.py` | **`check_publishability`** 신설(권고) | `design_score` 에만 있고 게이트엔 없어 하네스가 못 봤다 |
| `harness/vocab.json` | `설계_교정` · `재시도.권고_재시도` · `대체재가격` 에 「판매가」 | 규칙은 파일에, 코드는 읽기만 |
| `tools/replicate_concept.py` | 시드 → 컨셉 재현(Java 사본) | 유료 앞에서 설계를 무료로 재려면 필요하다 |
| `backend/.../V21__research_competitor_seeds.sql` | `version` 칸 | 없어서 **백엔드가 기동 거부**했다 |
| `backend/.../ResearchConceptFactory.java` | javadoc 이 아직 계열 A 를 설명 | 우리가 의존하는 바로 그 필드 |

시험: `test_harness` **142** · `test_design_score` 19 · `test_failopen` 19 · `test_step12` 45,
전부 0 실패.

### 19.11 남은 것

1. **⑤수요** — 검색이 PAIN 을 통계로 보내게 하는 일(수집 층). 이번 판의 범위 밖이다.
2. **BM 캔버스 9칸** — 아직 안 세웠다(§18.6 이 남긴 자리).
3. `단가` var_role 의 「판매가」 — 계열 B 제품 컨셉에서 다시 나올 때 자리의 뜻부터 정한다.

---

## §20 판 ㉞ — ⑤수요의 진짜 원인은 **PDF 해석기 부재**였다 (2026-08-12, 미커밋)

### 20.1 §19.9 의 진단을 정정한다

§19.9 는 ⑤수요가 안 차는 원인을 「검색이 PAIN 을 통계가 아니라 뉴스로 보낸다」로 적었다.
**틀렸다.** 검색은 통계 PDF 를 물어왔고(kihasa·kiri·jthink), **우리가 읽지 못해 버렸다.**
남은 뉴스 기사만 발췌기에 들어가 정직하게 not_found 가 난 것이다.

원장을 사유별로 세면 답이 하나로 나온다:

```
pin-09          (로컬 CLI · 6/6)  문서 81 · usable 78 · pdf_unreadable  0
hmr-product-r1  (컨테이너)        문서 64 · usable 48 · pdf_unreadable  7
hmr-product-r2  (컨테이너)        문서 40 · usable 28 · pdf_unreadable  8
hmr-product-r3  (컨테이너)        문서 69 · usable 34 · pdf_unreadable 18
r4              (컨테이너)        문서 77 · usable 47 · pdf_unreadable 15
```

**48건이 전부 `pdfplumber 없음: ModuleNotFoundError`.**
`ai/requirements.txt` 는 `openai`·`requests`·`trafilatura` **셋뿐**이고 `pdfplumber` 가 없다.
컨테이너: 없음 · 로컬: 0.11.10. 엔진이 이미지 안으로 들어가면서 PDF 경로가 조용히 끊겼고,
**제품 경로가 실제로 수집을 돌린 것은 이번이 처음이라 제품으로 돌린 판은 예외 없이 전부**
PDF 를 버렸다.

**하필 ⑤에 몰린 이유**: 실태조사·설문 결과는 PDF 로 발행된다. pin-09 가 ⑤를 채운 문서도
통계청 PDF 였다(`kostat.go.kr/boardDownload.es` 혼자 식사 41.7% **확인됨**).

### 20.2 같이 정정할 두 가지

1. **판 ㉛ 의 「본문 도달률 54.1% → 87.3%」는 재계산 값이다.** `regrade-31` 은 `--from a4`
   재채점이라 fetch·extract 를 다시 안 돌았고, 두 원장의 문서 44건·분모 203,452자가 글자까지
   같다. 「모델에게 더 보냈다」가 아니라 **같은 문서를 새 상한으로 다시 잰 수**다.
2. **§19.9 의 「web 발췌 수율 21.9%」는 PDF 가 전멸한 상태의 관측이다.** 죽은 PDF 는 분모에
   아예 없다. 「web 검색의 본질적 수율」로 읽으면 안 된다 — pdfplumber 복구 후 다시 재야 한다.

### 20.3 HWP(한글 파일)는 **읽는 코드가 아예 없다**

- `adapters/web.py` 의 `fetch` 는 PDF 분기 다음에 `if "html" not in ctype:` →
  `http_status="not_html", content_status="empty"` 로 떨어뜨린다. `.hwp`·`.hwpx`·`.docx`·
  `.xlsx` 가 전부 여기다. `hwp` 를 다루는 코드는 저장소에 **0건**이다.
- ⚠ **PDF 와 달리 사유가 안 남는다.** PDF 는 판 ㉛ 이 「빈 페이지」와 「PDF 라 못 읽음」을
  갈라 `pdf_unreadable` + `error` 를 남기게 했는데(백로그 23·24), HWP 는 여전히 **진짜 빈
  페이지와 같은 칸(`empty`)** 에 앉는다. 이번 사고가 원장에서 보였던 것은 PDF 가 그 수리를
  받았기 때문이고, HWP 로 같은 일이 나면 **원장이 침묵한다.**
- 다만 실측으로 `not_html` 은 5판 전부 **0건**이다 — HWP 를 직접 받은 적은 없다.
- **더 중요한 것**: 정부 보도자료의 **HTML 페이지**는 잡히는데 본문이
  「첨부파일 — ….hwp (2.77MB) / ….hwpx / ….pdf … * 자세한 내용은 첨부된 파일을 참고하시기
  바랍니다」로 끝난다(`runs/after-docidx/a3_bodies.json` 실측). **수치가 첨부에만 있다.**
  즉 정부 통계의 정답은 첨부 파일 안에 있고, 우리가 여는 길은 지금 **PDF 하나뿐**이다.

### 20.4 다음 세션 착수점

**계획서: `~/.claude/plans/shiny-gathering-marble.md` (승인됨).** 순서대로:

0. 이 절(§20.1~20.3)이 그 계획의 0단계다 — **완료**.
1. **계측 4개**(LLM 0회) — 이게 본체다.
   - ① `runlog.Run.finish()` 에 `실행_능력`(pdfplumber·trafilatura 버전) + `coverage_caveat`
     한 줄. **이 한 줄만 있었으면 4판을 안 태웠다.**
   - ② `runlog` 가 `web_search_call.action.queries` 를 **세기만 하고 버린다**(r4: 호출 22 ·
     질의 209). `a3_web_query` 노드로 남겨 「질의가 달랐나 결과가 달랐나」를 가른다.
   - ③ `tools/funnel.py` 에 슬롯별 `content_status` 사유 축 + `--claim-type` + PDF 단계.
   - ④ `tools/preflight.py` 에 모듈 핑 — ⚠ **컨테이너 안에서** 돌려야 뜻이 있다.
2. 무료 눈확인 — r4 S12 의 khan·yna 본문을 `a3_bodies.json` 에서 직접 읽는다.
3. **L1**: `ai/requirements.txt` 에 `pdfplumber==0.11.10` 한 줄 + 이미지 재빌드.
   검증은 `--direct-urls`(`refetch: true`)로 죽은 PDF 만 되살리는 고리 — **LLM ≈6회**.
4. 무인 재현 — `--collect-slots S12,S13,S14` ×2, **≈20회/판**. 2/2 여야 유료 3판으로 간다.
5. 확정 — 화면에서 전 구간 3판, **3/3 6/6**(사용자 기준).

⚠ 컨테이너는 코드를 **굽는다**. `ai/`·`backend/` 를 고치면 반드시 `docker compose up -d --build`.
이 판에서 네 번 걸렸다(마이그레이션 jar · vocab · 프론트 화면 누락 · pdfplumber).

---

## §21 판 ㉟ 1단계 — 계측 4개 (2026-08-12, 미커밋, **LLM 0회**)

계획서 `~/.claude/plans/6-6-floating-wilkinson.md`. `requirements.txt` 는 **아직 안 고쳤다** —
순서를 뒤집으면 「고쳤더니 됐다」로 끝나고 다음 결함이 또 유료 4판을 먹는다.

### 21.1 무엇을 심었나

| | 자리 | 무엇 |
|---|---|---|
| ① | `runlog.py` `capability_fingerprint()` · `finish()` · `coverage_caveat()` | `result.json.실행_능력` = python·pdfplumber·trafilatura·requests·openai 버전. 없으면 **예외가 아니라 `None`**. pdfplumber 가 None 이면 caveat 에 「PDF 해석기 없음 — PDF 출처 커버리지 0」이 **어댑터 사유와 나란히** 붙는다(갈아치우지 않는다) |
| ② | `runlog.Meter.create(tag=…)` · `adapters/web.py:136` | 세기만 하고 버리던 `web_search_call.action.queries` 를 `a3_web_query` 노드로 남긴다. `tag` 는 **키워드 전용**이라 API 로 안 나간다. 질의 0 도 줄로 남는다 |
| ②-b | `schema.Document.is_pdf` · `web.fetch` PDF 분기 **두 갈래** | 사용자 확정. 없으면 해석기가 들어온 순간 「PDF 였다」가 사라져 3단계 통과 기준을 못 잰다. 표적 URL 이 `filedown.php`·`download.do` 라 확장자 추측이 안 먹는다 |
| ③ | `tools/funnel.py` | **PDF 단계** + 슬롯별 `본문사유`·`fetch사유`(코드까지)·`error사유`·`발췌사유` + `--claim-type` + 실행_능력 머리줄 |
| ④ | `tools/preflight.py` `modules` | pdfplumber·trafilatura·requests·openai 핑. 목록은 `runlog.CAPABILITY_PACKAGES` 를 **빌려 쓴다** — 두 곳에 적으면 갈라지고, 그 갈라짐이 이번 사고의 물리적 원인이었다 |

### 21.2 실증 — **이 절이 이 단계의 전부다**

컨테이너 안에서(`docker compose exec ai-server`, 재빌드 후):

```
[unreachable ] modules  설치 안 됨: pdfplumber  (pdfplumber 없음 · trafilatura 2.2.0
                        · requests 2.32.4 · openai 2.38.0)
진입 금지 — 유료 판에 들어가지 않는다        ← exit 1
```

컨테이너 재채점(`incontainer-p35`, LLM 0회)의 `result.json`:

```
실행_능력    {'python': '3.12.13', 'pdfplumber': None, 'trafilatura': '2.2.0', ...}
coverage     PDF 해석기 없음 — PDF 출처 커버리지 0
```

**이 두 줄이 있었으면 유료 4판(≈252회)을 안 태웠다.** 로컬은 여전히 전부 ok 로 뜬다
(pdfplumber 0.11.10) — 그 차이가 사고의 본체였고, 그래서 ①(수집 프로세스가 자기 능력을
적는다)이 ④(사람이 따로 돌린다)보다 먼저다.

### 21.3 깔때기가 이제 말하는 것 — r4 실측

`python tools/funnel.py --run 0c54ffb5-… --claim-type PAIN`

```
본문 판정                      17    8    9   empty 3 · pdf_unreadable 6
PDF 해석 (하한 — is_pdf 미측정)  6    0    6   pdfplumber 없음 6
  S12  본문: empty 1 · pdf_unreadable 2 | fetch: blocked:429 1 · ok 2
       | error: pdfplumber 없음 2 | 발췌: not_found 5
  S13  본문: pdf_unreadable 1 · empty 2 | fetch: ok 1 · blocked 1 · blocked:404 1
       | error: pdfplumber 없음 1 · SSLError 1 | 발췌: not_found 1
  S14  본문: pdf_unreadable 3 | fetch: ok 3 | error: pdfplumber 없음 3 | 발췌: not_found 2
```

- ⑤수요 3슬롯에서 죽은 PDF 는 **6건**(전 구간 15건 중). §20 의 15 는 실행 전체 수다.
- **S12 의 429 가 값으로 보인다** — 4단계 예비 레버 L2(재시도)의 근거가 원장에 섰다.
- 옛 원장은 `is_pdf` 칸이 없어 **「하한」이라고 이름에 적는다.** 없는 수를 0 으로 적으면
  미측정이 0 으로 둔갑한다.

### 21.4 동작 무변경 증명 (R5)

`--from a4` 재채점의 라벨을 **계측을 뺀 코드**와 대조했다(`nopatch-pin-09` vs
`regrade-p35-pin-09`): `{확인됨 9 · 미확인 3 · 출처약함 6 · off_slot 3}` **완전 일치.**
계측은 채점을 안 건드린다.

> ⚠ **따로 발견한 것 — 골든 3개가 이미 갈려 있다.** 원본 원장과 현재 코드의 재채점이
> 다르다. 계측 **이전부터** 그랬고(위 대조가 그것을 갈랐다), 규칙 version 문자열은 같다 —
> 즉 판 ㉜~㉞ 의 미커밋 엔진 변경이 골든에 재채점되지 않은 채 남아 있다.
>
> ```
> pin-09       원본 {확인됨 9 · 미확인 3 · 출처약함 5 · off_slot 2}
>              재채점 {확인됨 9 · 미확인 3 · 출처약함 6 · off_slot 3}
> p32-auto01   원본 {off_slot 13 · 미확인 3} → 재채점 {확인됨 6 · off_slot 3 · 미확인 13}
> paid31a-hmr  원본 {확인됨 3 · off_slot 3 · 미확인 5} → 재채점 {확인됨 3 · off_slot 4 · 미확인 7}
> r4           **일치** (가장 최근 원장)
> ```
>
> **골든을 비교 축으로 쓰기 전에 이것부터 정해야 한다** — 재채점본으로 골든을 갱신할지,
> 원본을 그대로 둘지. 이 판에서는 건드리지 않았다.

### 21.5 회귀 그물

`tests/test_step17.py` **신설 (40검사)** — R3(칸 존재)·②(tag 가 API 로 안 나감·단수형·
모르는 모양·질의 0)·②-b(기본값 False 로 옛 원장 복원)·③(PDF 단계·사유 4축·claim_type·
하한 표시). 기존 baseline 무변: harness 142 · design_score 19 · failopen 19 · step12 45 ·
step10 39 · step1 31 · step4 71, 전부 0 실패.

> ⚠ **`fresh_run` 지뢰.** `tests/test_step12.py` 의 `fresh_run` 은 `runlog.RUNS_DIR`(**읽기**
> 씨앗)를 지우는데 `Run` 은 `runpath.write_dir()`(쓰기)에 쓴다. 지워지지 않으니
> `run.jsonl` 이 append-only 로 쌓여 **두 번째 실행부터 지표가 배로 보인다.**
> `test_step17` 에서 실제로 걸려 `write_dir` 로 고쳤다. **step12 쪽은 안 고쳤다**(내 변경이
> 아니다) — 그 파일이 흔들리기 시작하면 여기가 원인이다.

### 21.6 다음

2단계(무료 눈확인) → 3단계(`pdfplumber==0.11.10` 한 줄 + 재빌드). 계획서 그대로.

---

## §22 판 ㉟ 2·3단계 — PDF 는 살았다. ⑤는 안 찼고 **원인이 바뀌었다** (2026-08-12, 미커밋)

유료 **LLM 6회**. 사전등록·결과는 `expected.md` 부록 AK.

### 22.1 2단계 무료 눈확인 — **문서 문제였다, 발췌 문제가 아니었다**

r4 의 S12 usable 5건 중 4건에 `혼자` 가 **0회**다. 남은 1건은 「혼자 **살고 있는**」
(거주지, 식사 아님). 본문은 1인가구 소득·주거·외로움 통계였다 — 발췌기는 정직했다.
S13 의 `혼밥` 14회는 KCI 논문 초록(혼밥↔우울감)이라 경험률 % 가 아니고, S14 의 28.3% 는
**소상공인**의 중개수수료 만족도였다.

→ **L3(대체 추출기)는 착수하지 않는다.** 계획서가 건 조건 그대로다.
→ `design_score`: r4 슬롯은 `value_range_자릿수` 1.0 · `must_contain_규율` 1.0 —
   ⑤ 표적은 pin-06 함정에 안 걸린다.

### 22.2 3단계 L1 — `pdfplumber==0.11.10` 한 줄

`ai/requirements.txt` 에 한 줄 + `docker compose up -d --build ai-server`.
**엔진 코드는 한 글자도 안 바꿨다.** 컨테이너 preflight 가 `unreachable → ok` 로 뒤집혔다.

`pain-pdf-01` (`--from a4 --source-run <r4> --direct-urls data/direct_urls_pain-pdf.json`,
`refetch: true`, channel `gov_doc`):

```
PDF 해석      들어감 6   나옴 6   잃음 0        ← 「하한」 표시도 사라졌다(is_pdf 측정본)
성적표        ①②③④⑥ 값까지 r4 와 완전 동일 · ⑤수요 미확보 (인용 0)
```

**L1 은 확정이다.** 429 로 죽었던 kihasa 까지 6/6 살았다. 그러나 ⑤는 안 찼다.

### 22.3 왜 안 찼나 — **세 가지가 겹쳤다.** 셋 다 무료로 갈랐다

1. **모집단 불일치 (핵심).** clik.nanet PDF 에 「사업자의 **69.3%** 가 배달비에 부담을
   느끼고 있음」이 **15,253자 지점 — 상한 20,000자 안**에 있다. 발췌기가 **보고도**
   안 집었고, 이유가 바로 옆에 있다: `Base: 배달앱 이용사업자(n=300)`.
   r4 의 mss 28.3%(소상공인 만족도)와 **같은 함정**이다.
2. **어휘 불일치.** gmr PDF 에 진짜 소비자 경험률이 있다 — 「소비자의 **82.8%** 가
   ‘필요 이상의 음식을 주문하는 경험을 한 적이 있다’」. 그런데 그 문장의 낱말은
   `배달팁`·`배달 비용`·`최소 주문금액`이고 **`배달비` 가 아니다.**
   슬롯 `must_contain=['배달비']` 가 **구조적으로** 못 잡는다.
3. **시점 불일치.** kiri PDF 는 「혼밥 비중 각각 90%」를 담았지만 **2017년 문서**다
   (슬롯 period 2025).

> **정정 — §20 진단의 남은 절반.** §20 은 「우리가 PDF 를 못 읽어 버렸다」였고 그건
> 맞았다(6/6 되살아났다). 그러나 **읽고 나니 답이 거기 없었다.** 검색이 PAIN 슬롯에
> 물어온 PDF 는 대부분 **사업자·정책 자료**이고, 소비자 실태조사는 다른 낱말을 쓴다.

### 22.4 4단계의 레버가 바뀌었다 — 계획서 갱신

계획서는 0/2 일 때 **L4(검색 프롬프트 PAIN 힌트)** 를 걸라 했고 근거는 「검색이 통계를
안 물어온다」였다. **검색은 물어왔다.** 후보가 둘로 갈린다 — **한 번에 하나만** 건다.

| | 무엇을 | 겨누는 것 | 실패의 뜻 |
|---|---|---|---|
| **L4** | `prompts.py` 에 `v33-pain` 신설 — 「**소비자 대상** 실태조사·설문」을 못 박는다 | 22.3 의 1·3 | 질의가 안 바뀌면 레버가 프롬프트에 닿지도 않은 것 (판별은 판 ㉟ ②의 `a3_web_query` 노드) |
| **L5** (신설) | 슬롯 어휘 — `must_contain`/`subject_aliases` 에 `배달팁`·`배달 비용` | 22.3 의 2 | ⚠ **완화가 아니어야 한다.** `must_contain` 은 `any()` 다 — 늘리면 느슨해진다. `must_not_contain` 에 **모집단 가드**(사업자·점주·소상공인)를 같이 넣어야 하고, `design_score.must_contain_규율` 축이 그것을 감시한다 |

여전히 **하지 않는다**: L3(대체 추출기 — 22.1 이 죽였다) · `route_sources` PAIN 분기 ·
`SEARCH_V1` 직접 수정(전 원장 비교 축이 소급 파괴된다).

### 22.5 새 파일

- `ai/app/research/research2/data/direct_urls_pain-pdf.json` — L1 검증 고리 사양
- `ai/app/research/research2/data/slots_r4-snapshot.json` — r4 슬롯 덤프(`design_score` 입력용)

---

## §23 판 ㉟ 4단계 — **성적표 2/2 6/6, 인용 검사 0/2. 5단계로 안 간다** (2026-08-12, 미커밋)

`pain-full-01`(LLM 19) · `pain-full-02`(LLM 15) = **34회**. 레버 0개, L1 만 들어간 상태에서
PAIN 3슬롯 무인 재수집 ×2. 사전등록·결과 전문은 `expected.md` 부록 AL.

### 23.1 좋은 소식 — L1 은 수집 경로에서도 작동한다

- **`pdfplumber 없음` 0건.** 남은 `pdf_unreadable` 1건은 사유가
  「텍스트층 없음(스캔본 추정)」 — **진짜 스캔본**이다.
  판 ㉟ ③의 error 축이 없었으면 이 둘이 같은 칸에 앉아 「아직도 PDF 가 죽는다」로 오진했다.
- `is_pdf` 로 잰 PDF 문서 판1 **7건** · 판2 **4건**. 「하한」 표시 없음.
- ①②③④⑥ 값까지 무변 — 부분 수집이 기존 확보를 구조적으로 보존한다.

### 23.2 나쁜 소식 — 6/6 은 **성적표의 6/6 이지 산출물의 6/6 이 아니다**

**판1** ⑤ 근거가 **하나**고, 그것이 `published_year: 2018` 인 보도자료인데
`year: 2025` 로 찍혔다(`year_source: 문맥에서 슬롯 기간 창 안의 연도`). 수치·단위·모집단은
맞다 — **연도만 틀렸고, `published_year` 와 정면으로 어긋나는데 A4 가 통과시켰다.**

**판2** 는 `R11 blocker` 가 터졌다:
```
같은 지표 두 값 {'배달비 부담|문제 경험률|대한민국|2025': [4.5, 13.7, 95.0]}
```
- 4.5% = 「**입점업체**가 인식하는 적정 **중개수수료**」 — 사업자 모집단 · 다른 지표
- 13.7% · 95% = 인용문이 **숫자 문자열뿐**이라 무엇의 비율인지 없다
- S13 = 「2025년 **전 세계** 1인 식사 **예약** 19% 증가」 — 지역도 지표도 다르다
- 3,333**원** 이 단위 `%` 슬롯에 들어왔다

> **성적표만 보면 안 된다**(규율 §3)는 것이 세 번째로 실증됐다. 판 ㉜ 은 `pin-06` 이
> 6/6 인데 오답이었고, 이번엔 **체인 위반이 성적표와 동시에** 났다.

### 23.3 판정 — 유료 3판(189회)을 태우지 않는다

정지 규칙 「2/2 → 5단계」는 **문자 그대로 충족했다.** 그러나 「채택된 행의 인용을 직접
본다」는 상시 규율이고 거기서 두 판 다 무너졌다. 지금 5단계로 가면 **거짓 6/6 을 세 번
사는 것**이다. (사후 합리화 아님 — 같은 인용 검사를 부록 AK 에서도 돌렸고, 그 검사가
AK 의 「PDF 는 살았는데 답이 없다」를 낳았다.)

### 23.4 다음은 레버가 아니라 **가드**다 — 계획서 4단계를 갈음한다

실패가 전부 「검색이 못 찾아서」가 아니라 **「틀린 것을 통과시켜서」**다.

| | 무엇 | 겨누는 실패 | 왜 지금 |
|---|---|---|---|
| **G2** | PAIN 슬롯 `must_not_contain` 에 **모집단 가드**(사업자·점주·입점업체·소상공인) | 판2 의 4.5% · AK 의 69.3% · r4 의 28.3% | **세 판에 걸쳐 세 번 나온 유일한 반복 실패**. 여기부터 |
| **G1** | `published_year` 와 `year` 가 어긋나면 감점 또는 격리 | 판1 의 2018→2025 | ⑤가 이 한 건에 걸려 있다 |
| **G3** | 인용문에 지표 서술 없이 숫자만 있으면 격리 | 판2 의 「95%」·「13.7%」 | 세 번째 |

**가드는 결정론이라 `--from a4` 재채점(LLM 0회)으로 잴 수 있다.** 이미 있는 원장
(`pain-full-01`·`02`·`pain-pdf-01`·r4)이 그대로 시험대다.

### 23.5 처음 잰 것 — 검색 분산 (판 ㉟ ②의 첫 수확)

**두 판의 실제 질의 겹침이 56개 중 4개(7%)다.** 문안(`SEARCH_V1`)은 같았으니 **모델 분산**.

| 질의 | 결과 | 뜻 |
|---|---|---|
| **다름(문안 동일)** | 다름 | **← 여기다.** 모델 분산 |

뜻: **단일 실행 비교로 검색 프롬프트(L4)를 판정할 수 없다.** L4 를 걸려면 반복 판이 필요하고,
그래서 더더욱 가드가 먼저다 — 가드는 1판으로, 그것도 공짜로 잴 수 있다.

### 23.6 누적 비용

계측 0 · L1 검증 6 · 무인 재현 34 = **40회.** 계획서 예산(≈235) 대비 195회 남았다.

---

## §24 판 ㊱ — 게이트를 고쳤다. 거짓 6/6 이 사라지고 **1/2** 이 남았다 (2026-08-12, 미커밋)

유료 **30회**(누적 70). 사전등록·결과는 `expected.md` 부록 AM.

### 24.1 새로 생긴 것

- **`tools/quote_audit.py`** — 채택된 사실의 인용문을 네 눈으로 본다
  (불가능_연도 · 모집단 · 무서술_인용 · 지역_이탈). **LLM 0 · 세기만 한다.**
  ⚠ 면제 설정은 **원장의 규칙에서 읽는다** — 사본을 두었더니 규칙이 통과시킨 PRICE 를
  감사기가 계속 집었다(재는 자와 자르는 자가 갈리는 그 실패의 N번째).
- **`rules/scoring.v1.json` v6 → v7** — off_slot 에 네 겹. 각자 `enabled` 를 가진다
  (측정 조건 = 규칙 값, 한 번에 하나만 켜서 잰다).
- `blocks/a_desk.py` — 네 겹 구현. `_OFF_SLOT_LAYERS` 에도 등록(안 하면 「기타」로 뭉개진다).

> ⚠ **새 겹은 맨 뒤여야 한다.** 처음에 「사실 자체의 성립이니 앞」이라 두었더니 기존 겹의
> 사유를 가로채 **검사 17개가 통째로 뒤집혔다**(must_contain·값범위·판 ⑩ 가격 면제).
> 최후의 겹이어야 «채택될 뻔한» 것만 걸러 기존 진단이 뜻을 지킨다.

> ⚠ **무서술 겹은 PRICE·API 채널을 면제한다.** 가격 인용은 본질적으로 짧고(「4,900원」)
> 그 정체는 판 ⑩ 의 조회시점 장치가 지킨다. API '인용문'은 표의 칸이지 문장이 아니다.

### 24.2 가드 판정 — 재채점(LLM 0)

| 원장 | 가드 전 | 가드 후 | quote_audit |
|---|---|---|---|
| **pin-09** (양성 대조) | 6/6 | **6/6** | 0 |
| r4 | 5/6 | 5/6 | 0 |
| pain-full-01 | **6/6** | **5/6** | 0 |
| pain-full-02 | **6/6** | **5/6** | 0 |

**거짓 6/6 둘이 내려가고 정직한 6/6 하나가 살아남았다.** 표적만 정확히 맞았다:
80.1%(불가능 연도) · 4.5%(모집단) · 95%·13.7%(무서술) · 19%(지역 이탈).

### 24.3 5단계 재현 ×2 — **1/2**

```
p36-full-01   ⑤ 채워짐(42.6% 확인됨/확정) · audit 0 · blocker 0   → 통과
p36-full-02   ⑤ 미확보                    · audit 0 · blocker 0   → 정직한 미확보
```

판1 의 채움은 판 ㉟ 의 넷과 질이 다르다 — 「균형 잡힌 식사의 어려움(42.6%)」,
easylaw.go.kr, 1인 가구 모집단, year=published_year=2023.
⚠ **다만 지표가 정확히 같지는 않다**(슬롯은 「혼자 식사 문제 경험률」). 가드 넷은
모집단·시점·서술·지역을 보지 **잰 것이 같은지는 못 본다** — 그 겹은 `must_contain` 이다.

**유료 3판을 안 태운다.** 1/2 는 분산이고, 두 판의 차이는 가드가 아니라 검색이
easylaw 를 물었느냐다. 질의 겹침 7% 인 층에서 1/2 는 「됐다」가 아니라 「운이었다」다.

### 24.4 다음 — 6′ L4 밖에 안 남았다

가드를 켠 뒤에도 ⑤가 반만 차고, **차는 쪽조차 우연히 좋은 문서를 물었을 때**다.
남은 레버는 「검색이 무엇을 물어오게 하는가」뿐이다.
⚠ 질의 분산 7% 때문에 L4 는 **전후 2판씩 = 4판(≈80회)**. 예산(235) 누적 70 이라
80 을 더 쓰면 150 이고, 그 뒤 확정 3판(189)은 **예산을 넘는다** — 착수 전 재승인이 필요하다.

### 24.5 물려받은 흠

`tests/test_step9.py` 는 `runs/<id>/result.json` 을 직접 읽어 깨져 있다(**기존**).
`runlog.RUNS_DIR`(읽기) vs `runpath.write_dir`(쓰기) 분리를 안 따라간 자리로,
`test_step12.fresh_run` 과 같은 지뢰다. 내 변경과 무관하다.

---

## §25 판 ㊱ 6′ — L4 `v33-pain`. **주지표 판정 불가, 부지표는 갈렸다** (2026-08-12, 미커밋)

유료 **34회**(누적 **104**). 사전등록·결과는 `expected.md` 부록 AN.

### 25.1 처치 — 한 줄. 그리고 왜 그 한 줄이 없었나

`search()` 는 `claim_type_hint` 를 **줄곧 넘기고 있었는데** `SEARCH_V1` 에 자리가 없어
`render()` 가 조용히 버렸다. **좋은 힌트가 한 번도 모델에 닿은 적이 없다.**

- `prompts.SEARCH_V33_PAIN` = `SEARCH_V1` + `자료 성격: {claim_type_hint}` **한 줄**
- PAIN 힌트에 **모집단**을 넣었다 — 옛 힌트는 「설문·실태조사에서 찾아라」까지만 말하고
  «누구에게 물었나»가 없었고, 우리가 세 번 걸린 자리가 정확히 거기다
- ⚠ `SEARCH_V1` 은 **글자 하나 안 고쳤다**(해시 `d4ccd39af4fd`). 힌트 덮어쓰기도
  `CLAIM_TYPE_HINT_BY_VARIANT` 로 **변종별**이라 `v12-2` 가 가리키는 글도 그대로다 —
  이름이 가리키는 문안이 조용히 달라지면 전 원장의 비교 축이 소급 파괴된다
- 켜는 법: `--search-prompt v33-pain` (규칙에 심겨 `result.json` 까지 간다)

### 25.2 주지표 — 개선이라 부를 수 없다

```
전 v1        1/2   (p36-full-01 통과 · 02 미확보)
후 v33-pain  1/2   (p36-l4-01 미확보 · 02 통과)
```

사전등록은 「2/2 여야 개선」이었다. 아니었다. 표본 2대2, 질의 겹침 3%(108개 중 3).

### 25.3 부지표 — 여기서 갈렸다

| | ⑤를 채운 근거 |
|---|---|
| v1 | easylaw.go.kr 「균형 잡힌 식사의 어려움(42.6%)」 — 모집단·시점은 맞지만 **지표가 다르다** |
| **v33-pain** | **kostat.go.kr** 「혼자 식사한 비율은 아침 식사한 사람 중 **41.7%**(2.9%p)…」 |

**후자는 `pin-09` 이 ⑤를 채운 바로 그 통계청 원출처이고 지표까지 정확히 같다.**
제품 경로가 이 문서를 찾아온 것은 **처음**이다. 그리고 그 문서는 PDF 라 판 ㉟ L1
(pdfplumber) 없이는 읽히지도 않았다 — 두 판의 처치가 여기서 만난다.

⚠ **표본 1건이다.** 「레버가 정답 문서를 부른다」가 아니라 「레버를 켠 판에서 정답
문서가 **처음 나타났다**」까지가 관측이다.

### 25.4 계측이 답한 것 — 네 칸 표

| 질의 | 결과 | 뜻 |
|---|---|---|
| **다름**(모집단·조사 낱말 39%→60%) | 주지표 같음 · 부지표 다름 | 레버는 **닿았고** 문서 선택을 바꿨으나 **판정 수준을 못 넘겼다** |

「죽은 레버」와 다르다 — 죽은 레버는 질의가 안 바뀐다. 판 ㉟ ② 의 `a3_web_query` 가
없었으면 이 둘을 **구별할 수 없었다.**

### 25.5 상태와 남은 것

- `v33-pain` 은 **미채택 변종으로 보존**한다(v12-2 와 같은 자리). 기본은 여전히 `v1`.
- 확정 3판(189회)은 **예산 초과**라 안 태웠다. 누적 104 / 예산 235.
- 다음에 표본을 늘린다면 **v1 2판 + v33-pain 2판을 더** 붙여 4대4 로 보는 것이 최소다
  (≈70회). 그 전에 「⑤ 하나에 이만큼 쓸 값어치가 있나」를 먼저 정해야 한다.

---

## §26 판 ㊱ 7단계 — **연속 3판 6/6 도달.** 검색 표본 2→6 (2026-08-12, 미커밋)

유료 **138회**(누적 **242** / 예산 235 — **7회 초과**). 사전등록·결과는 `expected.md` 부록 AO.

### 26.1 목표 달성

```
p36-n6-01  6/6 · ⑤ n=2 최고등급 확정 · quote_audit 0 · R11 blocker 0   (LLM 44)
p36-n6-02  6/6 · ⑤ n=2 최고등급 확정 · quote_audit 0 · R11 blocker 0   (LLM 41)
p36-n6-03  6/6 · ⑤ n=6 최고등급 확정 · quote_audit 0 · R11 blocker 0   (LLM 53)
```

**사용자 기준(연속 3판 6/6) + 판 ㊱ 의 새 기준(인용 검사 0 · 체인 clean)을 동시에 만족한다.**

### 26.2 무엇이 그것을 만들었나 — 세 판이 겹쳐야 했다

| 판 | 처치 | 없었으면 |
|---|---|---|
| ㉟ L1 | `pdfplumber` 한 줄 | 정답 문서가 **PDF 라 읽히지도 않는다** |
| ㊱ 가드 | off_slot 네 겹 + `quote_audit` | 6/6 이 나와도 **거짓인지 알 수 없다** |
| ㊱ L4+표본 | `v33-pain` + `search_samples` 2→6 | 정답 문서에 **닿지 못한다** |

**세 판 모두 ⑤의 주근거가 같다** — `kostat.go.kr` 「혼자 식사한 비율은 아침 식사한 사람
중 41.7%…」. pin-09 이 쓴 그 문서이고, 6′ 에서 **한 번** 나타났던 것이 표본 6에서
**세 번 다** 나왔다(q≈0.25 · N=6 → 82% 예측과 일치).

### 26.3 표본 수는 규칙이자 CLI 다

- `rules.adapters.web.search_samples` (기본 **2** — 옛 원장과 같은 조건)
- `run.py --search-samples N` → 규칙에 심겨 `result.json` 까지 간다.
  ⚠ **규칙 파일을 손으로 고쳐 재지 말 것.** 그러면 측정 조건이 파일 상태에 숨는다
  (이 판에서 실제로 그렇게 쟀고, 무슨 조건이었는지 알려면 파일 이력을 뒤져야 했다).

### 26.4 벽시계 — 순차 루프가 지배항이 됐다

`search()` 의 표본 루프만 **순차**였다. 슬롯(`run.MAX_WORKERS=5`)·발췌
(`EXTRACT_WORKERS=5`)는 이미 병렬인데 거기만 남아 있었고, N 을 2→6 으로 올리자
**판당 ≈10분**이 됐다. 병렬로 바꿨다(`search_workers=6`).

- 가짜 계량기 실측 **2.4초 → 0.41초**(6표본), 표본 순서 보존 — **유료 0회로 확인**
- ⚠ **실전 벽시계는 아직 안 쟀다.** 슬롯 병렬 안의 병렬이라 동시 실행이 최대 5×6=30 이다.
  429 를 만나면 **`search_workers` 를 조인다**(표본 수 N 을 줄이면 적중률이 같이 떨어진다)

### 26.5 남은 흠 — 다음 가드 G5

`quote_verified` 는 **인용문이 문서에 있는지만** 보고 **값이 인용문 안에 있는지는 안 본다.**

```
S14 75.9%  「배달비 부담 역시 과하다***고 느끼고 있었다.」   ← 숫자가 없다
S13 54.0%  「이들은 주거비를 제외하면 식품 구매와 외식비에…」  ← 값도 지표도 없다
```

둘 다 미확인/추정이라 ⑤의 확정 등급을 만들진 않았지만 `n` 을 부풀렸다.
⚠ G5 는 **단위 환산을 반드시 다뤄야 한다** — KOSIS 의 `"DT": "38041110"`(백만원)과
value 38041110000000 은 자릿수가 다르고, 순진하게 대조하면 정상 3건이 오탐이 된다.

### 26.6 상태

- 시험 16파일 **0 실패**(`test_step6` 은 소스 grep 단언을 **동작 검사**로 바꿨다 —
  `_unused_query` 라는 **변수 이름**을 찾고 있었고, 이름은 계약이 아니다)
- 예산 **242/235**. 더 태우려면 재승인
- `test_step9` 는 여전히 물려받은 `runs/` 경로 지뢰로 깨져 있다

---

## §27 다음 세션 착수점 — **읽을 것은 이 절 하나다** (2026-08-12 세션 끝)

> 다음 세션은 **플랜 모드로 계획을 새로 짠다.** 아래가 그 입력이다.
> 다시 조사하지 마라 — 유료 242회어치 진단이 §20~§26 에 값으로 남아 있다.

### 27.1 지금 어디인가 — 한 문단

제품 경로 시장조사가 **연속 3판 6/6** 을 냈다(`p36-n6-01/02/03`). 성적표뿐 아니라
**인용 검사 0 · 체인 blocker 0** 을 같이 만족한다. 세 판 모두 ⑤를 같은 문서로 채웠다 —
통계청 사회조사 「혼자 식사 41.7%」(`kostat.go.kr`), pin-09 이 쓴 그 원출처다.
**셋이 겹쳐서 됐다**: pdfplumber(판㉟) · 가드 넷+quote_audit(판㊱) · v33-pain+표본 6(판㊱).

### 27.2 사용자가 정한 다음 두 가지 — **이 순서다**

1. **G5 — 숫자 없는 인용을 막는다.** `quote_verified` 는 인용문이 문서에 있는지만 보고
   **값이 인용문 안에 있는지는 안 본다.** 실측 2건:
   ```
   S14 75.9%  「배달비 부담 역시 과하다***고 느끼고 있었다.」   ← 숫자 0개
   S13 54.0%  「이들은 주거비를 제외하면 식품 구매와 외식비에…」  ← 값도 지표도 없다
   ```
   ⚠ **단위 환산을 반드시 다뤄야 한다.** KOSIS `"DT": "38041110"`(백만원) vs
   `value_num 38041110000000` — 순진하게 대조하면 **정상 3건이 오탐**이 된다(실측).
   자리: `rules.scoring.off_slot` 에 다섯째 겹 + `tools/quote_audit.py` 에 같은 눈.
   **LLM 0회** — 기존 원장 재채점으로 잰다.

2. **전 구간 3판 검증.** 지금까지의 3판은 **부분 수집**이다(`--collect-slots S12,S13,S14`,
   나머지 14슬롯은 r4 결과를 물려받음). 「진짜 처음부터 3연속」은 아직 아니다.
   판당 **60~70회 추정** → 3판 ≈200회. **예산 재승인 필요**(누적 242/235).

### 27.3 착수 전에 알아야 할 것 — 전부 실측이다

- **컨테이너는 코드를 굽는다.** `ai/` 를 고치면 반드시 `docker compose up -d --build ai-server`.
  이번 세션에서만 다섯 번 걸렸다.
- **새 off_slot 겹은 맨 뒤에 둔다.** 앞에 뒀더니 기존 겹의 사유를 가로채 **검사 17개가
  뒤집혔다**. `_OFF_SLOT_LAYERS`(`blocks/a_desk.py`) 등록도 같이 — 안 하면 「기타」로 뭉개진다.
- **재는 자와 자르는 자를 갈라 두되, 설정은 한 곳에서 읽는다.** `quote_audit` 은 세기만
  하고 `rules` 가 자른다. 면제 설정을 양쪽에 사본으로 두었더니 **규칙이 통과시킨 것을
  감사기가 계속 집었다.** 지금은 감사기가 원장의 규칙을 읽는다.
- **가드 보정 기준은 `pin-09` 다.** ⑤를 정직하게 채운 유일한 옛 원장이다.
  **pin-09 의 ⑤(41.7%)를 죽이는 가드는 과조임이니 되돌린다.**
- **표본 수는 `--search-samples N` 으로 판마다 명시한다.** 규칙 파일을 손으로 고쳐 재면
  측정 조건이 파일 상태에 숨는다(이번에 실제로 그랬다).
- **검색 표본은 병렬이다**(`search_workers=6`). 슬롯 병렬 안의 병렬이라 동시 최대 5×6=30.
  **429 를 만나면 `search_workers` 를 조인다** — N 을 줄이면 적중률이 같이 떨어진다.
  ⚠ 병렬화의 **실전 벽시계는 아직 안 쟀다**(가짜 계량기로 2.4초→0.41초만 확인).
- **골든 3개(`pin-09`·`p32-auto01`·`paid31a-hmr`)는 원본과 재채점이 이미 갈려 있다**(§21.4).
  비교 축으로 쓰기 전에 「갱신할지 원본을 둘지」를 먼저 정한다.
- `tests/test_step9.py` 는 물려받은 `runs/` 경로 지뢰로 깨져 있다. 내 변경과 무관하다.

### 27.4 도구와 명령

```powershell
cd ai/app/research/research2
python tools/quote_audit.py --run <id> [--claim-type PAIN]   # 채택 인용 4눈 (LLM 0)
python tools/funnel.py --run <id> [--claim-type PAIN]        # 사유 축·PDF 단계 (LLM 0)
python tools/preflight.py --need modules --no-paid           # ⚠ 컨테이너 안에서
python run.py --id <새id> --from a4 --source-run <원장>       # 재채점 (LLM 0)
python run.py --id <새id> --from a4 --source-run <r4> \
       --collect-slots S12,S13,S14 --search-prompt v33-pain --search-samples 6
```

시험은 **파일별**로 (`python -m pytest` 는 이 폴더를 안 돈다). 현재 기준선:
step1 31 · step2 67 · step3 46 · step4 71 · step5 53 · step6 61 · step8 99 · step10 39 ·
step11 18 · step12 45 · step13 45 · step14 40 · **step17 40** · harness 142 ·
failopen 19 · design_score 19 — **전부 0 실패**.

### 27.5 이번 세션이 만든 것 (전부 미커밋)

| 새 파일 | 무엇 |
|---|---|
| `tools/quote_audit.py` | 채택 인용 검사 (불가능_연도·모집단·무서술_인용·지역_이탈) |
| `tests/test_step17.py` | 판㉟ 계측 회귀 40검사 |
| `data/direct_urls_pain-pdf.json` | L1 검증 고리 사양 |
| `data/slots_r4-snapshot.json` | r4 슬롯 덤프(design_score 입력) |

| 고친 파일 | 무엇 |
|---|---|
| `ai/requirements.txt` | **`pdfplumber==0.11.10`** — 이 한 줄이 판㉞ 사고의 원인이었다 |
| `runlog.py` | `실행_능력` 지문 · `Meter.create(tag=)` → `a3_web_query` 노드 |
| `schema.py` | `Document.is_pdf` |
| `adapters/web.py` | PDF 표식 · 검색 tag · **표본 수 규칙화 + 병렬화** |
| `blocks/a_desk.py` | off_slot 네 겹(맨 뒤) + `_OFF_SLOT_LAYERS` 등록 |
| `prompts.py` | `SEARCH_V33_PAIN` · `CLAIM_TYPE_HINT_BY_VARIANT` (v1 불변, 해시 `d4ccd39af4fd`) |
| `rules/scoring.v1.json` | **v6 → v7** — off_slot 네 겹 |
| `rules/adapters.v1.json` | `search_samples`(기본 2) · `search_workers`(6) |
| `run.py` | `--search-samples` |
| `tools/funnel.py` | PDF 단계 · 사유 4축 · `--claim-type` |
| `tools/preflight.py` | `--need modules` |
| `tests/test_step6/8/11` | 픽스처 인용문을 문장으로 · 소스 grep 단언을 동작 검사로 |

### 27.6 유료 원장 (이번 세션)

```
pain-pdf-01     6    L1 검증 (PDF 6/6 되살아남)
pain-full-01/02 34   가드 전 재현 — 성적표 2/2 6/6, **인용은 0/2**
p36-full-01/02  30   가드 후 재현 — 1/2
p36-l4-01/02    34   v33-pain — 1/2, 정답 문서 첫 등장
p36-n6-01/02/03 138  표본 6 — **3/3 6/6 · 인용 0 · blocker 0**   ← 목표 도달
합계 242 (예산 235)
```

### 27.7 ⚠ 세션 종료 시점의 어긋난 상태 하나

`rules/adapters.v1.json` 의 `search_samples` 가 **호스트 2 · 컨테이너 6** 이다.
3판을 돌릴 때 컨테이너 안 규칙 파일을 손으로 고쳤고 그 뒤 재빌드를 안 했다.

- **다음 `docker compose up -d --build` 가 컨테이너를 호스트값(2)으로 되돌린다.** 정상이다.
- 그 전에 컨테이너에서 뭔가 돌리면 **표본 6으로 돈다** — 재빌드 전 실행 결과를
  「기본 조건」으로 읽지 마라. `result.json.rules` 를 보면 실제 값이 적혀 있다.
- **호스트 기본을 2로 남긴 것은 의도다.** 지금까지의 모든 원장이 2 조건이고,
  기본을 6으로 올리면 옛 원장과의 비교 축이 조용히 갈린다. 6이 필요하면
  `--search-samples 6` 으로 **판마다 명시**한다.

---

## §28 판 ㊲ — G5, 그리고 **제품 경로에서 처음으로 진짜 6/6** (2026-08-12, 미커밋)

> §27 이 정한 두 가지 중 **1단계(G5)는 끝났고**, 2단계는 CLI 3판 대신 **제품 경로 3사업안**으로
> 바뀌었다(사용자 결정). 컨셉도 「대기업 신사업 고정」으로 확정됐다.

### 28.1 G5 — 값 부재 인용 (LLM 0회)

`off_slot` **다섯째 겹** `값_부재_인용` 을 붙였다. 채택된 값이 **인용문 안에서 읽히는가**를 본다.

- **문자열 대조가 아니라 값 대조다.** 「1만 원」=10000 · 「38조」=3.8e13 이라
  `str(value_num) in quote` 로는 정상 인용이 통째로 오탐이다. 인용문에서 수를 뽑아
  `parse_number` 로 값을 만들어 비교한다(`_QUOTE_NUM` 이 뒤에 붙은 한국어 배수까지 먹는다).
- **§26.5 가 경고한 단위 환산 함정은 «채널 면제» 로 풀렸다.** 오탐 위험 6건이 전부
  `kosis_api`·`dart_api` 였다 — 환산 산수를 새로 짤 필요가 없었다.
- 자리: `rules/scoring.v1.json` v7→**v8** · `blocks/a_desk.py`(맨 뒤 + `_OFF_SLOT_LAYERS`) ·
  `tools/quote_audit.py`(같은 함수 import — 산식은 한 곳) · `tests/test_step18.py` **23검사**.

**재채점 측정(LLM 0회)** — `--concept data/concept_hmr-solo.json` 기준:

```
p36-n6-01  6/6 → 6/6   ⑤수요 n 2→1   인용지적 0→0
p36-n6-02  6/6 → 6/6   ⑤수요 n 2→2   인용지적 0→0
p36-n6-03  6/6 → 6/6   ⑤수요 n 6→5   인용지적 0→0
pin-09     6/6 → 6/6   ⑤ 41.7% 확정 유지
```

§26.5 의 예측 그대로다 — **「n 을 부풀렸지만 확정 등급은 안 만들었다」.** 과조임 아님 · 오탐 0 ·
목표 명중, 판정 기준 셋을 다 통과했다.

### 28.2 **제품 기본값을 올렸다** — 제품은 6/6 조건으로 돈 적이 없었다

`--search-samples`·`--search-prompt` 는 **run.py 의 CLI 인자**이고, 제품
(`MarketResearchWorker` → `pipeline.py`)은 `rules/adapters.v1.json` 을 그대로 읽는다.
그 파일이 `v1`·표본 **2** 였다 — **판 ㊱ 이 6/6 을 만든 두 레버가 제품에는 하나도 없었다.**

```
search_prompt : v1  → v33-pain      (v1 에는 {claim_type_hint} 자리가 없어 힌트가 버려졌다 — 누락 수리)
search_samples: 2   → 6             (§26.2 의 결정적 레버)
```

⚠ 대가: 옛 원장과 CLI 로 비교하려면 이제 `--search-samples 2 --search-prompt v1` 을 명시한다.
실제 조건은 판마다 `result.json.rules` 에 값째로 남으므로 원장이 언제나 답한다.

### 28.3 제품 경로 3사업안 — **A·B·C**

컨셉 정본은 `data/concept_hmr-solo.json` 계열(대기업 신사업 · 1인가구 프리미엄 냉동 간편식).
프로젝트 3 에 사업안 A·B·C 가 있고 **요약 두께가 다르다** — A 다섯 줄, B·C 한 줄.

| 사업안 | 결과 |
|---|---|
| **B** `2a484d08` | **6/6 · 미확보 0 · 인용지적 0 · collect OK 952초** (총 17분 20초) |
| **C** `633c1b70` | **하네스 게이트 미통과** — 수집을 시작조차 안 했다 |
| **A** `0c54ffb5` | (이 문단 작성 시점 실행 중) |

**B 의 ⑤수요를 채운 문서가 판 ㊱ 이 찾던 그 문서다** — `kostat.go.kr` 통계청 사회조사
「혼자 식사한 비율은 아침 식사한 사람 중 41.7%」. 제품 기본값 교체가 실제로 작동했다는 증거다.

**C 의 실패는 결함이 아니라 방어다.** `harness_failure.json`:

```
slot_id S12 · 힌트 ["편의성 문제","간편함 문제"]
why: "컨셉 본문에 그대로 나오는 힌트가 1개 미만 —
      컨셉에 없는 업종 지식은 상수를 LLM 기억으로 옮긴 것뿐이다"
```

C 의 컨셉 전문이 한 줄이라 관측할 것을 못 뽑는다. 스냅샷도 안 썼다(`failopen-v1`) —
나쁜 설계로 수집하면 그 빈손이 「자료 부재」로 오독되기 때문이다. **유료 80회를 무료로 막았다.**

### 28.4 제품 경로에서 찾은 결함 **7개** — 전부 실측

| # | 증상 | 원인 | 처리 |
|---|---|---|---|
| 1 | 6/6 인데 **미용실 데이터** | 씨앗 없으면 화면이 보낸 견본 라벨로 **조용히 폴백** | `MarketResearchService.startFull` 폴백 제거 → 실패. `TwinSurveyStimulusDraftService` 도 같은 모양이라 같이 |
| 2 | 59초 만에 SUCCEEDED | 같은 컨셉 원장이 있으면 수집 건너뜀(의도) | 원장을 비켜 놓아야 재수집 |
| 3 | 화면에 견본 3개 버튼 | 프론트 `SAMPLE_CONCEPTS` | 제거(시장조사·트윈 조사) |
| 4 | 새 조사 중 **옛 결과가 그대로** | `active` 여도 `ResultBody` 렌더 | 조사 중엔 감춤 |
| 5 | 「입력한 내용을 다시 확인해 주세요」 | `StartRequest.conceptId` 가 `@NotBlank` 인데 이제 null | 제약 제거 |
| 6 | `AI_RESULT_INVALID` 즉시 실패 | 죽은 실행이 `run.jsonl` 만 남겨 `exists()`=True → 수집 건너뜀 | `runpath.complete()` 신설(result.json 을 본다) + `quarantine_partial()` |
| 7 | BM 캔버스가 `beauty-noshow` | 2단계가 **최신 FULL 결과의 conceptId** 를 잇는데 그것이 남의 컨셉 | `startBm` 에서 현재 씨앗과 대조 → 다르면 실패 |

**1 이 제일 위험하다.** 실패보다 나쁜 것은 **남의 자료로 성공했다고 말하는 것**이다 —
화면에 6/6 · SUCCEEDED 가 떴고 사용자가 틀렸다는 것을 알 방법이 없었다.
**6 은 재현 조건이 「실행이 죽었다」라 흔하다** — 하루에 두 번 걸렸고, 한 번 걸리면
그 사업안은 영영 수집을 못 하는 상태로 굳었다.

### 28.5 알아 둘 것

- **조사가 도는 중에 `docker compose up --build` 를 하지 말 것.** 컨테이너가 새로 만들어지면
  돌던 조사가 죽는다(오늘 7분치가 그렇게 날아갔다 — `AI_SERVICE_UNAVAILABLE` 의 정체다).
- **사업안 선택만으로는 시장조사가 안 돈다.** 「7개 검증 가정 확인」 → 「최종 법률·규제 보고서
  확정」 → **「다음 분석 준비」**(`BUILD_HANDOFF`) 까지 가야 `READY_FOR_MARKET` 이 되고 씨앗이 선다.
- **`llmCalls` 는 실제 사용량이 아니다.** `budget.charge(COLLECT_CALLS)` 가 **정액 80** 을 적을
  뿐이다. 표본 6 전 구간은 검색만 54회(웹 슬롯 9 × 6)라 실제는 100회를 넘는다. **비용 보고 금지.**
- **전 구간·표본 6 의 실전 벽시계는 ≈17분**(B 실측: harness 27초 + collect 952초 + 나머지).
  부분수집 10~15분과 다르다.
- **§26.4 의 「순차 루프가 지배항」은 오진이다.** 실측(`p36-l4-01` vs `p36-n6-01`):
  검색 구간 18초→63초, **검색 뒤 캄캄한 구간 192초→520초**, 후보 URL 15→37.
  검색은 전체의 **10%** 다 — 지배항은 발췌이고, 검색 병렬화로 아낄 수 있는 것은 45초(7.6%)뿐이다.
  ⚠ `p36-n6-*` 3판은 **병렬 검색으로 돈 적이 없다**(규칙 스냅샷에 `search_workers` 칸이 없고
  한 슬롯 안 표본 타임스탬프가 11~12초 간격 순차다). 실전 벽시계는 여전히 미측정이다.
- **크롬 확장의 좌표·ref 클릭이 이 화면에서 안 먹었다.** 뷰포트 2561px 인데 스크린샷은
  1568px 로 와서 좌표가 1.63배 어긋나고, ref 는 재렌더로 다른 버튼을 가리켰다(실제로 C 의
  가정 확인을 두 번 눌렀다). **`javascript_tool` 로 DOM 을 직접 누르는 것이 유일하게 확실했다.**

### 28.6 시험

- `tests/test_step18.py` **23검사 0실패** (신설) · research2 파일별 기준선 그대로
- 백엔드 `journey` 패키지 **0실패**(`MarketResearchSeedBranchTests` 3 · `InputFactoryTests` 11).
  전체는 480 중 10 실패인데 **전부 물려받은 것**이다(finance 8 · concept 1 · idea 1 · module 1)
- `ai/tests` 594 통과 / 4 실패 — 실패는 전부 `concept_portfolio_v2` 엔진 쪽이다
- `tests/test_step9.py` 는 여전히 물려받은 `runs/` 경로 지뢰로 깨져 있다
