# -*- coding: utf-8 -*-
"""**규칙이 한 사업 아이템에 맞춰져 있지 않은가.** (판 ㊸ 보완)

## 왜 이 검사가 필요한가

이 저장소가 반복해서 밟은 함정이 **「답안지 규칙」**이다 — 컨셉 하나(냉동 간편식)를 보며
규칙을 조율해 놓고 그것을 일반 규칙이라고 믿는 것. 그러면 다른 사업 아이템에서 조용히
빗나가고, 그것을 알아내려면 **아이템마다 유료로 다시 재야 한다**
(실측: 아이템당 문서 130건 ≈ LLM 130회 ≈ 800원).

> **규칙에 업종어가 없으면 다른 아이템에서 다시 잴 이유도 없다.**

성장률·가격·수요는 업종을 안 가리고 같은 방식으로 재는 항목이다. 그러니 규칙을
**이 엔진의 분류**(절 · 게재 갈래 · 등급 · 단위)로만 적으면 일반성은 구조적으로 보장된다.
사람의 규율로 지키던 것을 검사로 옮긴다.

## 무엇을 재나

`data/concept_*.json` 열한 개의 **`name`**(사업의 이름)에서 낱말을 뽑아, **두 개 이하의
컨셉에만 나오는 낱말**을 「업종어」로 본다. 「관리」·「대상」처럼 여러 사업에 걸치는 말은
업종어가 아니다.

`name` 만 보는 이유는 **거짓 경보를 없애기 위해서**다. 컨셉 본문 전체를 훑으면
「단순」·「다른」·「관측을」 같은 평범한 말이 걸려 검사가 못 쓰게 된다(실측).

## 무엇을 안 재나

`_` 로 시작하는 키의 값은 **건너뛴다.** 거기에는 「실측: 냉동 간편식에서 이런 일이
있었다」처럼 **왜 그 규칙을 만들었는지**를 적어야 하고, 그 기록은 지우면 안 된다.

⚠ **이 예외가 이 검사의 구멍이다.** 규칙을 `_설명` 키에 숨기면 안 걸린다.
다만 그 키는 코드가 읽지 않으므로 **동작에는 영향이 없다.**
"""
from __future__ import annotations

import glob
import io
import json
import os
import re

import pytest

_R2 = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                   "..", "app", "research", "research2"))
_낱말 = re.compile(r"[가-힣]{2,}")

#: 검사 대상 — **기계가 읽는 규칙만** 본다.
_규칙_파일 = ["rules/*.json", "harness/vocab.json"]

#: 몇 개 컨셉 이름까지 나오면 「업종어」로 볼 것인가.
_업종어_상한 = 2


def _업종어() -> set:
    표: dict[str, set] = {}
    for path in glob.glob(os.path.join(_R2, "data", "concept_*.json")):
        d = json.load(io.open(path, encoding="utf-8"))
        for w in set(_낱말.findall(str(d.get("name") or ""))):
            표.setdefault(w, set()).add(os.path.basename(path))
    return {w for w, c in 표.items() if len(c) <= _업종어_상한}


def _기계가_읽는_값(node, out: list) -> None:
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(k, str) and k.startswith("_"):
                continue                      # 「왜 이 규칙인가」를 적는 자리
            if isinstance(k, str):
                out.append(k)
            _기계가_읽는_값(v, out)
    elif isinstance(node, list):
        for v in node:
            _기계가_읽는_값(v, out)
    elif isinstance(node, str):
        out.append(node)


def _대상() -> list:
    파일: list = []
    for pat in _규칙_파일:
        파일 += sorted(glob.glob(os.path.join(_R2, pat)))
    return 파일


def test_감사할_규칙_파일이_실제로_있다():
    """⚠ 경로가 바뀌면 이 검사가 **0개를 통과**시키며 초록이 된다. 공허한 통과를 막는다."""
    assert len(_대상()) >= 3, f"규칙 파일을 못 찾았다 — 경로가 바뀐 것 같다: {_대상()}"


def test_업종어_목록이_비어_있지_않다():
    """컨셉 파일 모양이 바뀌면 목록이 비고, 그러면 아래 검사가 **공허하게 통과**한다."""
    말 = _업종어()
    assert len(말) >= 10, f"업종어를 못 뽑았다 — 컨셉 `name` 을 못 읽는 것 같다: {sorted(말)}"


#: **이미 있는 부채.** 한 번에 다 갚을 수 없으니 **늘지 않게** 막고 갚아 나간다 —
#: 프론트의 `test-debt-baseline.json` 과 같은 방식이다. 목록은 **줄어들기만 한다.**
_기준선 = json.load(io.open(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "rules_business_terms_baseline.json"), encoding="utf-8"))["파일별"]


def test_기준선에_적힌_부채가_실제로_남아_있다():
    """**갚은 것은 목록에서 지운다.** 안 지우면 다음 사람이 「아직 있다」고 착각한다."""
    업종어, 유령 = _업종어(), {}
    for path in _대상():
        허용 = set(_기준선.get(os.path.basename(path)) or [])
        if not 허용:
            continue
        말들: list = []
        _기계가_읽는_값(json.load(io.open(path, encoding="utf-8")), 말들)
        있는 = {w for 조각 in 말들 for w in _낱말.findall(조각) if w in 업종어}
        if 허용 - 있는:
            유령[os.path.basename(path)] = sorted(허용 - 있는)
    assert not 유령, f"이미 갚은 부채가 목록에 남아 있다 — 지워라: {유령}"


@pytest.mark.parametrize("path", _대상(), ids=lambda p: os.path.basename(p))
def test_규칙에_한_사업_아이템의_업종어가_없다(path):
    업종어 = _업종어()
    말들: list = []
    _기계가_읽는_값(json.load(io.open(path, encoding="utf-8")), 말들)

    허용 = set(_기준선.get(os.path.basename(path)) or [])
    걸린 = sorted({w for 조각 in 말들 for w in _낱말.findall(조각)
                  if w in 업종어 and w not in 허용})
    assert not 걸린, (
        f"{os.path.basename(path)} 에 **업종어**가 있다: {걸린}\n"
        "이것이 「답안지 규칙」이다 — 다른 사업 아이템에서 조용히 빗나가고, "
        "그것을 알려면 아이템마다 유료로 다시 재야 한다(≈800원).\n"
        "고치는 법: 업종 낱말 대신 **이 엔진의 분류**로 적는다. 예)\n"
        "  ✗ 「가전·온라인쇼핑 숫자는 성장 근거로 쓰지 마라」\n"
        "  ✓ 「상위 범주(OURS_UMBRELLA) 갈래는 성장 근거로 쓰지 않는다」")


def test_이_검사가_실제로_잡는다():
    """**검사가 헛돌지 않는지 스스로 확인한다.** 통과가 「없다」인지 「못 본다」인지 가른다."""
    업종어 = _업종어()
    심은 = sorted(업종어)[0]
    말들: list = []
    _기계가_읽는_값({"묶음": [{"고르기": {"어휘": [심은]}}]}, 말들)
    assert {w for 조각 in 말들 for w in _낱말.findall(조각) if w in 업종어}, \
        f"심어 둔 업종어 {심은!r} 를 못 잡았다"


def test_설명_키는_건너뛴다():
    """**왜 이 규칙인가**를 적은 기록은 지우게 만들지 않는다 — 그것이 이 저장소의 자산이다."""
    업종어 = _업종어()
    심은 = sorted(업종어)[0]
    말들: list = []
    _기계가_읽는_값({"_왜": f"실측: {심은} 에서 이런 일이 있었다"}, 말들)
    assert not {w for 조각 in 말들 for w in _낱말.findall(조각) if w in 업종어}
