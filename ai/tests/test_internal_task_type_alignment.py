import re
from pathlib import Path

from app.api.executions import TASK_TYPES


EXPECTED_TASK_TYPES = {
    "IDEA_INTERPRETATION",
    "LEGAL_REVIEW",
    "IDEA_LEGAL_PRECHECK",
    "CONCEPT_LEGAL_VALIDATION",
    "CONCEPT_GENERATION",
    "QUICK_ASSESSMENT",
    "DETAILED_ANALYSIS",
    "PERSONA_CARD_GENERATION",
    "PERSONA_INTERVIEW",
    "INTERVIEW_SYNTHESIS",
    "MARKETING_GENERATION",
    "MARKETING_COMPARISON",
    "FINAL_REPORT_GENERATION",
    # 실험용. AI 서버에만 구현이 있고 백엔드에는 enum 값만 있다 — 아직 이 TaskType 으로
    # TaskRun 을 만드는 코드가 없다. 패턴 B 로 옮길 때 TaskRunWorker.validateResult() 에
    # 분기를 넣어야 한다.
    "MARKET_RESEARCH",
}


def test_java_and_fastapi_task_types_are_the_same_fourteen_values():
    java_enum = (
        Path(__file__).resolve().parents[2]
        / "backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java"
    ).read_text(encoding="utf-8")
    enum_body = re.search(r"enum\s+TaskType\s*\{([^}]*)\}", java_enum, re.DOTALL)
    assert enum_body is not None
    java_task_types = {
        value.strip()
        for value in enum_body.group(1).split(",")
        if value.strip()
    }

    assert TASK_TYPES == EXPECTED_TASK_TYPES
    assert java_task_types == EXPECTED_TASK_TYPES
    assert len(java_task_types) == 14
