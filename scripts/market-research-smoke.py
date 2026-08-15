"""MARKET_RESEARCH 실스택 스모크 — ai-server 컨테이너 **안에서** 돈다.

ai-server 는 호스트에 포트를 열지 않는다. 그래서 이 파일을 stdin 으로 밀어 넣는다:

    docker compose exec -T ai-server python - < scripts/market-research-smoke.py

기본은 `mode=RESCORE` — **LLM 0회 · 네트워크 0회**라 돈이 들지 않는다.
`SMOKE_MODE=BM` 을 주면 BM 판정 1회(**유료**)까지 태운다.

⚠ **판 ㉝ 에서 다시 썼다.** 옛 판은 옛 러너 계약(`sourceRun`·`fromStage`·`metrics`·`ledger`)을
   보고 있었고 씨앗도 `route12-02` 로 박혀 있었다. 지금 계약은 **봉투**다
   (`runId`·`mode`·`stages`·`scorecard`·`market`·`evidence`). 옛 판을 그대로 돌리면
   `KeyError` 로 죽으면서 **「배선이 깨졌다」로 오진**하게 된다.

⚠ 이 스크립트가 보는 것은 **AI 서버까지**다. 백엔드의 `MarketResearchContract` 왕복은
   여기서 안 본다 — 그건 별건이고, 파이썬 검사가 자바 계약을 대신하지 못한다.
"""

import hashlib
import io
import json
import os
import unicodedata
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

BASE = "http://127.0.0.1:8000"
TOKEN = os.environ["AI_INTERNAL_SERVICE_TOKEN"]
# ⚠ `beauty-13b` 는 이름과 달리 `CPT-CAFE-INV`(카페) 로 기록돼 있어 되짚기가 다른 컨셉을 집는다.
SEED_RUN = os.environ.get("SMOKE_SOURCE_RUN", "beauty-13")
CONCEPT_ID = os.environ.get("SMOKE_CONCEPT_ID", "beauty-noshow")
MODE = os.environ.get("SMOKE_MODE", "RESCORE").upper()
TEXT = "cafe subscription market research smoke"

#: **유료 갈래**(판 ㊸). 실린 컨셉을 보내고 `sourceRun` 을 **안** 보내면 파이프라인이
#: 「표에 없는 이름표 = 제품 사업안」으로 읽어 원장을 새로 만든다(`collect=True`) —
#: 그것이 실제 사용자 경로이자 돈이 드는 자리다. 견본 원장 위 재채점으로는
#: 수집·절 체인·요약이 **한 번도 안 밟힌다**(RESCORE 실측: sections=SKIPPED).
#:
#: ⚠ 입력은 `MarketResearchInputFactory.full()` 을 그대로 베낀다. 예산을 안 실으면
#:   AI 쪽이 `Budget(total=0)` 으로 떨어져 절 체인이 조용히 통째로 degrade 된다.
CONCEPT_FILE = os.environ.get("SMOKE_CONCEPT_FILE", "")
LLM_BUDGET = int(os.environ.get("SMOKE_LLM_BUDGET", "270"))
TIMEOUT = int(os.environ.get("SMOKE_TIMEOUT", "300"))

#: 자바 `MarketResearchContract.ENVELOPE` 와 같은 집합.
#: ⚠ 판 ㊸ 에서 **세 칸이 늘었다**(2·8·9절). 이 상수를 안 따라 고치면 스모크가
#:   「봉투 불일치」로 **거짓 실패**하고, 배선이 깨진 것으로 오진하게 된다.
ENVELOPE = {"runId", "conceptId", "asOf", "generatedAt", "mode", "stages", "degradations",
            "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes",
            "judgment", "prescriptions", "synthesis"}
#: 판 ㊸ 에서 7 → 10 과목. 뒤 셋은 절 체인이 채운다(`serialize._SECTION_SUBJECT`).
SUBJECTS = {"MARKET_SIZE", "GROWTH", "COMPETITOR", "PRICE", "DEMAND", "CALCULATION", "NOT_FOUND",
            "CHANNEL", "UNIT_ECONOMICS", "REGULATION"}


def canonical(value):
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [canonical(item) for item in value]
    if isinstance(value, dict):
        return {unicodedata.normalize("NFC", k): canonical(v) for k, v in value.items()}
    return value


if CONCEPT_FILE:
    # 실린 컨셉이 **이름표를 이긴다**(`_inline_concept`). 컨셉을 안 실으면 표에 없는
    # 이름표는 컨셉 파일을 못 찾는다.
    TEXT = io.open(CONCEPT_FILE, encoding="utf-8").read()

digest = "sha256:" + hashlib.sha256(TEXT.encode()).hexdigest()
task_input = {
    "textContents": [{"contentKey": "concept", "contentType": "TEXT", "language": "ko-KR",
                      "totalCharacters": len(TEXT), "contentHash": digest,
                      "chunks": [{"index": 0, "text": TEXT, "characterCount": len(TEXT),
                                  "chunkHash": digest}]}],
    "mode": MODE,
    "sourceRun": SEED_RUN,
    "conceptId": CONCEPT_ID,
}
if CONCEPT_FILE:
    # ⚠ **`sourceRun` 을 지운다.** 남겨 두면 그 씨앗 원장 위 재채점이 되어 수집이 안 돈다.
    task_input.pop("sourceRun")
    task_input["llmBudget"] = LLM_BUDGET
if MODE == "BM":
    task_input["llmBudget"] = 2
    # 사용자가 앞 화면에서 채우는 실행 계획. 여기서 같이 태워야 **배선이 실제로 도는지**를
    # 본다 — 층마다 따로 통과해도 사이가 끊기면 캔버스는 그대로 빈다.
    # ⚠ 값은 정수여야 한다(canonical hash 가 부동소수점을 거부한다).
    if os.environ.get("SMOKE_PLAN", "1") != "0":
        task_input["planMaterial"] = {
            "key_partners": ["스모크 — 결제 처리 대행"],
            "customer_relationship": "스모크 — 예약 확인 자동 발송으로 접점 유지",
        }
        task_input["executionConstraints"] = {"budget_krw": 7000000, "months": 6, "team": 3}

if MODE == "VALIDATION":
    # **사용자가 실제로 누르는 것.** 「사업 검증」 버튼은 시장조사(FULL)와 BM 을 한 실행으로
    # 잇는다(`MarketResearchService.startValidation` → `TaskType.BUSINESS_VALIDATION`).
    # ⚠ 봉투를 합치는 `ai/app/validation/runner.py::_merge` 는 **테스트 0 · 실행 0** 이라,
    #   FULL 봉투가 통과한다는 사실이 VALIDATION 봉투가 통과한다는 뜻이 아니다.
    task_input["llmBudget"] = LLM_BUDGET
    if os.environ.get("SMOKE_PLAN", "1") != "0":
        task_input["planMaterial"] = {
            "key_partners": ["스모크 — 냉장 물류 위탁"],
            "customer_relationship": "스모크 — 정기 배송 알림으로 접점 유지",
        }
        task_input["executionConstraints"] = {"budget_krw": 7000000, "months": 6, "team": 3}

correlation = "smoke-" + uuid.uuid4().hex[:8]
body = {"contractVersion": "1.0",
        "taskType": "BUSINESS_VALIDATION" if MODE == "VALIDATION" else "MARKET_RESEARCH",
        "taskSchemaVersion": "1.0",
        "taskRunId": correlation, "taskAttemptId": "smoke-" + uuid.uuid4().hex[:12],
        "correlationId": correlation,
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(seconds=TIMEOUT))
        .isoformat(timespec="seconds").replace("+00:00", "Z"),
        "locale": "ko-KR", "input": task_input}
subset = {key: body[key] for key in
          ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")}
body["canonicalInputHash"] = "sha256:" + hashlib.sha256(
    json.dumps(canonical(subset), ensure_ascii=False, sort_keys=True,
               separators=(",", ":")).encode()).hexdigest()

home = os.environ.get("RESEARCH2_HOME", "/app/app/research/research2")
print(f"home    : {home} exists={os.path.isdir(home)} "
      f"seed={os.path.isdir(os.path.join(home, 'runs', SEED_RUN))}")
print(f"mode    : {MODE}   source={task_input.get('sourceRun') or '(수집한다 — 유료)'}"
      f"   concept={CONCEPT_FILE or CONCEPT_ID}   llmBudget={task_input.get('llmBudget')}")

request = urllib.request.Request(
    f"{BASE}/internal/v1/ai/executions",
    data=json.dumps(body, ensure_ascii=False).encode(),
    headers={"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}",
             "X-Correlation-Id": correlation},
)
try:
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        status, payload = response.status, json.load(response)
except urllib.error.HTTPError as error:
    status, payload = error.code, json.load(error)

print(f"status  : {status}")
if status != 200:
    print(json.dumps(payload, ensure_ascii=False, indent=2)[:1500])
    raise SystemExit(1)

result = payload["result"]
problems = []

# 유료 실행의 봉투는 **남긴다.** 자바 계약(`MarketResearchContract`) 왕복은 이 파이썬이
# 대신하지 못하는데, 봉투를 버리면 그것을 재려고 **또 사야 한다**.
if os.environ.get("SMOKE_OUT"):
    io.open(os.environ["SMOKE_OUT"], "w", encoding="utf-8").write(
        json.dumps(result, ensure_ascii=False, indent=1))
    print(f"봉투    : {os.environ['SMOKE_OUT']} 에 남겼다")

missing, extra = ENVELOPE - set(result), set(result) - ENVELOPE
if missing or extra:
    problems.append(f"봉투 불일치 — 빠짐 {sorted(missing)} · 남음 {sorted(extra)}")

stages = result.get("stages") or []
llm = sum(stage.get("llmCalls", 0) for stage in stages)
print(f"stages  : {[(s['name'], s['status']) for s in stages]}")
print(f"llm     : {llm}회   degradations: {len(result.get('degradations') or [])}")
print(f"evidence: {len(result.get('evidence') or [])}건")

# ⚠ VALIDATION 은 **둘 다** 본다 — FULL 봉투와 BM 봉투를 합친 것이라
#   성적표·절 체인도, 캔버스도 같이 서야 한다. 여기서 FULL 만 보면 사용자가 받는
#   모드의 절반이 검사 밖에 남는다.
if result.get("mode") in ("FULL", "VALIDATION"):
    subjects = {row["subject"] for row in (result.get("scorecard") or [])}
    print("scorecard: " + " · ".join(
        f"{row['subject']}={row['state']}" for row in (result.get("scorecard") or [])))
    if subjects != SUBJECTS:
        problems.append(f"10과목이 아니다 — 빠짐 {sorted(SUBJECTS - subjects)} · "
                        f"남음 {sorted(subjects - SUBJECTS)}")
    if not (result.get("market") or {}).get("notFound"):
        problems.append("⑦행(못 찾은 것)이 비었다 — 절대 빼지 않는 칸이다")
    if result.get("mode") == "FULL" and (result.get("canvas") is not None
                                         or result.get("bm") is not None):
        problems.append("FULL 인데 canvas·bm 이 null 이 아니다")

    # ── 요인 원장 — 계산식의 항이 «값으로» 나왔는지 ─────────────────────
    #   여기서 봐야 하는 이유: 파이썬 검사는 「골든 픽스처와 키 집합이 같다」까지만 보고,
    #   실제 원장 위에서 요인이 정말 서는지는 실스택에서만 드러난다.
    seen, prose = 0, 0
    for name in ("tam", "sam", "som", "growth"):
        figure = (result.get("market") or {}).get(name)
        if not figure:
            continue
        factors = figure.get("factors")
        if not isinstance(factors, list):
            problems.append(f"market.{name}.factors 가 배열이 아니다")
            continue
        seen += len(factors)
        for factor in factors:
            if factor.get("basis") not in ("관측", "가정", "가설"):
                problems.append(f"{name}: 요인 판정이 어휘 밖 — {factor.get('basis')!r}")
            if factor.get("basis") == "관측" and not factor.get("sourceCount"):
                problems.append(f"{name}/{factor.get('name')}: 관측인데 출처 0곳")
            # 잘린 문장은 «…» 없이 문장 한가운데서 끝난다. 길이로만 재면 못 잡으니
            # 규칙 파일의 서술과 대조하는 건 파이썬 검사에 맡기고, 여기서는 표가
            # **서는지**만 본다.
        prose += len(figure.get("assumptions") or [])
    print(f"요인    : {seen}줄   표 밖 해석 경계 {prose}문장")
    if not seen:
        problems.append("요인이 0줄이다 — 계산식의 항이 표로 서지 않았다")

    # ── 판 ㊸ 출시 차단 목록 (`expected.md` §34) ──────────────────────
    #   ⚠ 여기는 **모양**이 아니라 **내용**을 본다. 앞의 검사가 전부 통과해도
    #   「돌긴 도는데 사람이 읽을 것이 없다」가 그대로 통과하던 자리다.
    by_stage = {s["name"]: s for s in stages}
    sec = by_stage.get("sections")
    print(f"절체인  : {sec}")
    codes = [d.get("code") for d in (result.get("degradations") or [])]
    print(f"덜 된 것: {codes or '없음'}")

    # ⚠ **재채점에서는 이 목록을 재지 않는다.** RESCORE 는 LLM 0회라 절 체인이 설계대로
    #   SKIPPED 다. 그것을 FAIL 로 세면 「검사가 빨간데 코드는 멀쩡한」 거짓 경보가 되고,
    #   그 다음부터 아무도 이 스모크를 안 믿는다. 차단 목록은 **유료 실행의 잣대**다.
    if "MODE_RESCORE" in codes:
        print("§34    : 재채점이라 재지 않는다 — 차단 목록은 유료 FULL 에서만 선다")
        for line in problems:
            print(f"FAIL: {line}")
        raise SystemExit(1 if problems else 0)

    # ② 예산 270 에서 절 체인이 실제로 도는가. **없는 단계**와 **실패한 단계**를 가른다.
    if sec is None:
        problems.append("sections 단계가 아예 없다 — 절 체인이 제품 경로에서 안 돈다")
    elif sec.get("status") not in ("OK", "SUCCEEDED", "DEGRADED"):
        problems.append(f"sections 단계가 {sec.get('status')} 다")
    if "BUDGET_EXHAUSTED" in codes:
        problems.append("BUDGET_EXHAUSTED — 예산 270 이 모자랐다")

    # 2·8·9절이 **실제로 찼는가.** null 이면 화면에 그 자리가 아예 안 선다.
    for name in ("judgment", "prescriptions", "synthesis"):
        block = result.get(name)
        n = len(block) if isinstance(block, list) else (0 if block is None else 1)
        print(f"{name:13s}: {'없음' if not n else f'{n}건'}")
        if not n:
            problems.append(f"{name} 이 비었다 — 이 판이 이으려던 절이다")

    # ③ ★ 원가·수익성 거짓 「확인됨」 — 지금 화면에서 **유일하게 틀린 확신을 주는 줄**이다.
    #    건수만 보는 `_section_rows` 가 「한 개 팔면 얼마 남나」에 0건인 채로 FILLED 를 준다.
    ue = next((r for r in (result.get("scorecard") or [])
               if r["subject"] == "UNIT_ECONOMICS"), None)
    원가 = [e for e in (result.get("evidence") or [])
            if e.get("section") == "UNIT_ECONOMICS"
            and any(w in (e.get("metric") or "") + (e.get("subject") or "")
                    for w in ("원가", "마진", "매출총이익", "제조원가", "단위당", "기여이익"))]
    print(f"원가    : UNIT_ECONOMICS={ue and ue.get('state')} · 원가에 닿는 사실 {len(원가)}건")
    if ue and ue.get("state") == "FILLED" and not 원가:
        problems.append("UNIT_ECONOMICS 가 「확인됨」인데 원가에 닿는 사실이 0건이다 "
                        "— 거짓 확신. 출시 차단(§34-3)")

    # ④ 9절이 연도를 **나를 수 있는가**(화면 표기의 원천).
    #
    # ⚠ **「연도가 있어야 한다」로 재지 않는다.** 연도가 있는지는 **자료가 정하는 것**이지
    #   배선이 정하는 것이 아니다. 실측(2026-08-15): 규칙을 고쳐 상위 범주 근거가 빠지자
    #   살아남은 근거(배달비 설문)에 원래 연도가 없어 이 검사가 빨개졌다 — **고친 것이
    #   맞는데 검사가 틀렸다.** 화면은 그때 「연도 없음」이라고 적는다(`MarketResultBody`).
    #   그러니 재야 하는 것은 **칸이 붙어 오는가**이지 값이 찼는가가 아니다.
    if isinstance(result.get("synthesis"), list):
        출처 = [s for line in result["synthesis"] for s in (line.get("sources") or [])]
        해 = sum(1 for s in 출처 if s.get("period"))
        print(f"9절 연도: 출처 {len(출처)}개 중 {해}개에 연도가 있다"
              f"{' (없는 것은 화면이 「연도 없음」으로 적는다)' if 해 < len(출처) else ''}")
        if 출처 and not all("period" in s for s in 출처):
            problems.append("9절 출처에 `period` 칸 자체가 없다 — 화면이 연도를 못 쓴다")
if result.get("mode") in ("BM", "VALIDATION"):
    cells = ((result.get("canvas") or {}).get("cells")) or []
    print(f"canvas  : {len(cells)}칸   decision={(result.get('bm') or {}).get('decision')}")
    if len(cells) != 9:
        problems.append(f"캔버스가 9칸이 아니다: {len(cells)}")

    # ── 사용자 실행 계획이 칸까지 갔는가 ─────────────────────────────
    #   자바·파이썬 단위 검사는 각 층만 본다. 「요청에 실은 문장이 캔버스 칸에
    #   글자 그대로 섰는가」는 실스택에서만 드러난다.
    plan = task_input.get("planMaterial") or {}
    if plan:
        by_cell = {cell["canvasCell"]: cell for cell in cells}
        want = {"KEY_PARTNERS": plan.get("key_partners", [None])[0],
                "CUSTOMER_RELATIONSHIPS": plan.get("customer_relationship")}
        for name, text in want.items():
            cell = by_cell.get(name) or {}
            body_text = " ".join(cell.get("content") or [])
            mark = "✔" if text and text in body_text else "✘"
            print(f"계획    : {mark} {name} status={cell.get('status')} "
                  f"labels={cell.get('sourceLabels')} content={cell.get('content')}")
            if not text or text not in body_text:
                problems.append(f"{name}: 사용자가 쓴 문장이 칸에 없다")
                continue
            # 사용자가 쓴 칸은 **계획**이다. 근거를 인용하지 않았는데 VERIFIED 면
            # 「꽉 찬 캔버스」가 「검증된 캔버스」로 읽힌다.
            if not cell.get("marketEvidenceIds") and cell.get("status") != "PLAN":
                problems.append(f"{name}: 근거 0인데 status={cell.get('status')} — PLAN 이어야 한다")
            if not any("관측이 아니다" in line for line in (cell.get("caveats") or [])):
                problems.append(f"{name}: 「관측이 아니다」 경계가 칸에 없다")
        cost = by_cell.get("COST_STRUCTURE") or {}
        print(f"비용    : status={cost.get('status')} content={cost.get('content')}")

# ── 경계 불변식 — 이 프로젝트의 대표 검사 ─────────────────────────────
by_id = {item["id"]: item for item in (result.get("evidence") or [])}
carried = 0
for cell in ((result.get("canvas") or {}).get("cells") or []):
    want = {c for ref in cell["marketEvidenceIds"] for c in by_id.get(ref, {}).get("caveats", [])}
    carried += len(want)
    if not want <= set(cell["caveats"]):
        problems.append(f"{cell['canvasCell']}: 인용한 근거의 경계가 칸에 없다")
available = sum(len(item.get("caveats") or []) for item in by_id.values())
print(f"경계    : 칸으로 도달한 문장 {carried}개 (원장 근거가 든 경계 {available}개)")

# ⚠ **0 은 「지켰다」가 아니라 「못 쟀다」일 수 있다.** 원장에 경계가 있는데 이번 실행의
#   칸이 그 근거를 하나도 인용하지 않으면 위 불변식은 **공허하게 통과**한다 — 실측(판 ㉝):
#   원장에 경계 2건이 있는데 BM 모델이 그 카드를 안 골라 `carried=0` 인 채 OK 가 찍혔다.
#   이 프로젝트가 반복해 당한 「검사가 공허했다」(판 ㉛·㉜-b)와 같은 모양이라 초록으로 두지 않는다.
if result.get("mode") == "BM" and available and not carried:
    problems.append("경계 검사가 공허하게 통과했다 — 칸이 경계를 가진 근거를 하나도 "
                    f"인용하지 않았다(원장 경계 {available}개). 도달을 **확인하지 못했다**")

if MODE == "RESCORE" and llm != 0:
    problems.append(f"재채점인데 LLM 을 {llm}회 불렀다")

if problems:
    for line in problems:
        print(f"FAIL: {line}")
    raise SystemExit(1)
print(f"OK - {MODE} 봉투가 계약 모양으로 나왔다 (LLM {llm}회)")
