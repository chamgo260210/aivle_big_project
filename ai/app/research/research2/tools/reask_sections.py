# -*- coding: utf-8 -*-
"""**절마다 따로 묻기** — 문서 × 절 각각에 「이 절에 관한 것만」을 묻는다. (판 ㊶)

    # 시범 — 문서 10건 × 4절 (≈70원)
    python tools/reask_sections.py 0c54ffb5-... --id p41-pilot --limit 10 \
           --sections PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION

    # 전 구간 (≈950원)
    python tools/reask_sections.py 0c54ffb5-... --id p41-full \
           --sections PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION

    python tools/reask_sections.py ... --dry-run     # 호출 수·비용만 (LLM 0회)

**왜 이렇게 묻나** — 판 ㊵ 실측. 같은 문서·같은 60,000자·같은 모델에서 질문만 바꿨다:

| 질문의 폭 | 결과 |
|---|---|
| 슬롯 1개 (제품 방식) | 문서 141건 중 **120건 `not_found`** |
| 절 7개 메뉴 + 「전부 뽑아라」 | 17건 — **전부 매출액 · 전부 `MARKET_SIZE`** |
| 통짜 12만 자 | 3건 (더 나쁨) |
| **절 1개** | **가격 표 전 품목 ×3개년 — 6,513원 포함** |

**너무 좁으면 못 찾고, 너무 넓으면 한 주제만 긁는다.**

⚠ **폐기된 색인이 아니다.** 색인은 「일부 문서만 열자」였고 이것은 「**모든** 문서를
절마다 다시 묻자」다. **버리는 곳이 없다.**

⚠ **컨셉이 프롬프트에 안 들어간다** (절대규칙 6 — 수집에 가격 가설을 넣으면 자기확인 회로).

산출은 `read_sections.py` 의 `sections.json` **과 같은 모양**이다. 그래야
`publish_gate.py` · `render_sections.py` · `checklist.py` 가 그대로 먹는다.
같은 문서가 절마다 한 번씩 나오므로 `문서별` 은 **(문서 × 절)** 단위다.
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
from base import load_env_key
from runlog import Meter, Run, load_rules
import read_sections as RS
from focus_probe import FOCUS, LABEL

MODEL = "gpt-4o-mini"
WORKERS = 6
JSON_OBJ = re.compile(r"\{.*\}", re.S)
FIELDS = ("quote", "number_raw", "unit_raw", "year", "subject", "table_context")

#: 실측 단가 (`p39-secFULL/result.json`: 112회 · in 949,451 · out 47,793 → 238원)
_IN, _OUT, _KRW = 0.15, 0.60, 1390
_TOK_PER_CHAR = 0.610      # p40-focus 실측 (60,000자 → 36,626 토큰, 프롬프트 포함)
_OUT_PER_CALL = 1667 * 0.6  # p40-focus 실측을 보수적으로


#: 출력 상한을 **명시한다.** 판 ㊶ 2차 시범 실측: 오뚜기 문서의 `PRICE` 응답이
#: `{"status":"found","facts":[{…` 중간에서 **잘렸고**, 통째로 파싱 실패해 **11건이 0건이 됐다.**
#: 상한을 안 주면 모델이 스스로 짧게 끊고, 그 사실이 어디에도 안 남는다.
MAX_OUT = 16384
_OBJ = re.compile(r"\{[^{}]*\}")


def _parse(raw: str) -> tuple:
    """(data, 잘렸나). **잘려도 건질 것은 건진다** — 규칙 5(실패는 값이다)의 연장이다.

    통째로 버리면 「모델이 못 뽑았다」와 「우리가 못 읽었다」가 구별되지 않는다.
    """
    m = JSON_OBJ.search(raw)
    if m:
        try:
            return json.loads(m.group(0)), False
        except Exception:
            pass
    facts = []
    for om in _OBJ.finditer(raw):          # 완성된 객체만 건진다
        try:
            o = json.loads(om.group(0))
        except Exception:
            continue
        if isinstance(o, dict) and o.get("quote"):
            facts.append(o)
    if facts:
        return {"status": "found", "facts": facts}, True
    return {}, bool(raw.strip())


def _one(d: dict, code: str, meter, cap: int) -> dict:
    """문서 하나 × 절 하나. **예외를 올리지 않는다**(규칙 5 — 실패는 값이다)."""
    body = d["text"][:cap]
    out = {"trace_id": d["trace_id"], "url": d["url"], "글자": d["글자"],
           "보낸_글자": len(body), "별칭": d["별칭"], "물은_절": code}
    try:
        r = meter.create("a3_reask", model=MODEL, max_output_tokens=MAX_OUT,
                         input=prompts.render(
                             FOCUS, label=LABEL[code],
                             document=f"[문서] {d['title'] or d['url']}\n{body}"))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "items": []}

    raw = getattr(r, "output_text", "") or ""
    data, 잘림 = _parse(raw)
    out["잘림"] = 잘림
    if (data.get("status") or "") != "found":
        # ⚠ **원문을 남긴다.** 판 ㊶ 1차 시범에서 `not_found` 7건이 나왔는데 사유가 전부
        # 「모델이 형식을 안 지켰다」였고, 그것이 **진짜 없어서인지 파싱 실패인지 가를 수
        # 없었다.** 사유를 못 가르면 다음 판이 엉뚱한 데를 판다(규칙 5 — 실패는 값이다).
        return {**out, "status": "not_found",
                "note": str(data.get("note") or "")[:200],
                "왜": ("모델이 not_found 를 냈다" if data.get("status") == "not_found"
                      else "JSON 을 못 읽었다"),
                "원문": raw[:2000], "items": []}

    hay = RS._norm(body)
    items = []
    for f in data.get("facts") or []:
        if not isinstance(f, dict):
            continue
        it = {k: str(f.get(k) or "") for k in FIELDS}
        # **절은 물어본 절이다.** 모델에게 절을 고르게 하지 않는다 — 그것이 판 ㊵ 의 병이었다.
        it["section"] = code
        it["quote_verified"] = bool(it["quote"]) and RS._norm(it["quote"]) in hay
        it["section_valid"] = True
        it["채택"] = it["quote_verified"]
        it["탈락_사유"] = "" if it["채택"] else "인용이 본문에 없다"
        items.append(it)
    return {**out, "status": "found", "note": "", "items": items}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True, help="새 실행 id (원장을 덮지 않는다)")
    ap.add_argument("--sections", default="PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION",
                    help="다시 물을 절. **이미 넘치는 절은 넣지 않는다**")
    ap.add_argument("--cap", type=int, default=60000)
    ap.add_argument("--limit", type=int, default=0, help="문서 N건만 (시범용)")
    ap.add_argument("--urls", default="",
                    help="URL 조각 쉼표 목록. **시범 전용** — 지표가 실재하는 문서를 겨눈다. "
                         "⚠ 본 실행에 쓰면 그것이 색인이다(폐기된 길). 시범은 측정이지 산출이 아니다")
    ap.add_argument("--expect", default="", help="산출에 이 문자열이 있는지 찍는다 (사전 등록 지표)")
    ap.add_argument("--dry-run", dest="dry", action="store_true",
                    help="호출 수·예상 비용만. **LLM 0회 · 0원**")
    a = ap.parse_args()

    codes = [s.strip().upper() for s in a.sections.split(",") if s.strip()]
    bad = [c for c in codes if c not in LABEL]
    if bad:
        print(f"모르는 절 코드: {bad}  (가능: {', '.join(LABEL)})")
        return 1

    docs = RS._corpus(a.source_run)
    if a.urls:
        want = [u.strip() for u in a.urls.split(",") if u.strip()]
        docs = [d for d in docs if any(u in (d["url"] or "") for u in want)]
        print(f"⚠ --urls 로 {len(docs)}건만 골랐다 (시범 전용)")
    if a.limit:
        docs = docs[:a.limit]
    보낼 = sum(min(d["글자"], a.cap) for d in docs)
    호출 = len(docs) * len(codes)
    tok_in = 보낼 * _TOK_PER_CHAR * len(codes)
    tok_out = _OUT_PER_CALL * 호출
    원 = (tok_in / 1e6 * _IN + tok_out / 1e6 * _OUT) * _KRW
    print(f"문서 {len(docs)}건 × 절 {len(codes)}개 = **{호출}회** · "
          f"보낼 글자 {보낼 * len(codes):,} · 예상 **{원:,.0f}원**")
    print(f"  절: {' · '.join(codes)}")
    if a.dry:
        print("\n--dry-run — 여기서 멈춘다 (LLM 0회 · 0원)")
        return 0

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(a.id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    작업 = [(d, c) for d in docs for c in codes]
    res: list = [None] * len(작업)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_one, d, c, meter, a.cap): i for i, (d, c) in enumerate(작업)}
        done = 0
        for fu in cf.as_completed(futs):
            res[futs[fu]] = fu.result()
            done += 1
            if done % 20 == 0:
                print(f"  … {done}/{len(작업)}")

    items = [it for r in res for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    per = {}
    for it in ok:
        per[it["section"]] = per.get(it["section"], 0) + 1

    out = {"source_run": a.source_run, "run_id": a.id, "cap": a.cap,
           "문서": len(docs), "물은_절": codes,
           "보낸_글자": sum(r["보낸_글자"] for r in res),
           "상태": {s: sum(1 for r in res if r["status"] == s)
                  for s in sorted({r["status"] for r in res})},
           "인용_총": len(items), "인용_채택": len(ok), "절별": per, "문서별": res}
    path = os.path.join(run.dir, "sections.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"\n문서×절 상태 {out['상태']}")
    print(f"사실 {len(items)}건 · 인용 대조 통과 {len(ok)} (떨어짐 {len(items) - len(ok)})")
    print("절별 통과 " + " · ".join(f"{k} {v}" for k, v in sorted(per.items())))
    if a.expect:
        있 = any(a.expect in (it["number_raw"] + it["quote"]) for it in ok)
        print(f"\n**사전 등록 지표 «{a.expect}» — {'나왔다' if 있 else '안 나왔다'}**")
    print(f"기록: {path}")
    m = run.counters
    돈 = m.get("llm.tokens_in", 0) / 1e6 * _IN + m.get("llm.tokens_out", 0) / 1e6 * _OUT
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · in {m.get('llm.tokens_in', 0):,.0f} "
          f"out {m.get('llm.tokens_out', 0):,.0f} · ≈{돈 * _KRW:,.0f}원")
    return 0


if __name__ == "__main__":
    sys.exit(main())
