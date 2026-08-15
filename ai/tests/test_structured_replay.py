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


def structured_temperature():
    from app.providers import structured
    return structured.TEMPERATURE


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


# ── 온도를 거절하는 모델 (2026-08-15 신설) ──────────────────────────────
#
# 추론 모델(gpt-5.6 계열)은 `temperature` 를 1 로 고정하고 다른 값을 400 으로 거절한다
# (실측: 「Only the default (1) value is supported」). 이 함수는 제품의 **모든** 구조화
# 호출이 지나는 자리라, 여기서 안 막아 두면 모델을 바꾸는 순간 컨셉·BM·법률·마케팅·
# 인터뷰가 한꺼번에 죽는다.

class _RejectsTemperature(_Transport):
    """추론 모델을 흉내 낸다 — **추론을 끄지 않은 채** 온도를 보내면 400.

    실제 규칙 그대로다(2026-08-15 실측): `effort=none` 이면 온도가 통하고,
    `effort=low` 와 함께면 400 이다. 이 규칙을 안 흉내 내면 「온도를 되찾는 길」이
    있는데도 테스트가 초록으로 지나간다.
    """

    def install(self, monkeypatch):
        transport = self
        transport.bodies = []

        class Response:
            content = b"x" * 10
            headers: dict[str, str] = {}

            def __init__(self, rejected):
                self.status_code = 400 if rejected else 200
                self._rejected = rejected

            def json(self):
                if self._rejected:
                    return {"error": {"type": "invalid_request_error", "param": "temperature",
                                      "message": "Unsupported value: 'temperature' does not "
                                                 "support 0.1 with this model."}}
                return {"choices": [{"message": {"content": json.dumps(transport.payload)}}]}

        class Client:
            def __init__(self, **_kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *_args):
                return False

            async def post(self, *_args, **kwargs):
                body = kwargs["json"]
                transport.calls += 1
                transport.bodies.append(body)
                rejected = ("temperature" in body
                            and body.get("reasoning_effort") != "none")
                return Response(rejected)

        monkeypatch.setattr("app.providers.structured.httpx.AsyncClient", Client)
        return self


@pytest.fixture(autouse=True)
def forget_learned_models():
    """배운 것은 프로세스에 남는다 — 테스트끼리 새게 두면 순서에 따라 결과가 갈린다."""
    from app.providers import structured
    structured._MODEL_MODE.clear()
    yield
    structured._MODEL_MODE.clear()


def test_a_model_that_rejects_temperature_is_retried_without_it(monkeypatch):
    transport = _RejectsTemperature({"ok": True}).install(monkeypatch)

    assert _call() == {"ok": True}

    assert transport.calls == 2, "온도 거절을 보고도 다시 보내지 않았다"
    assert "temperature" in transport.bodies[0]
    assert "reasoning_effort" not in transport.bodies[0]
    # ★ 온도를 «버리지» 않고 추론을 끄는 쪽을 먼저 시도한다 — 옛 동작을 지키는 길이다.
    assert transport.bodies[1]["temperature"] == structured_temperature()
    assert transport.bodies[1]["reasoning_effort"] == "none"


def test_the_rejection_is_learned_so_the_next_call_costs_nothing_extra(monkeypatch):
    transport = _RejectsTemperature({"ok": True}).install(monkeypatch)

    _call("user-a")
    _call("user-b")

    # 1회차는 400 + 재시도 2회, 2회차는 처음부터 온도 없이 1회.
    assert transport.calls == 3
    assert transport.bodies[2]["reasoning_effort"] == "none"


def test_a_four_hundred_that_is_not_about_temperature_is_not_retried(monkeypatch):
    """다른 400 까지 재시도하면 엉뚱한 곳에 돈을 쓴다."""
    transport = _Transport({"ok": True})

    class Response:
        status_code = 400
        content = b"x" * 10
        headers: dict[str, str] = {}

        def json(self):
            return {"error": {"type": "invalid_request_error", "param": "messages",
                              "message": "too long"}}

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

    with pytest.raises(ProviderFailure):
        _call()

    assert transport.calls == 1, "온도와 무관한 400 을 재시도했다"


def test_the_recording_is_keyed_by_the_body_that_was_actually_sent(monkeypatch, tmp_path):
    """열쇠가 «보내지도 않은» 본문에서 나오면 다음 판이 엉뚱한 답을 재생한다."""
    monkeypatch.setenv("AI_REPLAY_DIR", str(tmp_path))
    transport = _RejectsTemperature({"ok": True}).install(monkeypatch)

    _call()
    recordings = list(tmp_path.glob("*.json"))
    assert len(recordings) == 1
    saved = json.loads(recordings[0].read_text(encoding="utf-8"))
    assert saved["request"]["reasoning_effort"] == "none"

    # 두 번째 판은 배운 상태로 시작하므로 같은 열쇠를 만들고 **재생**해야 한다.
    from app.providers import structured
    structured._MODEL_MODE.clear()                    # 재기동을 흉내 낸다
    before = transport.calls
    assert _call() == {"ok": True}
    assert transport.calls == before + 1, "400 한 번만 더 겪고 재생으로 붙어야 한다"
