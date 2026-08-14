"""`execute_structured_prompt` 의 녹화/재생.

이 경로를 타는 것은 재무·시장 인터뷰·법률·여정이고, 2026-08-14 이전에는 녹화가 **없어서**
「돌려봐야 아는 것」이 매번 유료였다. 아래 네 가지만 지키면 된다.

  1. 껐을 때(기본) 동작이 하나도 안 바뀐다
  2. 재생이 붙으면 **네트워크를 아예 안 친다**
  3. 프롬프트가 다르면 다른 녹화다
  4. 깨진 녹화를 만나도 조용히 유료 호출로 넘어가지 않는다

⚠ 저장소에 async 테스트 플러그인이 없다(pytest-asyncio 미설치, async 테스트 0건).
   의존성을 늘리지 않으려고 루프를 직접 돌린다.
"""

import asyncio
import json

import pytest

from app.providers.structured import ProviderFailure, execute_structured_prompt


@pytest.fixture(autouse=True)
def provider_env(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_API_KEY", "sk-test-not-a-real-key")
    monkeypatch.setenv("AI_MODEL", "test-model")
    monkeypatch.setenv("AI_BASE_URL", "https://provider.invalid/v1")
    monkeypatch.delenv("AI_REPLAY_DIR", raising=False)
    monkeypatch.delenv("AI_REPLAY_MODE", raising=False)


class _Transport:
    """호출이 실제로 나갔는지 세는 가짜 전송층."""

    def __init__(self, payload):
        self.payload = payload
        self.calls = 0

    def install(self, monkeypatch):
        transport = self

        class Response:
            status_code = 200
            content = b"x" * 10
            headers: dict[str, str] = {}

            def json(self):
                return {"choices": [{"message": {"content": json.dumps(transport.payload)}}]}

        class Client:
            def __init__(self, **_kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *_args):
                return False

            async def post(self, *_args, **_kwargs):
                transport.calls += 1
                return Response()

        monkeypatch.setattr("app.providers.structured.httpx.AsyncClient", Client)
        return self


def _call(user: str = "user"):
    return asyncio.run(execute_structured_prompt("system", user, task_type="TEST_TASK"))


def test_replay_disabled_by_default_writes_nothing(monkeypatch, tmp_path):
    transport = _Transport({"ok": True}).install(monkeypatch)

    assert _call() == {"ok": True}

    assert transport.calls == 1
    assert list(tmp_path.iterdir()) == []


def test_auto_mode_records_then_replays_without_calling_provider(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_REPLAY_DIR", str(tmp_path))
    transport = _Transport({"ok": True}).install(monkeypatch)

    first = _call()
    recordings = list(tmp_path.glob("*.json"))
    assert len(recordings) == 1
    assert transport.calls == 1

    second = _call()
    assert second == first == {"ok": True}
    assert transport.calls == 1, "재생인데 provider 를 다시 쳤다"

    # 녹화 파일에 비밀이 섞이지 않는다 — 키는 헤더에만 있고 body 에는 없다.
    saved = json.loads(recordings[0].read_text(encoding="utf-8"))
    assert "sk-test-not-a-real-key" not in json.dumps(saved)
    assert saved["taskType"] == "TEST_TASK"


def test_replay_mode_fails_instead_of_spending_money_on_a_miss(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_REPLAY_DIR", str(tmp_path))
    monkeypatch.setenv("AI_REPLAY_MODE", "replay")
    transport = _Transport({"ok": True}).install(monkeypatch)

    with pytest.raises(ProviderFailure) as failure:
        _call()

    assert transport.calls == 0
    assert failure.value.code == "DEPENDENCY_UNAVAILABLE"
    assert failure.value.retryable is False


def test_a_different_prompt_is_a_different_recording(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_REPLAY_DIR", str(tmp_path))
    transport = _Transport({"ok": True}).install(monkeypatch)

    _call("user-a")
    _call("user-b")

    assert transport.calls == 2
    assert len(list(tmp_path.glob("*.json"))) == 2


def test_a_broken_recording_is_loud_not_a_silent_paid_call(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_REPLAY_DIR", str(tmp_path))
    transport = _Transport({"ok": True}).install(monkeypatch)
    _call()
    next(tmp_path.glob("*.json")).write_text("not json", encoding="utf-8")

    with pytest.raises(ProviderFailure):
        _call()

    assert transport.calls == 1, "깨진 녹화를 만나고 조용히 유료 호출로 넘어갔다"
