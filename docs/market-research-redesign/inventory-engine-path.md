# 시장분석 엔진 ↔ 제품 경로 — 실행 경로 전수

봉투 `9bf0ecce-0197-4404-abd3-5e9a3992f3cb` (SUCCEEDED, 2026-08-13 12:31:58→12:33:06)
conceptId `0c54ffb5-b7bf-46b0-adc2-be284fed6acb`
원장 `ai/app/research/research2/runs-generated/0c54ffb5-.../result.json`

---

## 경로 지도

```
제품 경로
  React → Spring MarketResearchService.startValidation()
        → BusinessValidationWorker (2초 폴러 · 예산 33분)
        → POST /internal/v1/ai/executions {taskType:"BUSINESS_VALIDATION"}
        → ai/app/api/executions.py:233 → validation.execute_business_validation()
             FULL 한 번 + BM 한 번, 봉투를 합침 (새 엔진 아님 · 오케스트레이션)
        → pipeline.py run_market_research()
             source_run = concept_id,  collect = not runpath.complete(source_run)
        → research2 엔진을 **함수로 직접 import** (별도 프로세스 없음)

CLI 경로
  cd research2 && python run.py --slots ... --formulas ...
        CWD=research2 전제, --concept 기본값은 data/concept.json (공용 작업 파일)
```

**같은 엔진, 다른 호출 방식.** 제품 경로는 컨셉을 인라인으로 실어 보내 임시파일에 쓰거나
원장의 `input.concept` 를 쓴다. CLI 는 `data/concept*.json` 파일 경로를 받는다.
제품 경로는 `_abs()` 로 상대경로를 `RESEARCH_HOME` 기준 절대경로로 강제한다
(`pipeline.py:611-617` — 2026-08-11 에 CWD 차이로 프로덕션 수집이 즉사한 이력, 지금은 수리됨).

---

## 단계별 실제 — 이번 판 13단계

| # | 단계 | 상태 | 초 | LLM |
|---|---|---|---|---|
| 1 | harness | **SKIPPED** | 0 | 0 |
| 2 | dryrun | **SKIPPED** | 0 | 0 |
| 3 | collect | **SKIPPED** | 0 | 0 |
| 4 | verdict | OK | 0 | 0 |
| 5 | cards | OK | 0 | 0 |
| 6 | scorecard | OK | 0 | 0 |
| 7 | summary | **FAILED** | 47 | 3 |
| 8 | restore | OK | 0 | 0 |
| 9 | verdict (재실행) | OK | 0 | 0 |
| 10 | cards (재실행) | OK | 0 | 0 |
| 11 | bm_adapter | OK | 0 | 0 |
| 12 | bm_model | OK | 17 | **1** |
| 13 | scorecard (재실행) | OK | 0 | 0 |

**이번 판의 유료 LLM 호출은 `bm_model` 1회(17초) + 실패한 summary 3회가 전부다.**

`degradations` 4건 원문:
- `NOT_WIRED(harness)` · `(dryrun)` · `(collect)` — 셋 다 같은 문구
  `"저장된 수집(--from a4) 위에서 돈다 — 이 단계는 이번 실행에서 돌지 않았다"`
- `CHECK_FAILED(summary)` — `"검사 미통과 3회 — 요약을 버리고 카드만 낸다(fail-closed)"`

### 「안 돌았다」의 실제 원인 — DB 이력으로 재구성 (추측 아님)

같은 conceptId 로 `MARKET_RESEARCH_FULL` 이 2026-08-12 에 **6회** 실행됐다
(01:11 · 01:26 · 01:35 · 01:51 · 06:02 · **07:36**).
마지막 07:36:53→07:49:24(≈12.5분)이 원장의 `finished_at 2026-08-12T07:48:47` ·
`wall_clock_sec 688.8` 과 일치 — **이것이 실제 유료 수집을 태운 판**이다.

`BUSINESS_VALIDATION` 은 **다음날** 실행됐고(12:19 FAILED → 12:31 SUCCEEDED),
그 시점엔 `runpath.complete(source_run)` 이 참이라 `collect=False` 로 갈렸다.

> **「저장된 수집」은 다른 사업의 원장이 아니라, 같은 프로젝트가 전날 태운 자기 자신의
> 유료 수집이다.** 오염이 아니다.

⚠ 다만 **「이번 판이 실제 수집을 했는가」를 한눈에 보여 주는 필드가 없다.**
`stages[].status="SKIPPED"` + `degradations[].code="NOT_WIRED"` 문자열을 읽어야 안다.

---

## 설계와 실제가 갈리는 곳

### ① 카페 POS 산문은 원장에서 파생된 게 아니라 **규칙 파일의 하드코딩 상수다** ★

```python
# blocks/c_chain.py:437-439
"independent_topdown_blocked": list(
    ((rules.get("consistency") or {}).get("report_notes") or {})
    .get("independent_topdown_blocked") or []),
```

값은 `rules/consistency.v1.json:244-250` 에 **문자열 그대로** 박혀 있다:

> ① 상위권이 비상장이다 — 코케비즈·토스플레이스·쿠팡·스포카·한국결제네트웍스·비바리퍼블리카 6사 모두 DART 상장 0건
> ② 상장 1사(카페24, 042000)는 전사 매출이라 카페 SaaS 시장 매출이 아니다
> ③ … 카페24 거래액 679억·12조, 코케비즈 거래액 100억 …

**컨셉·컨텍스트를 전혀 참조하지 않고 모든 실행에 무조건 복사된다.**
확인: `runs/beauty-13/result.json`(미용실 노쇼)·`runs/pet-treat-15/result.json`(반려동물 간식)
에도 **똑같은 문자열이 각 9회씩** 들어 있다.

> 다른 프로젝트 원장이 섞인 게 아니라, **업종 무관한 하드코딩 예시문이 모든 프로젝트
> 산출물에 무조건 찍히는 구조**다.

### ② TAM = SAM — 두 원인의 겹침

**A. 값이 같은 이유** — 원장 슬롯 `S3`(SAM · 거래액 · "대한민국 수도권")의
`proxy_선언.사유 = "프리미엄 냉동 간편식의 세부 시장 통계 부재로 관측 지역 제한 사용"`.
하네스가 스스로 「수도권 전용 통계가 없어 전국 통계를 대신 쓴다」고 선언했고,
실제로 `C-F002`(SAM 근거)의 `sourceUrl`·`value`(3.804111E13)가 `C-F001`(TAM 근거)과
**완전히 동일**하다 — 같은 KOSIS 전국 통계가 두 슬롯에 중복 채택됐다.
**C-F001·C-F002 가 같은 근거인 이유도 이것이다.**

**B. 라벨이 같은 이유** — `rules/series_unit.v1.json:113-125` 의 `계열_TAM_구조.map.C` 가
계열 C 전체(TAM·SAM 둘 다)에 **단일 스펙 객체**를 쓰고, 그 `"식"` 필드가 문자 그대로
`"TAM(연) = 시장 거래액 × 추정점유율"` 이다.
`service/verdict.py:325-358` `_judge_market_t7()` 이 TAM 계산과 SAM 계산 **양쪽에서 같은
`spec["식"]`** 을 쓴다(338·358행). **별도의 SAM 계산식이 없다.**

### ③ 같은 성장률이 한 봉투 안에 100배 차이로 두 번 나간다

| 자리 | 값 | 단위 |
|---|---|---|
| `market.growth.value` | **15.1464** | `PERCENT_PER_YEAR` |
| `evidence[] id="C-CALC-성장률"` | **0.1514642138369066** | `%` |

둘 다 `materialIds: ["C-F004","C-F003"]` — **같은 근거를 인용하며 같은 성장률을 가리킨다.**

### ④ 경쟁 계량 프롬프트가 SaaS 예시를 식품 제조사에도 그대로 준다

`harness/slot_harness.py:260,393` 이 공시 없는 경쟁사에 대해
「가입 매장 수」·「누적 가입자 수」 같은 web 계량으로 관측하라를 **업종 무관 범용 지시**로 박아 둔다.

실제로 S8(오뚜기)에 `metric:"가입 매장 수"` 가 배정됐고, 수집된 인용문 원문은 —

> **"26개 사업장에서 제조 및 판매를 하고 있습니다."**

원문은 **「사업장」(제조·판매 시설)** 인데 슬롯 라벨은 **「가입 매장 수」**(회원제 매장·가맹점)다.
**의미가 다른 두 개념이 같은 값으로 묶였다.**

### ⑤ 캔버스 `reason`·`missingEvidence` 가 상태 변경 후 갱신되지 않는다

`bm/analyze.py:42-64` 가 출처 라벨이 화이트리스트 밖이면 `content=[]` · `status=UNVERIFIED` ·
`reason="허용된 입력 출처 라벨이 없어 Canvas 내용을 제거했습니다."` 로 세팅한다.
이후 `serialize.py:496-528` `_stamp_user_plan()` 이 계획 칸을 `status="PLAN"` 으로 올리고
`content` 를 사용자 문장으로 채우지만 **`reason`·`missingEvidence` 에는 대입문이 없다.**

실제 봉투:
- `KEY_PARTNERS` — `status:"PLAN"`, `content:["냉동식품 생산 위탁사","저온 물류","대형 이커머스 플랫폼"]`
  인데 `reason:"허용된 입력 출처 라벨이 없어 Canvas 내용을 제거했습니다."`
- `COST_STRUCTURE` — `content:["예산 3,000,000,000원","기간 18개월","인원 12명"]`
  인데 `reason:"비용 구조에 대한 정보가 없다."`

---

## 산출물 실제 내용

**성적표 7과목** — 위 전수 목록(화면 편)과 동일.

**market.tam / market.sam** — 둘 다 `1.1412333E13` · `추정` ·
`formula:"TAM(연) = 시장 거래액 × 추정점유율"`.
요인: `시장 거래액 3.804111E13`(관측 · sourceCount 1 · kosis.kr) × `추정점유율 0.3`(가정 · sourceCount 0)

**market.som** — `null`. notes: "SOM 은 이 파이프라인이 산출하지 않는다 — som:null 은 0 이 아니라 «안 쟀다»다"

**market.price** — `min 2400 · base 3400 · max 5900 · baseKind MEDIAN_PROVISIONAL`

**bm** — `decision REVISION_REQUIRED` · `marketFitStatus PASS` · `consistencyStatus PASS`
`gateReasons`: G1/CHANNELS/UNMAPPED · G4/null/UNMAPPED
`legal: {used:false, status:UNVERIFIED, "법률·규제 결과가 제공되지 않았습니다."}` — 경계 표시 유지됨

**canvas 9칸** — VERIFIED 1(고객 세그먼트) · PARTIAL 2 · UNVERIFIED 1(채널) · PLAN 5
계획 5칸 전부 `caveats:["사용자가 입력한 실행 계획이다 — 관측이 아니다."]`

**notFound** — `empty_slots` 7(S2·S4·S10·S11·S12·S14·S17) · `thin_slots` 3(S7·S8·S9) ·
`unit_mismatch` 2(F_TAM·F_SAM "슬롯 '%' vs 값 '비율'") · `contradictions` 4 ·
`url_filtered` 1(요기요 약관, `terms_policy`) · `extract_capped` 18 · `fetch_empty` 2

**원장 metrics** — `llm.calls 190` · `a3_web_query.rows 72` · `a3_document.rows 182` ·
`a3_finding: found 12 / not_found 5` (슬롯 17개 중)

---

## 사용자에게 도달하는 것 / 안 하는 것

| | |
|---|---|
| 도달 | 성적표 7과목 · market 전체 · evidence 배열(url·quote·grade) · canvas 9칸 · bm.decision·gateReasons · degradations · stages |
| 안 도달 | `summary`(FAILED → null) · `market.som`(산출 자체 없음) · `legal.used=false` 는 화면까지 그대로 옴 |
| 값엔 있는데 화면엔? | `KEY_PARTNERS`·`COST_STRUCTURE` 의 모순된 `reason`/`missingEvidence` — 프론트 렌더 여부는 이번 조사에서 미확인 (화면 전수 목록에 따르면 **안 그린다**) |

백엔드는 `MarketResearchContract.validate()` 로 **필드 존재만 검증**하고 내용을 고치지 않는다.

---

## 확인 못 한 것

1. `_assert_same_concept()`(pipeline.py:396-416)가 이번 판에 실제로 통과했는지 —
   코드 경로상 돌 조건은 맞으나 **통과 여부가 원장·봉투 어디에도 값으로 안 남는다**
2. `d7deb86c-…`(같은 conceptId, 12:19 FAILED)의 실패 사유 — `task_results` 미조회.
   12분 뒤 재시도가 성공했는데 그 사이 무엇이 달랐는지 미확인
3. 견본 `concept_hmr-solo.json` 과 이번 판 인라인 컨셉의 텍스트 diff — 안 함
   (코드상 견본이 이번 판에 영향 줄 경로는 없다고 판단)
4. `search_samples:6` 이 계속 유지돼 온 값인지 특정 실험 커밋에서만 6이었는지 — git blame 안 봄
5. `grade_monotone.py` 등 무료 감사 도구를 이번 판 위에서 돌리지 않음(읽기만 하라는 지시)
