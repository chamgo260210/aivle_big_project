# -*- coding: utf-8 -*-
"""**대표값 잣대가 «답안지»가 아닌지 기계로 잰다 — 미용실 검사.** (판 ㊹ 4단계)

## 왜 이 검사가 있나

판 ㊸ 이 `data/reference_facts.json` 127건을 잣대로 삼아 「13/127」을 재다가,
**「그 127건은 이 컨셉 보고서 한 편에서 뽑은 답안지」**라는 판정을 받았다.
같은 함정이 이름만 바꿔 돌아오는 자리가 **대표값 잣대**다 — 「6,513원이 절 머리에 섰나」를
재기 시작하면 그 순간 답안지가 된다.

## 판별식

> **잣대의 모든 조항을 다른 컨셉(미용실 예약 SaaS)에 그대로 대입해 말이 되면 통과.
> 한 업종에서만 성립하는 조항이 하나라도 생기면 그것이 답안지다.**

그래서 이 검사는 `headline.물음` 과 `rules/publish.v1.json` 의 표지 목록에
**업종 낱말이 하나라도 있으면 실패**시킨다. 잣대는 「무엇을 묻는가」로만 서야 한다.

⚠ **`ai/tests/` 에 둔다** — `ai/pytest.ini` 가 엔진 폴더를 제외해서, 엔진 안에 두면
CI 가 한 번도 안 돈다.
"""
from __future__ import annotations

import io
import json
import os
import sys

import pytest

_ENGINE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "research", "research2")
for _p in (_ENGINE, os.path.join(_ENGINE, "tools"), os.path.join(_ENGINE, "adapters")):
    if _p not in sys.path:
        sys.path.insert(0, _p)

headline = pytest.importorskip("headline", reason="시장조사 엔진이 없는 환경")

#: **이 컨셉에서만 뜻이 있는 말.** 잣대에 하나라도 들어가면 답안지다.
#: 목록은 지금 원장의 컨셉(냉동 간편식)과 과거 판의 컨셉(미용실·반려동물)에서 뽑았다 —
#: 새 컨셉을 돌릴 때 여기에 그 업종 낱말을 **더한다.** 줄이지 않는다.
업종말 = [
    "냉동", "간편식", "간편", "HMR", "도시락", "식품", "식자재", "급식", "외식",
    "1인 가구", "1인가구", "혼밥", "보존료", "급속냉동", "만두", "즉석",
    "미용실", "헤어", "네일", "예약", "노쇼", "시술",
    "반려동물", "반려", "펫", "사료",
    "필라테스", "요가", "회원권", "가맹", "치킨",
]

#: 잣대에 들어가도 되는 말 — **회계·통계·행정 어휘**다. 업종을 안 탄다.
#: (이 목록은 검사가 쓰지 않는다. 「무엇이 허용인가」를 사람이 읽으라고 적어 둔다.)
_허용_보기 = ["단가", "판매가", "이익률", "마진", "구성비", "점유", "의무", "인증", "규격"]


def _표지들():
    """`headline.물음` 안의 모든 낱말을 (어디, 낱말) 로 편다."""
    for sec, spec in headline.물음.items():
        yield (f"{sec}.묻는_것", spec["묻는_것"])
        for 이름, 낱말들 in (spec.get("가산") or {}).items():
            for w in 낱말들:
                yield (f"{sec}.가산.{이름}", w)
        for w in spec.get("감점") or []:
            yield (f"{sec}.감점", w)
        for w in spec.get("찾는_단위") or []:
            yield (f"{sec}.찾는_단위", w)


def test_대표값_잣대에_업종_낱말이_없다():
    """★ **미용실 검사.** 잣대는 「무엇을 묻는가」로만 서야 한다."""
    걸린 = [(어디, 값, w) for 어디, 값 in _표지들()
           for w in 업종말 if w in str(값)]
    assert not 걸린, (
        "대표값 잣대에 업종 낱말이 들어갔다 — **이것이 답안지다.**\n"
        + "\n".join(f"  {어디}: {값!r} 안에 {w!r}" for 어디, 값, w in 걸린))


def test_규칙_파일의_표지에도_업종_낱말이_없다():
    """`rules/publish.v1.json` 의 요건·절 표지도 같은 잣대를 받는다.

    ⚠ **역할 어휘(`역할_어휘`)와 컨셉 어휘는 여기서 안 본다** — 그것들은 컨셉 파일에서
      뽑거나 이미 그 성격이 다르다. 이 검사가 보는 것은 **판 ㊹ 에서 내가 손으로 적은
      두 목록**뿐이다. 손으로 적은 자리가 답안지가 되기 가장 쉽다.
    """
    p = os.path.join(_ENGINE, "rules", "publish.v1.json")
    R = json.load(io.open(p, encoding="utf-8"))
    걸린 = []
    for w in R.get("요건_표지") or []:
        걸린 += [("요건_표지", w, b) for b in 업종말 if b in str(w)]
    for sec, ws in (R.get("절_표지") or {}).items():
        for w in ws:
            걸린 += [(f"절_표지.{sec}", w, b) for b in 업종말 if b in str(w)]
    assert not 걸린, (
        "규칙 파일의 표지에 업종 낱말이 들어갔다 — **이것이 답안지다.**\n"
        + "\n".join(f"  {어디}: {값!r} 안에 {b!r}" for 어디, 값, b in 걸린))


def test_등급과_카테고리_거리를_잣대로_쓰지_않는다():
    """**방금 죽인 것이 부활하지 않았는지.**

    - **등급**: 이 판의 왕관 사실 「6조 8천억」이 화이트리스트에 없는 도메인이라
      「추정」으로 밀렸다. 그 등급으로 대표값을 고르면 **같은 사실이 같은 이유로 또 밀린다**
    - **카테고리 거리**: 목표 6절 머릿값 「식료품 제조업 영업이익률」은 **산업 전체 값**이라
      「우리 시장에 가까운가」로 고르면 항상 밑에 깔린다 — **게이트의 환생**이다
    """
    import ast, inspect
    tree = ast.parse(inspect.getsource(headline._점수).strip())
    fn = tree.body[0]
    # ⚠ **독스트링과 주석을 빼고 본다.** 「등급을 쓰지 않는다」라고 «적어 둔» 것을
    #   「등급을 쓴다」로 읽으면, 규율을 적을수록 검사가 실패한다.
    if fn.body and isinstance(fn.body[0], ast.Expr) and isinstance(fn.body[0].value, ast.Constant):
        fn.body = fn.body[1:]
    src = ast.unparse(fn)
    for 금지 in ("등급", "kind"):
        assert 금지 not in src, f"대표값 점수에 {금지!r} 가 들어갔다 — 판 ㊹ 이 일부러 뺀 잣대다"


def test_서랍은_버리는_곳이_아니다():
    """**머리에 안 선 것은 전부 서랍에 있어야 한다.** 하나도 사라지지 않는다."""
    cards = [{"_절": "PRICE", "주제": f"값{i}", "_원문값": f"{i}원", "단위": "원",
              "값": float(i), "기간": "2025", "_갈래": "OURS_SEGMENT", "인용": ""}
             for i in range(1, 11)]
    got = headline.build({}, cards)
    본 = len(got["PRICE"]["머리"]) + len(got["PRICE"]["서랍"])
    assert 본 == len(cards), f"카드 {len(cards)}장을 넣었는데 {본}장만 나왔다 — 어딘가로 사라졌다"
    assert len(got["PRICE"]["머리"]) == headline.TOP_N


def test_서랍_라벨은_건수가_아니라_대표값이다():
    """⚠ 「밖 258건」은 **열 이유를 주지 않는다.** 라벨에 값이 있어야 사람이 정한다."""
    cards = [{"_절": "MARKET_SIZE", "주제": f"규모{i}", "_원문값": f"{i}조원", "단위": "원",
              "값": float(i) * 1e12, "기간": "2025", "_갈래": "OURS_SEGMENT", "인용": ""}
             for i in range(1, 8)]
    라벨 = headline.build({}, cards)["MARKET_SIZE"]["서랍_라벨"]
    assert 라벨 and any(ch.isdigit() for ch in 라벨), f"서랍 라벨에 값이 없다: {라벨!r}"
    assert "외" in 라벨, f"서랍 라벨에 나머지 건수가 없다: {라벨!r}"
