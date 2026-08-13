# -*- coding: utf-8 -*-
"""칸 → 근거 매핑 — **LLM 0회.** 근거 id · 출처 라벨 · 상태를 **기계가 확정한다.**

<b>왜 이 층이 있나.</b> 캔버스를 쓰는 것은 모델이고(`bm/flow.py` — 「모델 호출은 정확히
1회다」), 모델은 자기가 인용할 근거도 스스로 고른다. 실측(2026-08-13, 프로젝트 3 HMR):
원장에 근거 17건이 있고 BM 봉투가 그것을 전부 실어 보냈는데 **캔버스가 인용한 것은 0건**,
그러면서 관측 3칸이 `labels=['concept_snapshot']` 로 `VERIFIED` 였다. `concept_snapshot`
은 **사용자가 쓴 컨셉 서술문**이다 — 모델이 자기 입력을 자기가 확인했다고 도장 찍었다.

<b>그래서 셋을 모델에게 안 맡긴다.</b> 카드의 `칸` 은 원장이 이미 알고 있고, 라벨은 그
`칸` 에서 나오고, 상태는 근거 개수와 `verdict` 도장에서 나온다. 셋 다 모델이 지어낼 수
없는 사실이다. 모델에게 남는 것은 문장(`content`·`reason`)과 해석뿐이다.

<b>덮는 대상은 관측 4칸뿐이다.</b> 계획 5칸(`gate.PLANNED_CELLS`)은 손대지 않는다. 이유 둘:

① 계획 칸에 근거 id 가 하나라도 붙으면 `serialize._stamp_user_plan()` 이 그 칸을 통째로
   건너뛰어(`serialize.py:517`) **PLAN 도장과 「사용자가 입력한 실행 계획이다 — 관측이
   아니다」 경계 문구가 사라진다.**
② 계획 5칸 상태를 여기서 파생하면 `gate` G4(계획 5칸 전부 관측 미달)가 상시 발동해
   판정 상한이 영구 `CONDITIONAL` 이 된다.

<b>돌리는 자리는 `serialize.canvas_cells()` 보다 반드시 앞이다.</b> 그 함수가
`marketEvidenceIds` 에서 칸의 경계(`caveats`)를 파생하고 `assert_caveats_reached()` 와
자바 `requireCaveats` 가 같은 불변식을 두 층에서 막는다 — 뒤에서 id 를 바꾸면 BM 결과가
통째로 거부된다. 순서는 **mapping → citation.enforce → _stamp_user_plan → gate** 다.
"""
from __future__ import annotations

from ..research.bm.contracts import CanvasStatus
from ..research.bm.prompt import ALLOWED_CANVAS_SOURCE_LABELS
from .gate import OBSERVED_CELLS, _MARKET_LABELS

#: 카드의 `칸` → 캔버스 칸. 정본은 `research2/harness/vocab.json` 의
#: `canvas.측정판정.cells`(칸 이름은 한글, 값이 claim_type)이고 여기는 **enum 이름으로만**
#: 바꾼다.
#:
#: 관측 카드의 `칸` 은 **항상 claim_type 이다.** `cards.py:99` 는 `_canvas_cell or
#: claim_type` 이지만 슬롯 정의(`research2/data/slots_*.json` 40개 중 37개)가 싣는
#: `_canvas_cell`(한글)이 원장까지 못 온다 — `run.py:42 mk_slot` 이 `_` 접두 키를 통째로
#: 버리기 때문이다(절대 규칙 6, 자기확인 회로 차단). 그래서 항상 claim_type 으로 떨어진다.
#: ⚠ `Slot` 에 `canvas_cell` 이 승격되면 이 전제가 뒤집힌다 — 그때는 `_cell_of` 가 관측
#:   카드를 **어느 칸에도 안 붙여** 관측 4칸이 근거 0건이 되고 G1 이 네 번 걸린다(fail-closed).
#:   `tests/test_validation_mapping.py` 가 그 모양을 못박아 둔다.
#:
#: ⚠ `GROWTH` 는 vocab 의 `claim_types_by_formula.F_GROWTH` 예외로 고객 세그먼트에 실린다.
CLAIM_TYPE_CELL = {
    "TAM": "CUSTOMER_SEGMENTS",
    "SAM": "CUSTOMER_SEGMENTS",
    "GROWTH": "CUSTOMER_SEGMENTS",
    "PAIN": "VALUE_PROPOSITIONS",
    "COMP": "VALUE_PROPOSITIONS",
    "COMPARABLE": "VALUE_PROPOSITIONS",
    "CHANNEL": "CHANNELS",
    "PRICE": "REVENUE_STREAMS",
    "ALT": "REVENUE_STREAMS",
}

#: 한글 칸 이름 → 캔버스 칸. **계산 카드(`C-CALC-*`) 전용 보조 경로다** — 그 카드만 이 길로
#: 온다(`cards.py:165` 가 「고객 세그먼트」를 글자 그대로 박는다). 저장소에 한글↔enum
#: 사상표가 없어 여기서 새로 만든다. `tests/test_validation_mapping.py` 가 vocab.json 과 대조한다.
#:
#: ⚠ **관측 카드에는 이 길을 열지 않는다.** 열어 두면 위 전제가 뒤집혔을 때(=`칸` 이 한글로
#:   오는 날) 근거 id 는 붙는데 라벨은 0건이 되고, `_labels_for` 폴백이 모델이 쓴
#:   `concept_snapshot` 을 되살린다 — 이 층이 막으려던 바로 그 상태로 **조용히** 되돌아간다.
#:   막힌 채 시끄럽게 실패하는 편(G1)이 낫다.
CELL_NAME_KO = {
    "고객 세그먼트": "CUSTOMER_SEGMENTS",
    "가치 제안": "VALUE_PROPOSITIONS",
    "채널": "CHANNELS",
    "수익원": "REVENUE_STREAMS",
}

#: `칸`(claim_type) → 출처 라벨. 값은 전부 `gate._MARKET_LABELS` 안에 있다 —
#: 라벨 화이트리스트는 이미 세 벌(`bm/prompt.py`·`gate.py`·자바)이라 **네 번째를 만들지
#: 않는다.** `CHANNEL` 에 맞는 라벨이 화이트리스트에 없어 자리가 비어 있다.
CLAIM_TYPE_LABEL = {
    "TAM": "market_size",
    "SAM": "market_size",
    "GROWTH": "growth_rate",
    "COMP": "competitor_analysis",
    "COMPARABLE": "competitor_analysis",
    "PRICE": "price_analysis",
    "ALT": "price_analysis",
    "PAIN": "demand_evidence",
}

#: 계산 카드 id 접미사 → 라벨. `C-CALC-{TAM,SAM,성장률}`(`cards.py:163`).
_CALC_LABEL = {"TAM": "market_size", "SAM": "market_size", "성장률": "growth_rate"}

#: `verdict["판정"]` 의 도장 키 → 캔버스 칸. **넷 중 셋만 칸이 있다**
#: (`verdict.py:745-750`) — `9_SOM_초기점유` 는 캔버스 칸이 아니고, 거꾸로
#: `CUSTOMER_SEGMENTS` 에 대응하는 도장이 없다. 그 칸은 근거 개수만으로 정한다.
STAMP_CELL = {
    "6_수익_가격": "REVENUE_STREAMS",
    "7_채널": "CHANNELS",
    "8_차별점": "VALUE_PROPOSITIONS",
}

#: 도장이 있는 칸의 상태. 근거가 0건이면 도장과 무관하게 `UNVERIFIED` 다.
_STAMP_STATUS = {"검증됨": CanvasStatus.VERIFIED}

#: 도장이 없는 칸(`CUSTOMER_SEGMENTS`)에서 `VERIFIED` 로 보는 최소 근거 수.
#: 시장 크기·성장률이 같은 칸에 실리므로 한 건은 「부분」이다.
_VERIFIED_MIN_EVIDENCE = 2


def _cell_of(card: dict) -> str | None:
    """카드 하나가 어느 칸인가. 못 정하면 `None`(그 카드는 어느 칸에도 안 붙는다).

    한글 칸 이름은 **계산 카드에서만** 읽는다(`CELL_NAME_KO` 주석 참조).
    """
    name = str(card.get("칸") or "")
    cell = CLAIM_TYPE_CELL.get(name)
    if cell:
        return cell
    if _calc_suffix(card) is not None:
        return CELL_NAME_KO.get(name)
    return None


def _calc_suffix(card: dict) -> str | None:
    """계산 카드면 id 접미사(`TAM`·`SAM`·`성장률`), 아니면 `None`."""
    card_id = str(card.get("카드_id") or "")
    prefix = "C-CALC-"
    return card_id[len(prefix):] if card_id.startswith(prefix) else None


def _label_of(card: dict) -> str | None:
    """카드 하나가 만드는 출처 라벨. 계산 카드는 id 접미사에서 읽는다."""
    name = str(card.get("칸") or "")
    label = CLAIM_TYPE_LABEL.get(name)
    if label:
        return label
    suffix = _calc_suffix(card)
    return _CALC_LABEL.get(suffix) if suffix is not None else None


def _stamps(verdict: dict | None) -> dict[str, str]:
    """`verdict` → 칸별 도장. 없으면 빈 dict."""
    out = {}
    for key, cell in STAMP_CELL.items():
        entry = ((verdict or {}).get("판정") or {}).get(key) or {}
        stamp = entry.get("도장")
        if stamp:
            out[cell] = str(stamp)
    return out


def derive(cards: list[dict], verdict: dict | None = None) -> dict[str, dict]:
    """카드(+`verdict`) → **관측 4칸**의 `marketEvidenceIds`·`sourceLabels`·`status`.

    근거 id 는 `serialize.evidence(cards)` 와 **같은 카드 리스트**에서만 뽑는다 — 자바
    계약이 `marketEvidenceIds ⊆ evidence[].id` 를 요구하므로 등급 등으로 거르면 안 된다.
    """
    ids: dict[str, list[str]] = {name: [] for name in OBSERVED_CELLS}
    labels: dict[str, list[str]] = {name: [] for name in OBSERVED_CELLS}
    for card in cards or []:
        cell = _cell_of(card)
        if cell not in ids:
            continue
        card_id = str(card.get("카드_id") or "")
        if not card_id or card_id in ids[cell]:
            continue
        ids[cell].append(card_id)
        label = _label_of(card)
        if label and label not in labels[cell]:
            labels[cell].append(label)

    stamps = _stamps(verdict)
    out = {}
    for name in OBSERVED_CELLS:
        count = len(ids[name])
        if count == 0:
            status = CanvasStatus.UNVERIFIED
        elif name in stamps:
            status = _STAMP_STATUS.get(stamps[name], CanvasStatus.PARTIAL)
        elif count >= _VERIFIED_MIN_EVIDENCE:
            status = CanvasStatus.VERIFIED
        else:
            status = CanvasStatus.PARTIAL
        out[name] = {"marketEvidenceIds": ids[name],
                     "sourceLabels": labels[name],
                     "status": status}
    return out


def _labels_for(item, derived: list[str]) -> list[str]:
    """파생 라벨. **비우지 않는다** — `content` 가 있는데 라벨이 0건이면 자바가 거부한다
    (`MarketResearchContract.java:244`). 그 경우 모델이 낸 라벨을 화이트리스트로 걸러 남긴다.
    """
    if derived or not item.content:
        return derived
    return [label for label in item.source_labels
            if label in ALLOWED_CANVAS_SOURCE_LABELS]


def apply(analysis, cards: list[dict], verdict: dict | None = None):
    """`BMAnalysisResult` → 관측 4칸을 기계 파생값으로 덮은 복사본.

    계획 5칸은 **그대로 돌려준다**(근거 id 가 붙는 순간 `USER_PLAN_CAVEAT` 이 사라진다).
    법률이 낸 `BLOCKED` 도 덮지 않는다 — 이 층은 판정을 올리는 자리가 아니다.

    ⚠ `model_copy(update=...)` 는 검증을 안 거친다. `status` 에 평문 문자열을 넣으면
      직렬화가 `status.value` 에서 터지므로 **enum 을 넣는다**.
    """
    derived = derive(cards, verdict)
    cells = []
    for item in analysis.canvas:
        found = derived.get(str(item.canvas_cell))
        if found is None:
            cells.append(item)                      # 계획 5칸 — 손대지 않는다
            continue
        update = {
            "market_evidence_ids": list(found["marketEvidenceIds"]),
            "source_labels": _labels_for(item, list(found["sourceLabels"])),
        }
        if item.status != CanvasStatus.BLOCKED:
            update["status"] = found["status"]
        cells.append(item.model_copy(update=update))
    return analysis.model_copy(update={"canvas": cells})
