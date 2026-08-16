"""모델 하나가 **우리가 실제로 보내는 요청 모양**을 받는지 확인한다.

```
cd ai
python -m app.tools.model_probe gpt-5.6-luna
python -m app.tools.model_probe gpt-4o-mini gpt-5.6-luna     # 나란히
```

**왜 필요한가.** 모델을 바꿀 때 값과 품질만 보고 고르면 늦게 터진다. 2026-08-15 에
`AI_MODEL` 을 `gpt-4o-mini` → `gpt-5.6-luna` 로 바꾸자 이런 일이 났다.

- 추론 모델은 `temperature` 를 1 로 고정하고 다른 값을 **400 으로 거절**한다
- 그런데 **`reasoning_effort="none"` 을 같이 주면 온도가 돌아온다**
- 그리고 제품에는 요청을 만드는 자리가 **세 군데**인데 서로 모양이 다르다 —
  한 곳을 고쳐도 나머지가 조용히 깨진 채로 남는다

이 도구는 **그 세 모양을 그대로** 보내 보고 무엇이 통하는지 표로 찍는다. 각 확인은
1회 호출이고 답은 한 글자라 값은 사실상 0 이다.

⚠ **품질은 재지 않는다.** 「이 모델이 우리 요청을 받는가」만 본다.
품질 대결은 원장 재코딩(`app.tools.recode_ledger`)과 시장조사 실측이 따로 한다.
"""

import argparse
import io
import json
import os
import sys

import httpx

#: 제품이 실제로 쓰는 온도. `app/providers/structured.py` 와 `research/bm/analyze.py` 가
#: 같은 값을 쓴다 — 여기서 다른 값을 넣으면 확인이 거짓이 된다.
TEMPERATURE = 0.1

#: 확인용 최소 스키마. strict json_schema 를 받는지만 보면 되므로 한 칸이면 충분하다.
SCHEMA = {"type": "object", "properties": {"ok": {"type": "boolean"}},
          "required": ["ok"], "additionalProperties": False}

TIMEOUT = 90.0


def _load_env(path: str = "../.env") -> None:
    """`.env` 를 환경에 얹는다. **이미 있는 값은 덮지 않는다.**"""
    if not os.path.isfile(path):
        return
    for line in io.open(path, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())


def _post(endpoint: str, key: str, body: dict) -> tuple[int, str]:
    try:
        response = httpx.post(f"https://api.openai.com/v1/{endpoint}",
                              headers={"Authorization": f"Bearer {key}"},
                              json=body, timeout=TIMEOUT)
    except httpx.HTTPError as failure:
        return 0, str(failure)[:120]
    if response.status_code == 200:
        return 200, ""
    try:
        return response.status_code, (response.json().get("error") or {}).get("message", "")[:120]
    except ValueError:
        return response.status_code, response.text[:120]


def _shapes(model: str) -> list[tuple[str, str, dict]]:
    """`(이름, 엔드포인트, 본문)` — **제품이 실제로 보내는 모양 그대로**.

    각 줄 옆에 어느 파일이 그 모양을 만드는지 적는다. 여기가 낡으면 확인이 거짓이 된다.
    """
    chat = [{"role": "user", "content": "ok"}]
    schema_format = {"type": "json_schema",
                     "json_schema": {"name": "probe", "strict": True, "schema": SCHEMA}}
    return [
        # app/providers/structured.py — 컨셉·법률·마케팅·인터뷰 코딩이 전부 이 모양이다
        ("structured: 온도만", "chat/completions",
         {"model": model, "messages": chat, "temperature": TEMPERATURE,
          "response_format": schema_format}),
        ("structured: 추론끔+온도", "chat/completions",
         {"model": model, "messages": chat, "temperature": TEMPERATURE,
          "reasoning_effort": "none", "response_format": schema_format}),
        ("structured: 온도 없이", "chat/completions",
         {"model": model, "messages": chat, "response_format": schema_format}),
        # app/interview/runner.py — 합성 응답자. 온도 1.0 이라 원래 문제가 없다
        ("interview: 온도1+토큰상한", "chat/completions",
         {"model": model, "messages": chat, "temperature": 1.0,
          "max_completion_tokens": 64, "response_format": schema_format}),
        # app/research/bm/analyze.py — responses.parse. **온도 인자를 직접 넘긴다**
        ("bm(responses): 온도만", "responses",
         {"model": model, "input": chat, "temperature": TEMPERATURE}),
        ("bm(responses): 추론끔+온도", "responses",
         {"model": model, "input": chat, "temperature": TEMPERATURE,
          "reasoning": {"effort": "none"}}),
        # research2/adapters/web.py — 웹 검색 도구. 도구를 못 붙이면 수집이 죽는다
        ("research2: web_search 도구", "responses",
         {"model": model, "input": chat, "tools": [{"type": "web_search"}]}),
    ]


def probe(model: str, key: str) -> list[tuple[str, int, str]]:
    return [(name, *_post(endpoint, key, body)) for name, endpoint, body in _shapes(model)]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="모델이 우리가 보내는 요청 모양을 받는지 확인한다 (품질은 안 잰다)")
    parser.add_argument("models", nargs="+", help="확인할 모델 이름")
    parser.add_argument("--env", default="../.env", help="키를 읽을 .env 경로")
    args = parser.parse_args(argv)

    _load_env(args.env)
    key = (os.getenv("AI_API_KEY") or os.getenv("OPENAI_API_KEY") or "").strip()
    if not key:
        print("AI_API_KEY 가 없다 — .env 경로를 --env 로 주거나 환경에 넣어라")
        return 2

    results = {model: probe(model, key) for model in args.models}
    width = max(len(name) for name, _e, _b in _shapes(args.models[0]))
    header = "요청 모양".ljust(width) + "".join(f"  {m:>16}" for m in args.models)
    print(header)
    print("-" * len(header))
    for index, (name, _endpoint, _body) in enumerate(_shapes(args.models[0])):
        cells = ""
        for model in args.models:
            status = results[model][index][1]
            cells += f"  {('OK' if status == 200 else str(status)):>16}"
        print(name.ljust(width) + cells)

    # 통하지 않은 것은 **이유까지** 찍는다. 상태 코드만 보면 왜 막혔는지 모른다.
    for model in args.models:
        failures = [(name, status, message) for name, status, message in results[model]
                    if status != 200]
        if failures:
            print(f"\n── {model} 이 거절한 것")
            for name, status, message in failures:
                print(f"   {name}  [{status}] {message}")
    return 0


if __name__ == "__main__":                                        # pragma: no cover
    sys.exit(main())
