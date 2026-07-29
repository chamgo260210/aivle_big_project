from typing import Annotated

from fastapi import (
    APIRouter,
    File,
    Form,
    Request,
    UploadFile,
)
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError

from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerRequest,
)
from app.services.banner_service import create_mock_banner
from app.services.prompt_service import build_banner_prompt
from app.utils.image_validator import read_and_validate_image


router = APIRouter(
    prefix="/api/v1/marketing/banners",
    tags=["Marketing Banner"]
)


def parse_keywords(
    keyword_text: str
) -> list[str]:
    """쉼표로 입력된 키워드를 리스트로 변환합니다."""

    return [
        keyword.strip()
        for keyword in keyword_text.split(",")
        if keyword.strip()
    ]


@router.post("/generate")
async def generate_banner(
    request: Request,
    promotion_name: Annotated[
        str,
        Form(
            min_length=1,
            max_length=100
        )
    ],
    main_banner: Annotated[
        str,
        Form(
            min_length=1,
            max_length=80
        )
    ],
    supporting_copy: Annotated[
        str,
        Form(
            min_length=1,
            max_length=150
        )
    ],
    mood: Annotated[
        AdvertisingMood,
        Form()
    ],
    banner_format: Annotated[
        BannerFormat,
        Form()
    ],
    image: Annotated[
        UploadFile,
        File()
    ],
    emphasis_keywords: Annotated[
        str,
        Form()
    ] = ""
):
    """마케팅 배너 생성 요청을 처리합니다."""

    # 입력값 검증 및 요청 모델 생성
    try:
        request_data = MarketingBannerRequest(
            promotion_name=promotion_name,
            main_banner=main_banner,
            supporting_copy=supporting_copy,
            mood=mood,
            banner_format=banner_format,
            emphasis_keywords=parse_keywords(
                emphasis_keywords
            )
        )

    except ValidationError as error:
        raise RequestValidationError(
            error.errors()
        ) from error

    # 입력값을 이미지 생성 프롬프트로 변환
    banner_prompt = build_banner_prompt(
        request_data
    )

    # 업로드 이미지 형식과 용량 검사
    image_bytes = await read_and_validate_image(
        image
    )

    # 실제 AI 대신 Mock 배너 이미지 생성
    mock_banner = create_mock_banner(
        image_bytes=image_bytes,
        original_filename=(
            image.filename
            or "image.png"
        )
    )

    # 생성된 Mock 이미지의 전체 주소 생성
    preview_url = (
        str(request.base_url).rstrip("/")
        + mock_banner["preview_path"]
    )

    # 처리 결과 반환
    return {
        "status": "completed",
        "message": "Mock 배너를 생성했습니다.",
        "data": request_data.model_dump(
            mode="json"
        ),
        "prompt_preview": banner_prompt,
        "banner": {
            "banner_id": mock_banner[
                "banner_id"
            ],
            "preview_url": preview_url,
            "mock": True
        },
        "image": {
            "original_filename": (
                image.filename
            ),
            "content_type": (
                image.content_type
            ),
            "size": len(image_bytes)
        }
    }