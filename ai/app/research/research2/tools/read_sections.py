# -*- coding: utf-8 -*-
"""**통째 읽기** — 문서마다 「이 문서가 9절 중 무엇을 채우나」를 묻는다. (판 ㊳ 3단계 소판 B)

    python tools/read_sections.py 0c54ffb5-... --id p39-secA
    python tools/read_sections.py 0c54ffb5-... --id smoke --limit 5     # 싸게 확인

**왜 슬롯 경로를 안 쓰나** — 지금 발췌(`web.extract`)는 문서 하나에 **슬롯 하나의 질문**을
던진다. 판 ㊳ 실측: 문서 141건 중 120건이 `not_found`, 읽는 양을 2.1배로 올려도 그 비율이
안 변했고, 값이 몰린 큰 문서 4건은 글자를 3배로 줘도 인용 **0 → 0** 이었다. 그 문서 안에
찾는 수가 **실재함은 `corpus_probe.py` 로 확인됐다.** 남은 설명은 질문 방식뿐이다.

**이 도구는 기존 파이프라인을 건드리지 않는다.** 원장을 읽어 `sections.json` 을 따로 낳는다.
계약·봉투·성적표는 4단계에서 정한다 — 3단계는 **무엇이 나오는지 먼저 본다.**

⚠ **인용 대조를 여기서 한다.** 절 사실이 슬롯 게이트를 우회하면 판 ㉞~㊲ 에서 쌓은
정밀도 장치가 새 경로에 하나도 안 걸린다. 그래서 `quote_verified` 를 이 안에서 매기고,
**떨어진 것도 값으로 남긴다**(규칙 5 — 실패는 값이다).
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, hashlib, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
import runpath
from base import load_env_key
from runlog import Meter, Run, load_rules

MODEL = "gpt-4o-mini"
WORKERS = 6
JSON_OBJ = re.compile(r"\{.*\}", re.S)
#: 인용 대조용 정규화 — 공백과 **문장부호까지만** 접는다.
#: 그 이상(숫자만 맞으면 통과 따위)으로 관대해지면 「대조했다」가 거짓이 된다.
_WS = re.compile(r"\s+")


def _punct() -> str:
    """관용할 문장부호. 값은 `rules/publish.v1.json` 에서 온다 (규약 ①).

    **왜 관대해졌나**: 체크리스트 1-6 「냉동간편식 1조 1,666억」의 본문은
    「…26.2% 증가함」(줄바꿈)이고 모델 인용은 「…26.2% 증가함.」이었다. **마침표 하나로 죽었다.**
    실측: 이 관용으로 18건이 되살아나고, 그중에 이번 판의 왕관 사실이 들어 있다.
    """
    p = os.path.join(ROOT, "rules", "publish.v1.json")
    try:
        return ((json.load(io.open(p, encoding="utf-8")).get("인용_관용") or {})
                .get("문장부호") or "")
    except Exception:
        return ""


_PUNCT = str.maketrans("", "", _punct())


def _norm(s: str) -> str:
    return _WS.sub("", s or "").translate(_PUNCT)


def _corpus(source_run: str) -> list[dict]:
    """원장의 문서를 **내용 기준으로 중복 제거**해 돌려준다.

    같은 URL 이 슬롯마다 다른 `trace_id` 로 저장돼 있어 `trace_id` 로 세면 문서가 부풀고,
    같은 문서를 여러 번 읽어 돈만 는다.
    """
    base = runpath.find(source_run)
    raw = json.load(io.open(os.path.join(base, "a3_bodies.json"), encoding="utf-8"))
    meta = {}
    with io.open(os.path.join(base, "run.jsonl"), encoding="utf-8") as fh:
        for line in fh:
            try:
                r = json.loads(line)
            except Exception:
                continue
            if r.get("node") == "a3_candidate":
                p = r.get("payload") or {}
                if p.get("trace_id"):
                    meta[p["trace_id"]] = (p.get("url") or "", p.get("title") or "")
    seen: dict[str, dict] = {}
    for tid, v in raw.items():
        text = v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)
        if not text.strip():
            continue
        h = hashlib.md5(text.encode("utf-8")).hexdigest()
        if h in seen:
            seen[h]["별칭"].append(tid)
            continue
        url, title = meta.get(tid, ("", ""))
        seen[h] = {"trace_id": tid, "url": url, "title": title,
                   "text": text, "글자": len(text), "별칭": []}
    return sorted(seen.values(), key=lambda d: -d["글자"])


def _refetch_pdfs(docs: list) -> list:
    """PDF 를 다시 받아 **지금의** `pdf_text` 로 본문을 다시 뽑는다. LLM 0회.

    원장의 본문은 옛 추출기(`pg.extract_text()`)가 만든 것이라 **다단 조판이 줄 단위로
    뒤섞여** 있다 — 그 문서에서는 온전한 문장이 존재하지 않아 인용 대조가 구조적으로
    통과할 수 없다(판 ㊳ 실측). **실패는 값이다** — 못 받은 문서는 옛 본문 그대로 두고
    그 사실을 남긴다.
    """
    import requests
    import pdf_text
    cfg = pdf_text.load_pdf_cfg()
    ua = {"User-Agent": "Mozilla/5.0"}
    out = []
    for d in docs:
        u = d["url"] or ""
        if "pdf" not in u.lower():
            out.append({**d, "재추출": "PDF 아님"})
            continue
        try:
            r = requests.get(u, timeout=40, headers=ua)
            if r.status_code != 200 or not pdf_text.is_pdf(r.content, "", cfg):
                out.append({**d, "재추출": f"못 받음(HTTP {r.status_code})"})
                continue
            text, why = pdf_text.extract(r.content, cfg)
            if not text.strip():
                out.append({**d, "재추출": f"본문 없음({why})"})
                continue
            out.append({**d, "text": text, "글자": len(text),
                        "재추출": f"다시 뽑음 {d['글자']:,}→{len(text):,}자"})
        except Exception as e:
            out.append({**d, "재추출": f"실패({type(e).__name__})"})
    for d in out:
        print(f"  [{d.get('재추출')}] {(d['url'] or '')[:78]}")
    return out


class _Doc:
    """`prompts.render_document` 가 보는 최소 모양."""

    def __init__(self, d):
        self.url, self.title, self.text = d["url"], d["title"], d["text"]


def _read_one(d: dict, meter, cap: int, max_items: int) -> dict:
    """문서 하나. **예외를 올리지 않는다** — 하나가 죽어 전체가 죽으면 안 된다(규칙 5)."""
    body = d["text"][:cap]
    out = {"trace_id": d["trace_id"], "url": d["url"], "글자": d["글자"],
           "보낸_글자": len(body), "별칭": d["별칭"]}
    try:
        r = meter.create("a3_sections", model=MODEL,
                         input=prompts.render(
                             prompts.EXTRACT_SECTIONS,
                             sections=prompts._SECTION_MENU,
                             max_items=max_items,
                             document=prompts.render_document(_Doc(d), cap)))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "items": []}

    m = JSON_OBJ.search(getattr(r, "output_text", "") or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    if (data.get("status") or "") != "found":
        return {**out, "status": "not_found",
                "note": str(data.get("note") or "모델이 형식을 안 지켰다")[:200], "items": []}

    hay = _norm(body)
    items, codes = [], set(prompts.SECTION_CODES)
    for f in (data.get("findings") or [])[:max_items]:
        if not isinstance(f, dict):
            continue
        q = str(f.get("quote") or "")
        sec = str(f.get("section") or "").strip().upper()
        it = {k: str(f.get(k) or "") for k in prompts.EXTRACT_ITEM_SECTION_FIELDS}
        it["section"] = sec
        # ── 두 겹. **떨어뜨리되 지우지 않는다** ──────────────────────
        it["quote_verified"] = bool(q) and _norm(q) in hay
        it["section_valid"] = sec in codes
        it["채택"] = it["quote_verified"] and it["section_valid"]
        it["탈락_사유"] = ("" if it["채택"] else
                        ("인용이 본문에 없다" if not it["quote_verified"]
                         else f"절 코드가 아니다({sec or '빈칸'})"))
        items.append(it)
    return {**out, "status": "found", "note": "", "items": items}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True, help="새 실행 id (원장을 덮지 않는다)")
    ap.add_argument("--cap", type=int, default=60000, help="문서당 보낼 글자 상한")
    ap.add_argument("--max-items", dest="max_items", type=int, default=20)
    ap.add_argument("--limit", type=int, default=0, help="문서 N건만 (싼 확인용)")
    ap.add_argument("--only-pdf", dest="only_pdf", action="store_true",
                    help="PDF 문서만. `--pdf-refetch` 효과를 재는 용도")
    ap.add_argument("--pdf-refetch", dest="pdf_refetch", action="store_true",
                    help="PDF 를 다시 받아 **지금의** pdf_text 로 본문을 다시 뽑는다(LLM 0회). "
                         "원장에 저장된 본문은 옛 추출기가 만든 것이라 다단 조판이 뒤섞여 있다")
    a = ap.parse_args()

    docs = _corpus(a.source_run)
    if a.only_pdf:
        docs = [d for d in docs if ".pdf" in (d["url"] or "").lower()
                or "pdf" in (d["url"] or "").lower()]
    if a.pdf_refetch:
        docs = _refetch_pdfs(docs)
    if a.limit:
        docs = docs[:a.limit]
    보낼 = sum(min(d["글자"], a.cap) for d in docs)
    print(f"문서 {len(docs)}건 · 보낼 글자 {보낼:,} · 상한 {a.cap:,}자/문서")

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(a.id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    results: list = [None] * len(docs)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_read_one, d, meter, a.cap, a.max_items): i
                for i, d in enumerate(docs)}
        done = 0
        for fu in cf.as_completed(futs):
            results[futs[fu]] = fu.result()
            done += 1
            if done % 20 == 0:
                print(f"  … {done}/{len(docs)}")

    items = [it for r in results for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    per_sec: dict = {}
    for it in ok:
        per_sec[it["section"]] = per_sec.get(it["section"], 0) + 1

    out = {"source_run": a.source_run, "run_id": a.id, "cap": a.cap,
           "문서": len(docs), "보낸_글자": sum(r["보낸_글자"] for r in results),
           "상태": {s: sum(1 for r in results if r["status"] == s)
                  for s in sorted({r["status"] for r in results})},
           "인용_총": len(items), "인용_채택": len(ok), "절별": per_sec,
           "문서별": results}
    path = os.path.join(run.dir, "sections.json")
    io.open(path, "w", encoding="utf-8").write(
        json.dumps(out, ensure_ascii=False, indent=1))

    print(f"\n문서 상태 {out['상태']}")
    print(f"인용 {len(items)}건 · 대조 통과 {len(ok)}건 "
          f"(떨어짐 {len(items) - len(ok)})")
    print("절별 " + " · ".join(f"{k} {v}" for k, v in sorted(per_sec.items())) or "절별 없음")
    print(f"기록: {path}")
    run.finish() if hasattr(run, "finish") else None
    m = run.counters
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · 토큰 in {m.get('llm.tokens_in', 0):,.0f} "
          f"out {m.get('llm.tokens_out', 0):,.0f} · "
          f"≈ ${m.get('llm.tokens_in', 0) / 1e6 * 0.15 + m.get('llm.tokens_out', 0) / 1e6 * 0.60:.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
