FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --chown=app:app main.py ./
COPY --chown=app:app app ./app
COPY --chown=app:app assets ./assets
# ⚠ **트윈 뱅크가 이미지 안에 있어야 한다.** 없으면 모듈 4(시장 인터뷰)가 실패하지 않고
#   «표본 0명»으로 조용히 성공한다 — 화면 경고도 0건이다(targeting.py 주석 참조).
#   런타임에 그렇게 새는 것보다 빌드에서 끊는 편이 낫다.
#
#   REQUIRE_TWIN_BANK=false 는 **열람 전용** 배포에만 쓴다. 트윈 뱅크는 KISDI 파생
#   재배포 금지 자산이라 넘겨줄 수 없는 상대가 있고, 그 사람도 «이미 나온 결과»는
#   봐야 하기 때문이다. 이 옵션으로 만든 이미지에서는 **모듈 4를 새로 돌리면 안 된다** —
#   막지 않고 0명으로 통과시킨다.
ARG REQUIRE_TWIN_BANK=true
RUN if [ "$REQUIRE_TWIN_BANK" = "true" ]; then \
      test -s app/twin/bank/twin_bank_manifest.json \
      || (echo "ERROR: ai/app/twin/bank 이 비어 있습니다. 트윈 뱅크를 채우거나, 열람 전용이면 --build-arg REQUIRE_TWIN_BANK=false 로 빌드하세요." >&2; exit 1); \
    else \
      echo "WARNING: 트윈 뱅크 없이 빌드합니다 — 열람 전용. 모듈 4를 실행하지 마세요."; \
    fi
RUN mkdir -p /app/outputs && chown app:app /app/outputs
USER app

EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=12 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/ready', timeout=2)"

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
