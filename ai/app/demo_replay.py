"""시연용 응답 재생.

⚠ **이것은 시연 전용 장치다. 기본은 꺼져 있다.**

시연 영상에서 파이프라인 전체를 실제로 돌리면 모듈 하나에 수십 분이 걸리고 유료다.
그렇다고 화면을 가짜로 만들면 「실제로 도는 것」을 보여줄 수 없다. 그래서 **느린
유료 호출 한 곳만** 미리 녹화해 둔 응답으로 바꾼다. 백엔드·DB·프론트·계보 검증은
전부 진짜로 돈다.

녹화본은 `task_results.result_json` 에서 그대로 꺼낸 것이라 스키마가 어긋날 일이 없다.

⚠ **녹화본은 프로젝트 2번의 것이다.** 응답 안에 `PROJECT:2`·`FINANCE:44e05e0b…`
   같은 그 프로젝트 전용 ID 가 박혀 있다. 그래서 **반드시 프로젝트 2번을 해당 모듈
   직전 스냅샷으로 되돌린 뒤** 재생해야 한다. 새 프로젝트에 재생하면 존재하지 않는
   ID 를 가리키는 결과가 만들어진다.

켜는 법 — `compose.demo.yaml` 을 겹쳐 올린다:
    docker compose -f compose.yaml -f compose.demo.yaml up -d ai-server

파일 이름 규칙:  <TASK_TYPE>.<순번3자리>.json   (예: MARKET_INTERVIEW.001.json)
같은 taskType 이 여러 번 불리면 순번대로 내주고, 다 쓰면 마지막 것을 반복한다.
"""

from __future__ import annotations

import json
import logging
import os
import threading
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_counters: dict[str, int] = {}
_lock = threading.Lock()


def replay_dir() -> Path | None:
    raw = os.getenv("AI_DEMO_REPLAY_DIR", "").strip()
    if not raw:
        return None
    path = Path(raw)
    return path if path.is_dir() else None


def delay_seconds() -> float:
    try:
        return max(0.0, float(os.getenv("AI_DEMO_DELAY_SECONDS", "3")))
    except ValueError:
        return 3.0


def _pick(directory: Path, task_type: str) -> Path | None:
    # 같은 taskType 의 녹화본을 순번대로 쓴다. 컨셉 포트폴리오처럼 한 모듈 안에서
    # 여러 번 불리는 것이 있어서 «몇 번째 호출인가» 를 구분해야 한다.
    candidates = sorted(directory.glob(f"{task_type}.*.json"))
    if not candidates:
        return None
    with _lock:
        index = _counters.get(task_type, 0)
        _counters[task_type] = index + 1
    # 녹화본을 다 쓰면 마지막 것을 반복한다 — 시연 중 소진돼서 터지는 것보다 낫다.
    return candidates[min(index, len(candidates) - 1)]


def load(task_type: str) -> dict[str, Any] | None:
    """녹화된 결과를 돌려준다. 시연 모드가 꺼져 있거나 녹화본이 없으면 None."""
    directory = replay_dir()
    if directory is None:
        return None
    chosen = _pick(directory, task_type)
    if chosen is None:
        logger.warning("demo replay: %s 녹화본 없음 — 실제 실행으로 넘어간다", task_type)
        return None
    try:
        with chosen.open(encoding="utf-8") as handle:
            result = json.load(handle)
    except Exception:
        logger.exception("demo replay: %s 읽기 실패 — 실제 실행으로 넘어간다", chosen)
        return None
    logger.info("demo replay: taskType=%s file=%s", task_type, chosen.name)
    return result


def reset() -> None:
    """순번을 처음으로 되돌린다. 스냅샷을 되감고 다시 찍을 때 쓴다."""
    with _lock:
        _counters.clear()
