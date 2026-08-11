# 사업안 → 시장분석·BM 배선 인수인계

작성: **2026-08-11** · 브랜치 `market-research-v2` (= `origin/main` `f500258`, PR #39 머지됨)
**착수 전이다. 코드는 한 줄도 안 썼다.** 이 문서는 조사 결과와 결정만 담는다.

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

### 1-6. 계열 A 를 고른 근거 — 성적표

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
| 계열 | **A 한 줄기.** AI 제안 + 사용자 확인, **A 아니면 「지원 준비 중」으로 반려** |
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

1. **컨셉 다리 + 계열 관문** — `ResearchConceptFactory` 신규, `V17__research_series.sql`,
   `pipeline.py` 가 실린 컨셉을 쓰게
2. **수집 배선** — `harness`·`dryrun`·`collect` 3단계 실행, `runs-generated/` 볼륨,
   예산·타임아웃 상향
3. **배선** — `MarketResearchService.startFull` 에 Seed 갈래 + 트윈 시드 조회 수리(§5)
4. **경쟁 씨앗** — `V18__research_competitor_seeds.sql` + 사업안 화면 별도 섹션

---

## 5. 착수 전에 알아야 할 지뢰 — 전부 실측이다

| # | 지뢰 | 근거 |
|---|---|---|
| 1 | **최상위 키를 늘리면 수집이 죽는다.** `Concept(**{언더스코어 아닌 키})` 이고 필드는 정확히 9개. `revenueModel` 같은 이름을 최상위에 넣으면 `TypeError` → **`_bm_plan`(언더스코어) 으로 넣는다** | `run.py:37`, `schema.py:53-64`, `bm_adapter.py:198-208` |
| 2 | **엔진 함수는 dict 가 아니라 파일 경로를 받는다.** 받은 JSON 을 쓸 수 있는 자리에 **파일로 떨궈야** 한다 | `verdict.py:733`, `cards.py:54`, `bm_adapter.py:190` |
| 3 | **`runs/` 는 `:ro` 로 마운트돼 있다.** 이유가 바로 위에 글로 적혀 있다. 새 원장은 별도 볼륨으로 | `compose.yaml:115-124` |
| 4 | **컨셉↔원장 `concept_id` 짝 검사가 새 길에는 없다.** 기존 검사는 `CONCEPTS` 표 3줄만 본다 → **새 검사를 반드시 넣는다** | `test_market_research.py:188` |
| 5 | **가격 파싱은 이미 있다.** `public static Long priceKrw(String)` — 「확실히 못 읽으면 null」. 재구현 금지 | `TwinSurveyStimulusDraftService.java:177-188` |
| 6 | **비교축은 추측 파싱 금지.** 엔진은 `[{축, 우리_값}]` 을 요구하는데 `differentiators.value` 는 한 문장. 못 알면 `[]` 로 두고 `축_부재` 로 나가게 | `verdict.py:652-671` |
| 7 | **`hypotheses` 는 반드시 `[]`.** 채우면 수집 프롬프트로 들어가 자기확인 회로가 된다(절대 규칙 6) | 입구계약서 §1 |
| 8 | **외부 키가 전부 선택값이다.** 없으면 어댑터가 조용히 `not_configured` 로 떨어진다. 유료 수집 전에 확인할 것 | `compose.yaml:105-107` |
| 9 | **예산이 안 맞는다.** 지금 FULL 은 LLM 3회·90~266초, 수집은 LLM ≈80회·3.5분+. `AI_SERVER_LONG_READ_TIMEOUT` 420s / 워커 BUDGET 6분·LEASE 8분 전부 모자란다 | `MarketResearchWorker.java:36-37` |
| 10 | **하네스 게이트가 새 컨셉에서 막힐 수 있다.** 필라테스 실측 **3회 시도 전부 통과 못 함**. 원인은 씨앗이 아니라 DART corp_name 상수 요구(백로그 39) | 입구계약서 §4 H5 |
| 11 | **`run.py` 를 함수로 가르는 작업량을 모른다.** 493줄짜리 `main()`. **2 를 시작할 때 먼저 재고 나서 진행할 것** | `run.py:151-493` |

### 병합이 남긴 구멍 — 3 에서 같이 막는다

`TwinSurveyStimulusDraftService.java:129-132` 가 **레거시 선택 경로**
(`ConceptSelectionRepository.findByProjectIdAndCurrentSelectionTrue…`)로 시드를 찾는다.
**사업안(포트폴리오) 기반 시드를 못 본다** — 조용히 견본 이름표로 떨어진다.
`ProjectModuleStatusService.java:70-75` 가 쓰는
`findByPortfolioSelectionIdAndStaleAtIsNull…` 로 같이 봐야 한다.

---

## 6. 검증

```powershell
cd backend  ; .\gradlew.bat clean test ; .\gradlew.bat postgresTest
cd frontEnd ; npm.cmd run lint; npm.cmd run test:baseline; npm.cmd run build
cd ai       ; python -m pytest -q
```

- 프론트 판정은 `test:run` 이 아니라 **`test:baseline`**
- **물려받은 실패 7건**(ai 4 · 백엔드 2 · 프론트 1)은 병합 전부터 있던 것 —
  **늘어나지만 않으면 된다**. 목록은 §7
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
- 마이그레이션 **V1~V16** 사용 중 → **다음 빈 번호는 V17**
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
