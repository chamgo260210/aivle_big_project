# -*- coding: utf-8 -*-
"""**절 사실 → 근거 카드.** LLM 0회 · 0원. (판 ㊸ 2단계)

    python tools/promote_cards.py runs-generated/p43-wire/publish.json \
           --concept data/concept_hmr-product.json

봉투의 `evidence[]` 는 **슬롯 기반 카드 15장**인데 판 ㊷ 체인이 싣는 사실은 **132건**이고
**둘은 겹치지 않는다**(판 ㊸ 0단계 실측). 승격하지 않으면

- 화면의 채널·원가·수익성·규제 세 과목이 **빈 채로 태어나고**
- 9절 문장이 인용한 수를 사업가가 검산하러 가면 `evidenceById` 에 **없다**

**한글 카드 키로 내보낸다.** 그래야 `serialize.evidence()` 의 번역표 한 곳을 그대로 지나간다 —
계약 키를 여기서 또 쓰면 「같은 물음을 두 곳이 각자 푼다」가 한 번 더 생긴다.

⚠ **등급을 새로 만들지 않는다.** `rules/fill.v2.json` 의 `등급표[kind]` 를 그대로 쓴다.
   상향은 하지 않는다 — 상향은 독립 화자 ≥2 를 요구하는데 이 체인은 화자를 못 센다.
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import publish_gate as PG                                           # noqa: E402
import synthesize as SY                                            # noqa: E402
from a_desk import kind_of                                         # noqa: E402


def _규칙() -> tuple:
    """⚠ `whitelist`·`fill` 은 **버전을 손으로 박지 않는다** — `load_rules()` 가 고른다.
    여기서 `whitelist.v8.json` 이라고 적으면 다음 판이 v9 를 올릴 때 이 자리만 옛 표를 본다."""
    from runlog import load_rules                                  # noqa: PLC0415
    R = load_rules()
    P = json.load(io.open(os.path.join(ROOT, "rules", "promote.v1.json"), encoding="utf-8"))
    return P, R["fill"], R["whitelist"]


def _등급(kind: str, 표: dict) -> str:
    for lv, kinds in 표.items():
        if not lv.startswith("_") and kind in kinds:
            return lv
    return 표.get("_기본") or "추정"


def _채택(it: dict, r: dict, kind: str, 불가: dict) -> str:
    """**채택 4요건**(`rules/fill.v2.json`)을 그대로 검사한다. 통과면 빈 문자열.

    ⚠ 등급표만 가져오고 요건을 안 보면 **「채택 불가」가 「확정」으로 화면에 앉는다.**
    등급표는 요건을 통과한 사실에만 붙는 이름표였고, 승격이 그 전제를 건너뛰고 있었다.

    `채택_불가_부류` 는 **등급을 낮게 주는 것이 아니라 받지 않는다**고 규칙이 적어 뒀다 —
    커뮤니티 추측은 「관측 존재」에서 이미 탈락하므로 낮은 등급으로도 실으면 안 된다.
    """
    if kind in ((불가 or {}).get("kinds") or {}):
        return f"채택 불가 부류({kind})"
    if not (r.get("url") or "").strip():
        return "url 없음"
    if not r.get("조회일"):
        # **백필 금지.** 오늘 날짜를 넣는 것은 지어내기다 — 규칙 파일이 그렇게 적어 뒀다.
        return "retrieved_at 없음"
    if not it.get("quote_verified"):
        return "인용 대조 실패"
    return ""


def _값(number_raw: str, unit_raw: str, 환산: dict) -> tuple:
    """(값, 단위). **화폐만 환산한다** — 모르는 단위는 원문 표기를 그대로 단위로 쓴다."""
    # ⚠ `_수값` 은 못 읽으면 `None` 이 아니라 **`-1`** 을 돌려준다. 그대로 흘리면
    #    「못 읽었다」가 **「마이너스 1원」**이 되어 화면에 값처럼 앉는다.
    n = SY._수값(number_raw)
    u = str(unit_raw or "").strip()
    conv = 환산.get(u)
    if n is None or n < 0:
        return None, (u or None)
    if conv:
        return n * conv["배수"], conv["단위"]
    return n, (u or None)


def build(publish: dict, concept: dict | None = None) -> list:
    """실린 사실 → **한글 카드 목록**. 안 실린 것(`OFF_TOPIC`)은 오지 않는다."""
    P, F, WL = _규칙()
    표, 환산 = F.get("등급표") or {}, P["단위_환산"]
    불가 = F.get("채택_불가_부류") or {}
    갈래경계, 앞머리 = P["갈래_경계"], P["id_앞머리"]

    카드, 거부 = [], []
    for r in publish.get("문서별") or []:
        url = r.get("url") or ""
        kind, 어떻게 = kind_of(url, WL)
        등급 = _등급(kind, 표)
        for it in r.get("items") or []:
            if not it.get("게재") or it["게재"] == "OFF_TOPIC":
                continue
            사유 = _채택(it, r, kind, 불가)
            if 사유:
                # **떨어뜨리되 지우지 않는다**(절대규칙 5 — 실패는 값이다).
                거부.append({"주제": it.get("subject"), "사유": 사유, "url": url})
                continue
            갈래 = it["게재"]
            값, 단위 = _값(it.get("number_raw"), it.get("unit_raw"), 환산)

            # 경계 — **값과 한 몸이다.** 갈래가 말하는 「이 수를 어떻게 읽나」를 옮긴다.
            경계 = []
            문장 = 갈래경계.get(갈래)
            if 문장:
                발 = str(it.get("게재_발행사") or "").strip()
                # ⚠ `**한 회사**` 안쪽을 갈아끼운다. 「한 회사」만 바꾸면 굵게 표시가 겹쳐
                #    `****오뚜기** 한 회사**` 가 된다(실측).
                경계.append(문장.replace("**한 회사**", f"**{발} 한 회사**")
                          if 발 and 갈래 == "COMPETITOR_FIRM" else 문장)
            tc = str(it.get("table_context") or "").strip()
            if tc:
                경계.append(P["표_경계"].replace("{표}", tc))

            카드.append({
                "카드_id": f"{앞머리}{len(카드) + 1:04d}",
                "종류": "관측",
                "계량": str(it.get("subject") or "")[:60],
                "주제": str(it.get("subject") or ""),
                # **연도만 있고 기간이 없다.** 없는 것을 지어내지 않는다.
                "기간": str(it.get("year") or "") or None,
                "값": 값, "단위": 단위,
                "등급": 등급, "등급_근거": f"등급표:{kind}({어떻게}) · 인용 본문 대조 통과",
                "출처_url": url, "kind": kind,
                # ⚠ **오늘 날짜를 적지 않는다.** 원장의 `a3_document.retrieved_at` 을
                #    되찾아 온다 — 지어내는 것(백필)이 아니라 있는 것을 옮기는 것이다.
                #    엔진의 채택 4요건(`fill.v2.json`)이 `retrieved_at` 을 요구하고,
                #    없으면 「채택 불가」다. 없이 「확정」을 붙이면 등급이 거짓이 된다.
                "조회일": r.get("조회일"),
                "인용": it.get("quote") or None,
                "경계": 경계,
                # 승격에서만 붙는 칸 — `serialize._EVIDENCE` 밖이라 봉투로 새지 않는다.
                # ⚠ `it["section"]` 을 그대로 쓰지 않는다 — 게재 판정 뒤의 재배정이 빠져
                #    **보고서와 화면이 같은 사실을 다른 절에 넣는다.** 정본은 `PG.절()`.
                "_절": PG.절(it), "_갈래": 갈래,
                "_발행사": (it.get("게재_발행사") or None),
                "_표키": (f"{r.get('trace_id')}|{tc}|{it.get('year') or ''}" if tc else None),
                "_원문값": f"{it.get('number_raw')}{it.get('unit_raw') or ''}",
            })
    if 거부:
        from collections import Counter                             # noqa: PLC0415
        print("승격 거부 —", " · ".join(f"{k} {v}" for k, v in
                                    Counter(x["사유"] for x in 거부).most_common()))
    return 카드


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", default="")
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    카드 = build(d)

    from collections import Counter
    print(f"승격 {len(카드)}장 (LLM 0회)\n")
    print("등급 —", " · ".join(f"{k} {v}" for k, v in Counter(c["등급"] for c in 카드).most_common()))
    print("출처 —", " · ".join(f"{k} {v}" for k, v in Counter(c["kind"] for c in 카드).most_common()))
    print("절   —", " · ".join(f"{k} {v}" for k, v in Counter(c["_절"] for c in 카드).most_common()))
    없 = sum(1 for c in 카드 if c["값"] is None)
    print(f"\n값을 못 읽은 것 {없}장 (값 null — **지어내지 않는다**)")
    print(f"경계가 붙은 것 {sum(1 for c in 카드 if c['경계'])}장")

    out = a.out or os.path.join(os.path.dirname(a.publish), "promoted.json")
    io.open(out, "w", encoding="utf-8").write(
        json.dumps({"카드": 카드}, ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
