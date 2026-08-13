import os
from fastapi import APIRouter, Header, HTTPException
from app.tasks.tech_ops_advisor import generate_tech_ops_advisory
from app.tasks.tech_ops_advisor.models import AdvisoryInput, AdvisoryResult

router = APIRouter(prefix="/internal/v1/tech-ops", tags=["TechOps advisory AI"])

@router.post("/advisory", response_model=AdvisoryResult)
async def advisory(body: AdvisoryInput, x_internal_api_key: str | None = Header(default=None)):
    if not os.getenv("AI_INTERNAL_SERVICE_TOKEN", "") or x_internal_api_key != os.getenv("AI_INTERNAL_SERVICE_TOKEN"):
        raise HTTPException(status_code=401, detail="invalid internal credential")
    return AdvisoryResult.model_validate(await generate_tech_ops_advisory(body.model_dump(mode="json")))
