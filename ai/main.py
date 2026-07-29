from fastapi import FastAPI
from pydantic import BaseModel
from pathlib import Path

from app.api.marketing import router as marketing_router
from fastapi.staticfiles import StaticFiles


app = FastAPI(
    title="AIVLE Test AI Server",
    version="0.1.0"
)

output_directory = (
    Path(__file__).resolve().parent
    / "outputs"
)

output_directory.mkdir(
    parents=True,
    exist_ok=True
)

app.mount(
    "/outputs",
    StaticFiles(
        directory=str(output_directory)
    ),
    name="outputs"
)

app.include_router(marketing_router)

# AI 서버 실행 상태 확인
@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "ai-server"
    }


class TestRequest(BaseModel):
    message: str


# Spring Boot가 보낸 값을 그대로 돌려주는 테스트 API
@app.post("/api/v1/test")
def connection_test(request: TestRequest):
    return {
        "success": True,
        "received_message": request.message,
        "reply": f"AI 서버가 '{request.message}'를 정상적으로 받았습니다."
    }