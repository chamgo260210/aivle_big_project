# 사업 검증 — 모델 바꾸는 법 (인수인계)

> 범위는 **여정 3번 사업 검증**(시장조사 + BM + 컨셉 다듬기)뿐이다.
> 마지막 실측 2026-08-15.

---

## 0. 먼저 이것부터 — 30초

```powershell
cd ai
python -m app.tools.model_probe gpt-4o-mini gpt-5.6-luna
```

바꾸려는 모델이 **우리가 실제로 보내는 요청 모양**을 받는지 표로 찍는다.
값은 사실상 0(호출당 한 글자 답). **품질은 안 잰다** — 「받는가」만 본다.

2026-08-15 실측:

```
요청 모양                     gpt-4o-mini   gpt-5.6-luna
structured: 온도만                    OK          400
structured: 추론끔+온도               400           OK
structured: 온도 없이                  OK           OK
interview: 온도1+토큰상한              OK           OK
bm(responses): 온도만                 OK          400
bm(responses): 추론끔+온도            400           OK
research2: web_search 도구            OK           OK
```

---

## 1. 왜 이름만 바꾸면 안 되나 — 지뢰 셋

### ⚠ 지뢰 A — 추론 모델은 온도를 거절한다

`gpt-5.x` 계열은 `temperature` 를 1 로 고정하고 다른 값을 **400** 으로 돌려보낸다.
**단 `reasoning_effort="none"` 을 같이 주면 온도가 돌아온다.**

| | 결과 |
|---|---|
| `effort=none` + `temperature=0.1` | **200** (추론 토큰 0) |
| `effort=low` + `temperature=0.1` | 400 |
| 아무것도 안 줌 | 200 (추론 토큰 41 — **추론이 켜져 돈다**) |

**온도를 그냥 버리면 안 된다.** 실효 온도가 1.0 이 되어 같은 입력에 같은 답이 안 온다
(`research/bm/analyze.py` 실측: 온도를 안 주니 **시도 6회 중 3회** 스키마 실패).

> `app/providers/structured.py` 는 이미 **3단 사다리**로 이걸 처리한다:
> `온도만 → 추론끔+온도 → 온도버리기`, 그리고 **통한 방식만 기억한다.**
> 여기를 지나는 호출은 **손댈 것이 없다.**

### ⚠ 지뢰 B — 요청을 만드는 자리가 셋인데 사다리는 하나뿐이다

| 자리 | 사다리 있나 |
|---|---|
| `app/providers/structured.py` | ✅ 있다 |
| `app/research/bm/analyze.py` | ❌ **없다** — `responses.parse(temperature=…)` 를 직접 부른다 |
| `app/research/research2/**` | ❌ 없다 — 파일마다 모델을 상수로 박아 쓴다 |

### ⚠ 지뢰 C — 값 상수가 모델과 같이 안 움직이면 원가 보고가 거짓이 된다

`read_sections.py` 는 `MODEL` 옆에 `PRICE_IN, PRICE_OUT` 을 둔다. **둘은 반드시 같이
바꾼다.** 안 바꾸면 실행이 스스로 보고하는 원가가 거짓이 되고, `expected.md` 원장이
그 거짓 숫자로 채워진다(판 ㊺ 에서 실제로 잡은 자리).

---

## 1-B. 지금 상태 — 사업 검증은 **루나로 정리 끝** (판 ㊾)

```
AI_MODEL=gpt-5.6-luna          BM_MODEL=gpt-5.6-luna    BM_REASONING_EFFORT=low
```

| 자리 | 모델 |
|---|---|
| 발췌 `read_sections` · 재질문 `reask_sections` | **luna** |
| 수집 발췌 `web.EXTRACT_MODEL` | **luna** |
| 산식 `a_design` · 종합 `synthesize` · 요약 `summary` · 초점 `focus_probe` | **luna** |
| `read_passages` · `write_sections` · `write_report` | **luna** (앞 셋은 `RS.MODEL` 추종) |
| 검색 `web.SEARCH_MODEL` | gpt-5.4-nano (그대로 — 웹 검색 도구용) |
| 하니스 `slot_harness` | **gpt-4o** (그대로 — 아래 §5) |
| BM `bm/analyze.py` | **luna** + `effort=low` |

**인자 차이를 흡수하는 자리는 둘뿐이다.** 새 호출을 만들면 여기서 가져다 쓴다:

| 어디 | 무엇 |
|---|---|
| `research2/runlog.py` → `call_options(model, cap)` | 온도 뺄지 · 상한 4배 열지 |
| `providers/structured.py` → `_MODEL_MODE` 사다리 | 온도 → 추론끔+온도 → 온도버리기 |
| `bm/analyze.py` → `_knobs(model)` | BM 전용(자기 `AsyncOpenAI` 를 쓴다) |

> ⚠ **`responses.parse` 와 HTTP 는 인자 이름이 다르다.**
> SDK 는 `reasoning={"effort": …}`, HTTP 는 평평한 `reasoning_effort=…`.
> 섞어 쓰면 `TypeError` 또는 400 이다.

---

## 2. ~~🔴 BM 이 깨져 있다~~ → 해결됨 (기록으로 남긴다)

`.env` 의 `AI_MODEL` 을 `gpt-5.6-luna` 로 바꾼 순간부터다.

- `bm/analyze.py:28` — `BM_MODEL` → `AI_MODEL` 순으로 고른다. `BM_MODEL` 이 비어 있으면 루나
- `bm/analyze.py:153` — `responses.parse(..., temperature=0.1)` 을 **직접** 넘긴다
- → 루나가 400 (`'temperature' is not supported with this model`)

**둘 중 하나를 고르면 된다.**

### (가) 지금 당장 되살리기 — 코드 0줄 · 30초
```dotenv
# .env
BM_MODEL=gpt-4o-mini
```
BM 만 옛 모델로 묶어 둔다. 나머지는 루나로 간다. **먼저 이걸 하고 시작하길 권한다.**

### (나) BM 도 루나로 — 코드 2줄
`ai/app/research/bm/analyze.py` 의 `_parse_once`:
```python
    response = await api.responses.parse(
        model=model,
        input=messages,
        text_format=BMAnalysisResult,
        temperature=_TEMPERATURE,
        reasoning={"effort": "none"},      # ← 추가. 추론 모델에서 온도를 되찾는 열쇠
    )
```
⚠ **`gpt-4o-mini` 는 이 인자를 거절한다**(`'reasoning.effort' is not supported`).
넣으면 되돌아갈 수 없으니 **모델과 같이 움직여야 하는 줄**이다. `default_model()` 값을
보고 갈라 주는 것이 안전하다.

⚠ `app/research/bm/` 는 **담당자 노트북에서 옮겨온 코드**다. 고치기 전에 담당자와 맞춘다.
`tools/bm_rehearsal/nb_llm.py` 는 「한 글자도 고치지 않는다」고 파일 스스로 적고 있다 —
**거긴 건드리지 말 것.**

---

## 3. 시장조사 엔진 — 파일마다 상수 하나

`ai/app/research/research2/` 는 파일마다 모델을 박아 쓴다. **`.env` 를 바꿔도 안 따라온다.**

### 제품 경로 (사용자가 버튼을 누르면 실제로 도는 것)

| 파일:줄 | 상수 | 지금 | 하는 일 |
|---|---|---|---|
| `tools/read_sections.py:46` | `MODEL` | **gpt-5.6-luna** ✅ | **발췌 — 비용·품질의 지배항** |
| `tools/read_sections.py:50` | `PRICE_IN, PRICE_OUT` | 0.20, 1.20 | ⚠ 위와 **같이** 움직인다 |
| `tools/reask_sections.py:52` | `RS.MODEL` | (자동 추종) | 손댈 것 없음 |
| `adapters/web.py:31` | `SEARCH_MODEL` | gpt-5.4-nano | 웹 검색 |
| `adapters/web.py:32` | `EXTRACT_MODEL` | gpt-4o-mini | 수집 발췌 |
| `blocks/a_design.py:25` | `MODEL` | gpt-4o-mini | 산식 설계 (`run.py` 가 부름) |
| `tools/synthesize.py:28` | `MODEL` | gpt-4o-mini | 종합 |
| `service/summary.py:62` | `SUMMARY_MODEL` | gpt-4o-mini | 요약 |
| `tools/focus_probe.py:31` | `MODEL` | gpt-4o-mini | 재질문 보조 |
| `harness/slot_harness.py:47` | `MODEL` | **gpt-4o** | ⚠ **일부러 4o** — 아래 |
| `tools/preflight.py:71` | 인라인 문자열 | gpt-4o-mini | 기동 점검 ping (무해) |

> ⚠ **`slot_harness.py` 를 mini 로 내리지 마라.** 파일이 이유를 적고 있다 —
> 「1차 초안에서 `gpt-4o-mini` 는 형식 예시를 그대로 베끼거나 통제 어휘를 어겼다(2회 폐기)」.

### 제품 경로가 아닌 것 (도구 — 급하지 않다)

`tools/read_passages.py:52` · `tools/write_sections.py:59` · `tools/write_report.py:54`
— 아무 데서도 import 하지 않는다. 나중에 정리해도 된다.

### 이미 한 번 바꿔 본 근거 (판 ㊺ · `read_sections.py` 주석에 원문 있음)

같은 문서 10건 · 같은 프롬프트 · 읽기 1회:

| 모델 | 뽑음 | 절 머리 | 입력 $/1M | 출력 $/1M |
|---|---|---|---|---|
| gpt-4o-mini | 71 | 30 | 0.15 | 0.60 |
| gpt-4o | 95 | 56 | 2.50 | 10.00 |
| gpt-5.6-terra | 342 | 123 | 2.00 | 12.00 |
| **gpt-5.6-luna** | **350** | **121** | **0.20** | **1.20** ← 채택 |

`mini` 는 「총매출 3조 6,745억」 같은 **큰 수만 긁고 세부를 건너뛴다.**
`luna` 는 terra 성능에 값이 1/10 이고, **절 머리 1건당 원가는 mini 보다도 낮다.**

---

## 4. 한 자리 바꾸는 절차

**한 번에 한 자리만 바꾼다.** 두 자리를 같이 바꾸면 무엇이 들었는지 못 가린다.

1. **받는지 확인** — `python -m app.tools.model_probe <새모델>`
2. **바꾼다** — 상수 1줄. `read_sections` 라면 `PRICE_IN/PRICE_OUT` 도 **같이**
3. **온도를 넘기는 자리인지 본다** — 넘긴다면 `reasoning_effort`(chat) 또는
   `reasoning={"effort":"none"}`(responses)를 같이 넣는다
4. **잰다** — 같은 재료로 전후 1판씩. 판정은 **뽑은 건수 · 절 머리 · 원가** 셋
5. **적는다** — 그 파일 주석의 표에 한 줄 추가. 표가 곧 다음 사람의 근거다

---

## 5. 손대지 않는 것

| | 왜 |
|---|---|
| `app/providers/structured.py` | 3단 사다리가 이미 처리한다 |
| `app/interview/runner.py` | 온도 1.0 이라 원래 문제가 없다 |
| `tools/bm_rehearsal/nb_llm.py` | 「우리 코드가 아니다」 — 파일이 그렇게 적고 있다 |
| `harness/slot_harness.py` 를 mini 로 | 실측으로 2회 폐기된 조합이다 |

---

## 6. 되돌리는 법

전부 상수 1줄 또는 `.env` 1줄이라 **되돌리기가 싸다.** 단 둘만 조심한다.

- **값 상수**(`PRICE_IN/OUT`)를 같이 되돌린다 — 안 그러면 원가 보고가 거짓이 된다
- **녹화**(`ai/replays/`)는 모델명이 열쇠의 일부라 자동으로 갈린다. 지울 필요 없다
