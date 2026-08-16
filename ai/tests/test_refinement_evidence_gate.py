# -*- coding: utf-8 -*-
"""다듬기 제안의 **근거 계약** — 근거 없는 제안은 애초에 안 나온다.

프롬프트는 *"시장 근거로 고치는 제안에는 근거 id가 붙어야 한다. 근거 없는 제안은
버려진다"* 고 약속한다(`app/tasks/concept_refinement.py`). 그런데 **그 검사를 하는 코드가
아무 데도 없었다** — `drift.filter_proposals` 는 값의 폭만, Java `requireProposals()` 는
모양만 본다. 그래서 근거 0건 제안이 그대로 컨셉에 적용됐고, 화면에는 「근거 없음」 배지를
단 채 떴다(실측: 가격 1팩 8,900원 → 9,500원).

⚠ 이 검사는 **`filter_proposals` 와 갈라 둔다.** 값 계약과 근거 계약을 한 함수에 섞으면
다음 사람이 어느 쪽 때문에 기각됐는지 못 가린다. 기존 시험 다섯이 근거 없는 제안으로 값
판정만 시험하는 것도 같은 이유로 그대로 둘 수 있다.
"""
from __future__ import annotations

from app.validation.drift import filter_ungrounded

봉투 = [{"id": "C-F001"}, {"id": "C-F012"}, {"id": "P-0003"}]


def _제안(**over) -> dict:
    base = {"fieldKey": "price", "currentValue": "1팩 8,900원",
            "proposedValue": "1팩 9,500원", "rationale": "이유",
            "source": "MARKET", "legalRef": None, "evidenceIds": ["C-F001"]}
    base.update(over)
    return base


def test_시장_근거가_0건이면_기각한다():
    통과, 기각 = filter_ungrounded([_제안(evidenceIds=[])], 봉투)
    assert 통과 == []
    assert len(기각) == 1
    assert "근거가 0건" in 기각[0]["rejectionReason"]


def test_없는_근거는_떼어_내고_진짜가_남으면_살린다():
    """★ 2026-08-15 실측으로 뒤집은 규칙.

    옛 규칙은 **전부 아니면 전무**였다 — 든 id 중 하나라도 봉투에 없으면 제안을 통째로
    기각했다. 그 결과 `p47-refine-01` 에서 **편의점 도시락 판매가 18건을 제대로 인용한
    가격 제안**이, 열아홉 번째로 지어낸 번호 하나(`C-F076`) 때문에 죽었다. 같은 실행에서
    **살아남은 것은 시장 규모 38조로 「차별점」을 바꾸자던 제안**이었다 — 게이트가 정확히
    거꾸로 걸렀다.

    ⚠ 규칙 §5-5(「보낸 ID 와 대조한다」)는 **그대로다.** 지어낸 번호는 여기서 사라져
    화면에도 안 간다. 달라진 것은 **그 벌을 제안 전체에 물리지 않는다**는 것뿐이다.
    """
    통과, 기각 = filter_ungrounded([_제안(evidenceIds=["C-F001", "C-없는것"])], 봉투)
    assert 기각 == []
    assert len(통과) == 1
    assert 통과[0]["evidenceIds"] == ["C-F001"], "지어낸 번호는 떼어 낸다"
    assert 통과[0]["proposedValue"] == "1팩 9,500원", "제안 값은 안 건드린다"


def test_든_근거가_전부_없는_것이면_기각한다():
    """떼고 나서 **남는 것이 0건**이면 그것은 근거 0건 제안과 같다."""
    통과, 기각 = filter_ungrounded([_제안(evidenceIds=["C-없는것", "또없는것"])], 봉투)
    assert 통과 == []
    assert "C-없는것" in 기각[0]["rejectionReason"]


def test_근거가_있으면_통과한다():
    통과, 기각 = filter_ungrounded([_제안(evidenceIds=["C-F001", "P-0003"])], 봉투)
    assert len(통과) == 1 and 기각 == []


def test_승격된_절_사실도_근거로_인정한다():
    """★ 판 ㊺ 에서 근거가 15장 → 143장이 됐다. 절 사실(`P-*`)을 못 쓰면 그 판이 헛것이 된다."""
    통과, _ = filter_ungrounded([_제안(evidenceIds=["P-0003"])], 봉투)
    assert len(통과) == 1


def test_법률_제안은_조항이_근거_자리를_대신한다():
    """광고 문구 칸이 동결이라 `differentiators` 로 우회하는 갈래다 — `evidenceIds` 는 빈다."""
    통과, 기각 = filter_ungrounded(
        [_제안(source="LEGAL", legalRef="식품위생법 제13조", evidenceIds=[])], 봉투)
    assert len(통과) == 1 and 기각 == []


def test_법률_제안인데_조항도_없으면_기각한다():
    통과, 기각 = filter_ungrounded([_제안(source="LEGAL", legalRef=None, evidenceIds=[])], 봉투)
    assert 통과 == []
    assert "legalRef" in 기각[0]["rejectionReason"]


def test_봉투를_못_받으면_환각_검사는_건너뛴다():
    """모르는 것을 「환각」으로 단정하면 멀쩡한 제안이 전부 기각된다. 0건 검사는 그래도 한다."""
    통과, 기각 = filter_ungrounded([_제안(evidenceIds=["무엇이든"])], None)
    assert len(통과) == 1 and 기각 == []
    통과2, 기각2 = filter_ungrounded([_제안(evidenceIds=[])], None)
    assert 통과2 == [] and len(기각2) == 1


def test_기각_사유는_사람이_읽는_말이다():
    """이 문장이 화면 「못 푼 것」에 그대로 선다 — 기계 말이 새면 안 된다."""
    _, 기각 = filter_ungrounded([_제안(evidenceIds=[])], 봉투)
    사유 = 기각[0]["rejectionReason"]
    assert not any(c in 사유 for c in "{}[]<>"), 사유
    assert len(사유) < 60, 사유


def test_기각해도_제안_내용은_안_지운다():
    """제안은 지워서 반쪽으로 만들면 안 되는 값이다 — 사유만 덧붙인다."""
    _, 기각 = filter_ungrounded([_제안(evidenceIds=[])], 봉투)
    assert 기각[0]["fieldKey"] == "price"
    assert 기각[0]["proposedValue"] == "1팩 9,500원"
