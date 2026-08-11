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

#: 자바 `MarketResearchContract.ENVELOPE` 와 같은 집합.
ENVELOPE = {"runId", "conceptId", "asOf", "generatedAt", "mode", "stages", "degradations",
            "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes"}
SUBJECTS = {"MARKET_SIZE", "GROWTH", "COMPETITOR", "PRICE", "DEMAND", "CALCULATION", "NOT_FOUND"}


def canonical(value):
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [canonical(item) for item in value]
    if isinstance(value, dict):
        return {unicodedata.normalize("NFC", k): canonical(v) for k, v in value.items()}
    return value


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

correlation = "smoke-" + uuid.uuid4().hex[:8]
body = {"contractVersion": "1.0", "taskType": "MARKET_RESEARCH", "taskSchemaVersion": "1.0",
        "taskRunId": correlation, "taskAttemptId": "smoke-" + uuid.uuid4().hex[:12],
        "correlationId": correlation,
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(seconds=300))
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
print(f"mode    : {MODE}   source={SEED_RUN}")

request = urllib.request.Request(
    f"{BASE}/internal/v1/ai/executions",
    data=json.dumps(body, ensure_ascii=False).encode(),
    headers={"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}",
             "X-Correlation-Id": correlation},
)
try:
    with urllib.request.urlopen(request, timeout=300) as response:
        status, payload = response.status, json.load(response)
except urllib.error.HTTPError as error:
    status, payload = error.code, json.load(error)

print(f"status  : {status}")
if status != 200:
    print(json.dumps(payload, ensure_ascii=False, indent=2)[:1500])
    raise SystemExit(1)

result = payload["result"]
problems = []

missing, extra = ENVELOPE - set(result), set(result) - ENVELOPE
if missing or extra:
    problems.append(f"봉투 불일치 — 빠짐 {sorted(missing)} · 남음 {sorted(extra)}")

stages = result.get("stages") or []
llm = sum(stage.get("llmCalls", 0) for stage in stages)
print(f"stages  : {[(s['name'], s['status']) for s in stages]}")
print(f"llm     : {llm}회   degradations: {len(result.get('degradations') or [])}")
print(f"evidence: {len(result.get('evidence') or [])}건")

if result.get("mode") == "FULL":
    subjects = {row["subject"] for row in (result.get("scorecard") or [])}
    print("scorecard: " + " · ".join(
        f"{row['subject']}={row['state']}" for row in (result.get("scorecard") or [])))
    if subjects != SUBJECTS:
        problems.append(f"7과목이 아니다: {sorted(subjects)}")
    if not (result.get("market") or {}).get("notFound"):
        problems.append("⑦행(못 찾은 것)이 비었다 — 절대 빼지 않는 칸이다")
    if result.get("canvas") is not None or result.get("bm") is not None:
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
else:
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
