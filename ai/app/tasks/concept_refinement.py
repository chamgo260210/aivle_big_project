# -*- coding: utf-8 -*-
"""시장 근거로 컨셉을 다듬는 제안 — <b>LLM 1회/라운드</b>.

새 TaskType 이 아니다. `CONCEPT_PORTFOLIO_V2_SELECTION_ACTION` 의 액션 하나
(`REFINE_FROM_MARKET`)로 들어간다 — 워커·타임아웃 배선이 그 위에 이미 있다.

⚠ **이 함수는 판정하지 않는다.** 계약(드리프트) 판정은 `app.validation.drift` 가 하고,
법률은 `DELTA_LEGAL` 이 한다. 여기는 «무엇을 어떻게 바꾸자»를 받아 오는 자리뿐이다.
두 일을 한 프롬프트에 묶으면 모델이 자기 제안을 자기가 통과시킨다.
"""
from __future__ import annotations

import json
from typing import Any

from app.providers import execute_structured_prompt

#: 한 라운드가 낼 수 있는 제안 수. 넘게 오면 앞에서부터 자른다 —
#: 한 번에 열 칸을 바꾸는 것은 다듬기가 아니라 다시 만드는 것이다.
MAX_PROPOSALS = 6

SYSTEM = """당신은 사업안을 **시장 근거와 법률 소견으로 다듬는** 분석가다.

지켜야 하는 것:
- `fieldKey` 는 **`refinableFields` 의 키를 그대로** 쓴다(예: `channels`, `price`).
  게이트 사유·캔버스에 보이는 대문자 가설 이름(`CHANNELS`, `PRICE`)을 쓰지 않는다.
- 동결된 칸은 **절대 건드리지 않는다**. 건드린 제안은 버려진다.
- 다듬을 수 있는 칸만 제안한다. 각 칸의 허용 폭이 함께 주어진다.
- 목록 칸(`channels`·`differentiators`·`featureSet`)은 **기존 항목을 글자 그대로 옮겨 적고**
  거기에 한 개만 더하거나 한 개만 바꾼다. 기존 항목을 요약하거나 고쳐 쓰면 「뺐다」로 세어져
  버려진다. `proposedValue` 는 원래 값과 **같은 자료형**으로 낸다.
- `price` 는 원래 적힌 방식을 그대로 따르고 금액만 바꾼다(예: 「1팩 8,900원」 → 「1팩 9,500원」).
- 시장 근거로 고치는 제안(`source: "MARKET"`)에는 **근거 id**가 붙어야 한다.
  근거 없는 제안은 버려진다.
- **법이 막은 표현**이 `legalFindings` 에 있으면, 광고 문구 칸은 동결이므로
  **`differentiators`** 를 고쳐 그 표현을 대체한다. 그 제안은 `source: "LEGAL"` 이고
  `legalRef` 에 「법령명 제N조」를 적는다(`evidenceIds` 는 비워도 된다).
- 직전 라운드에서 기각된 제안이 주어지면 **같은 제안을 반복하지 않는다**. 왜 막혔는지를
  읽고 다른 길을 찾거나, 길이 없으면 그 칸을 비운다.
- 고칠 것이 없으면 **빈 목록**을 낸다. 억지로 채우지 않는다.

각 제안은 사람이 읽는 말을 함께 낸다:
- `title` — 무엇을 했는지 한 마디(30자 이내). 예: "타깃을 좁혔어요", "가격을 시장 안으로 옮겼어요"
- `beforeText` / `afterText` — 바뀌기 전후를 **사람이 읽는 한 줄**로(120자 이내).
  목록 값이라도 JSON 이 아니라 말로 적는다. 예: "바쁜 직장인 누구나" → "바쁜 1인 가구 직장인"
- `rationale` — 왜 바꿨는지. 조사 결과를 가리키는 평서문.

출력은 JSON 하나:
{"proposals": [{"fieldKey": "...", "currentValue": ..., "proposedValue": ...,
                "title": "...", "beforeText": "...", "afterText": "...",
                "rationale": "...", "source": "MARKET", "legalRef": null,
                "evidenceIds": ["..."]}]}"""

NARRATE_SYSTEM = """당신은 확정된 사업안을 **한 문단으로 소개하는** 편집자다.

지켜야 하는 것:
- **없는 사실을 쓰지 않는다.** 주어진 컨셉 값과 변경 목록 안에서만 쓴다.
- 사업안 이름(`conceptName`)은 문단 안에 **그대로** 들어가야 한다.
- 각 변경의 `mark` 를 **글자 그대로** 담은 조각으로 끊고, 그 조각에 `changeRef`
  (변경 목록의 1부터 세는 번호)를 붙인다. 안 바뀐 구간은 `changeRef: null`.
  `mark` 는 그 변경에서 **실제로 바뀐 말**이다 — 줄이거나 바꿔 적으면 버려진다.
- 조각을 순서대로 이어 붙이면 자연스러운 한 문단이 되어야 한다.
- 한 changeRef 는 **한 번만** 쓴다.
- 존댓말로 쓴다. 3~5문장.

출력은 JSON 하나:
{"narrative": [{"text": "...", "changeRef": null}, {"text": "...", "changeRef": 1}]}"""


async def propose_refinements(material: dict[str, Any], concept: dict[str, Any]) -> list[dict]:
    """제안을 받아 온다. 계약 판정은 하지 않는다 — 호출자가 `drift.filter_proposals` 로 거른다."""
    user = json.dumps({
        "concept": concept,
        "round": material.get("round", 1),
        "frozenFields": material.get("frozenFields") or [],
        "refinableFields": material.get("refinableFields") or {},
        "gateReasons": material.get("gateReasons") or [],
        "canvas": material.get("canvas"),
        "marketEvidence": material.get("marketEvidence") or [],
        "legalFindings": material.get("legalFindings") or [],
        "previouslyRejectedByContract": material.get("driftRejections") or [],
        "previouslyRejectedByLegal": material.get("legalRejections") or [],
    }, ensure_ascii=False, sort_keys=True)

    payload = await execute_structured_prompt(system=SYSTEM, user=user,
                                              task_type="REFINE_FROM_MARKET")
    proposals = payload.get("proposals")
    if not isinstance(proposals, list):
        return []
    return [item for item in proposals[:MAX_PROPOSALS] if isinstance(item, dict)]


async def narrate_refined(concept: dict[str, Any], changes: list[dict[str, Any]]) -> list[dict]:
    """최종 컨셉 서술문을 조각 배열로 받아 온다. **판정은 Java 가 한다.**

    ⚠ 여기서 검증하지 않는 이유: 「변경 조각이 정말 그 값을 담았나」는 저장 직전에
    한 번만 봐야 한다. 두 곳에서 보면 규칙이 갈린다.
    """
    user = json.dumps({
        "concept": concept,
        "changes": [
            {
                "no": index + 1,
                "title": item.get("title", ""),
                "fieldKey": item.get("fieldKey", ""),
                # 문단이 반드시 담아야 하는 말. Java 가 같은 잣대로 검증한다.
                "mark": item.get("mark") or item.get("afterText", ""),
                "afterText": item.get("afterText", ""),
            }
            for index, item in enumerate(changes[:MAX_PROPOSALS])
        ],
    }, ensure_ascii=False, sort_keys=True)

    payload = await execute_structured_prompt(system=NARRATE_SYSTEM, user=user,
                                              task_type="NARRATE_REFINED")
    segments = payload.get("narrative")
    if not isinstance(segments, list):
        return []
    return [item for item in segments[:60] if isinstance(item, dict)]
