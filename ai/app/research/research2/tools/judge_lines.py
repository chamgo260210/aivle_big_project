# -*- coding: utf-8 -*-
"""**판단 문장을 기계로 쓴다.** LLM 0회 · 0원. (판 ㊷ 3단계)

    python tools/judge_lines.py runs-generated/p42-gate/publish.json \
           --concept data/concept_hmr-product.json

## 왜 LLM 에 안 맡기나

판 ㊵ 에서 LLM 집필층은 근거 없이 「적정 수준입니다」라고 썼고, 표에 유령 수를 넣었고,
억원↔백만원을 바꿨고, **그런데 검사기를 만점 통과했다.** 문장이 그럴듯하면 검사기가 못 잡는다.
그래서 판단은 기계가 하고, 기계가 못 하는 것은 **안 쓴다.**

## 규칙 셋 — 어기면 문장이 아예 안 나온다

1. **실린 사실만 인용한다** (`게재 != OFF_TOPIC`). 새 사실을 만들지 않는다
2. **비교쌍이 둘 다 있을 때만 쓴다.** 한쪽이 없으면 그 갈래는 통째로 침묵한다
3. **모든 수는 인용 사실의 `number_raw` 이거나 그것들로부터의 산술**이다.
   파생 수는 계산식을 같이 적는다 — 사업가가 손으로 검산할 수 있어야 한다

## 무엇을 답하나

성공 판정 ① **「내 가격이 시장 어디에 서 있나 — 비교 대상·배수, 그리고 어느 쪽으로 팔라」**.
"""
from __future__ import annotations

import argparse, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE):
    sys.path.insert(0, p)

import publish_gate as PG

_NUM = re.compile(r"-?[0-9][0-9,]*(?:\.[0-9]+)?")


def _값(it: dict):
    """`number_raw` 를 수로. 못 읽으면 None — **추측하지 않는다.**"""
    m = _NUM.search(str(it.get("number_raw") or ""))
    if not m:
        return None
    try:
        return float(m.group(0).replace(",", ""))
    except ValueError:
        return None


def _실린(d: dict) -> list:
    out = []
    for r in d["문서별"]:
        for it in r.get("items", []):
            if not it.get("게재") or it["게재"] == "OFF_TOPIC":
                continue
            sec = (it["section"] if it.get("게재_제자리")
                   else "COMPETITOR" if it["게재"] == "COMPETITOR_FIRM" else it["section"])
            out.append({**it, "_절": sec, "_url": r.get("url") or ""})
    return out


def _원(it: dict) -> bool:
    return str(it.get("unit_raw") or "").strip() in ("원", "")


def 가격_판단(실린: list, 정가: float, R: dict, V: dict) -> dict:
    """컨셉 가격이 어디에 서는지. **비교쌍이 없으면 그 갈래는 안 쓴다.**"""
    가격절 = [it for it in 실린 if it["_절"] == "PRICE" and _원(it) and _값(it)]

    # ① 경쟁사 개당단가 — 「같은 물건 한 개」의 값. 절 PRICE + 회사 출처 + 「단가」
    단가 = [it for it in 가격절
            if it["게재"] == "COMPETITOR_FIRM" and "단가" in str(it.get("subject") or "")]
    # ② 편의점 대체 — 컨셉이 problem 에 적은 「저가 편의점 제품」
    편의점 = [it for it in 가격절 if it["게재"] == "SUBSTITUTE"
             and "편의점" in " ".join(str(it.get(k) or "") for k in ("subject", "quote"))
             or (it["게재"] == "SUBSTITUTE" and "도시락" in str(it.get("subject") or ""))]
    # ③ 배달 대체 — 컨셉이 problem 에 적은 「배달은 최소주문금액과 배달비가 붙어」
    배달 = [it for it in 가격절 if it["게재"] == "SUBSTITUTE"
           and "배달" in " ".join(str(it.get(k) or "") for k in ("subject", "quote"))]

    갈래 = []

    if 단가:
        기준 = min(단가, key=_값)            # **가장 싼 것과 견준다** — 가장 불리한 비교다
        v = _값(기준)
        갈래.append({
            "무엇": "같은 진열대의 한 개 값",
            "문장": f"컨셉 가격 {정가:,.0f}원은 {기준['subject']} {기준['number_raw']}원의 "
                   f"{정가 / v:.2f}배다.",
            "계산": f"{정가:,.0f} ÷ {v:,.0f} = {정가 / v:.2f}",
            "근거": [기준],
        })

    if 편의점:
        상한 = max(편의점, key=_값)
        v = _값(상한)
        위 = 정가 > v
        갈래.append({
            "무엇": "편의점으로 대체될 때",
            "문장": f"컨셉 가격 {정가:,.0f}원은 {상한['subject']} {상한['number_raw']}원보다 "
                   f"{'위다' if 위 else '아래다'} ({abs(정가 - v):,.0f}원 {'비싸다' if 위 else '싸다'}).",
            "계산": f"{정가:,.0f} − {v:,.0f} = {정가 - v:,.0f}",
            "근거": [상한],
        })

    # 배달 한 끼는 **음식값 + 배달비**다. 둘 다 있을 때만 쓴다 (규칙 2).
    음식 = [it for it in 배달 if "배달비" not in str(it.get("subject") or "")
           and "주문액" not in str(it.get("subject") or "")]
    배달비 = [it for it in 배달 if "배달비" in str(it.get("subject") or "")]
    if 음식 and 배달비:
        a = min(음식, key=_값)
        b = min(배달비, key=_값)
        한끼 = _값(a) + _값(b)
        갈래.append({
            "무엇": "배달로 대체될 때",
            "문장": f"배달 한 끼는 최소 {한끼:,.0f}원이다({a['subject']} {a['number_raw']}원 + "
                   f"{b['subject']} {b['number_raw']}원). 컨셉 가격 {정가:,.0f}원은 그보다 "
                   f"{'위다' if 정가 > 한끼 else '아래다'}.",
            "계산": f"{_값(a):,.0f} + {_값(b):,.0f} = {한끼:,.0f}",
            "근거": [a, b],
        })
    elif 배달:
        갈래.append({"무엇": "배달로 대체될 때", "문장": None,
                     "왜_못_쓰나": "배달 한 끼는 음식값과 배달비가 **둘 다** 있어야 셈이 된다. "
                                f"지금 실린 것은 {'음식값' if 음식 else '배달비'}뿐이다.",
                     "근거": []})

    # ── 결론은 **조건문 틀**이고, 틀의 어느 가지를 타는지는 위에서 **계산된 부호**가 정한다.
    # ⚠ 결론을 고정 문구로 박으면 그것이 기계 옷을 입은 「적정 수준입니다」다 — 판 ㊵ 의 병이
    #    되돌아온다. 실측으로 걸렸다: 8,900원이 배달 한 끼 8,244원보다 **위**인데도 틀이
    #    「배달 대체면 설 자리가 있다」를 그대로 찍었다.
    편 = next((g for g in 갈래 if g["무엇"] == "편의점으로 대체될 때" and g.get("문장")), None)
    배 = next((g for g in 갈래 if g["무엇"] == "배달로 대체될 때" and g.get("문장")), None)
    결론 = None
    if 편 and 배:
        편위 = 정가 > _값(편["근거"][0])
        배기준 = sum(_값(s) for s in 배["근거"])
        배위 = 정가 > 배기준
        배차 = abs(정가 - 배기준) / 배기준
        근소 = 배차 <= 0.10          # 10% 안이면 「위」라고 잘라 말하지 않는다
        if 편위 and 배위 and 근소:
            결론 = (f"**양쪽 다 위다 — 다만 배달과는 {배차 * 100:.0f}% 차이로 근소하다.** "
                   "편의점을 대체하는 물건이라면 값으로는 설 자리가 좁고, 배달을 대체하는 "
                   "물건이라면 값이 거의 같아 **값이 아닌 이유로 골라야 한다.**")
        elif 편위 and 배위:
            결론 = ("**양쪽 다 위다 — 값으로는 설 자리가 없다.** 값이 아닌 이유"
                   "(정량·조리 시간·보존)가 서지 않으면 이 가격은 지탱되지 않는다.")
        elif 편위 and not 배위:
            결론 = ("**편의점을 대체하는 물건이면 비싸고, 배달을 대체하는 물건이면 설 자리가 있다.**")
        elif not 편위:
            결론 = ("**편의점 값보다도 아래다.** 저가 경쟁에 들어가는 값이라 "
                   "프리미엄이라는 컨셉 서술과 어긋난다 — 둘 중 하나가 틀렸다.")
        결론 += " 어느 쪽으로 팔지는 **이 조사가 정하지 못한다** — 시장 인터뷰에서 물을 것."
    return {"정가": 정가, "갈래": 갈래, "결론": 결론}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    c = json.load(io.open(a.concept, encoding="utf-8"))
    R = PG._rules()
    V = PG._vocab(c, R)

    정가 = ((c.get("_hypotheses_v2") or {}).get("6_수익_가격") or {}).get("제안값_krw_월")
    if not 정가:
        print("컨셉에 가격 제안값이 없다 — **판단을 지어내지 않는다.**")
        return 1

    실린 = _실린(d)
    res = 가격_판단(실린, float(정가), R, V)

    print(f"컨셉 가격 {res['정가']:,.0f}원 — 실린 사실 {len(실린)}건 위에서 잰다\n")
    for g in res["갈래"]:
        print(f"■ {g['무엇']}")
        if g.get("문장"):
            print(f"   {g['문장']}")
            print(f"   계산: {g['계산']}")
            for s in g["근거"]:
                print(f"   근거: {s['number_raw']} {s.get('unit_raw')} «{s['subject']}»  {s['_url'][:64]}")
        else:
            print(f"   (안 쓴다) {g['왜_못_쓰나']}")
        print()
    if res["결론"]:
        print("⇒", res["결론"])
    else:
        print("⇒ (결론 없음) 비교쌍이 갖춰지지 않았다. **지어내지 않는다.**")

    out = a.out or os.path.join(os.path.dirname(a.publish), "judgments.json")
    io.open(out, "w", encoding="utf-8").write(json.dumps(
        {"가격": {**res, "갈래": [{**g, "근거": [{k: s[k] for k in
                                             ("number_raw", "unit_raw", "subject", "quote", "_url")}
                                            for s in g["근거"]]} for g in res["갈래"]]}},
        ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
