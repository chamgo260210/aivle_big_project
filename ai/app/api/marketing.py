from typing import Annotated

from fastapi import (
    APIRouter,
    File,
    Form,
    HTTPException,
    Request,
    UploadFile,
)

from fastapi.concurrency import run_in_threadpool
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError

from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerRequest,
)

from app.services.openai_banner_service import (
    OpenAIBannerGenerationError,
    generate_banner_with_openai,
)
from app.services.prompt_service import build_banner_prompt
from app.utils.image_validator import read_and_validate_image

from app.services.marketing_copy_service import (
    MarketingCopyGenerationError,
    generate_marketing_copy,
)

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
    """마케팅 정보를 검증하고 업로드 이미지를 참고하여
    gpt-image-2로 광고 배너 이미지를 생성한다."""

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

    # 업로드 이미지 형식과 크기를 검증하고
    # 이미지 바이트를 읽는다.
    image_bytes = await read_and_validate_image(image)

    # gpt-4o-mini로 사용자 입력을
    # 광고 배너용 카피로 확장한다.
    try:
        marketing_copy = await run_in_threadpool(
            generate_marketing_copy,
            request_data,
        )

    except MarketingCopyGenerationError as error:
        raise HTTPException(
            status_code=502,
            detail=str(error),
        ) from error

    # 사용자 입력값을 이미지 생성 프롬프트로 변환한다.
    banner_prompt = build_banner_prompt(request_data)

    # gpt-image-2로 실제 배너 이미지를 생성한다.
    # OpenAI Python SDK는 동기 방식으로 호출되므로
    # FastAPI 이벤트 루프가 멈추지 않도록
    # 별도의 스레드에서 실행한다.
    try:
        generated_banner = await run_in_threadpool(
            generate_banner_with_openai,
            image_bytes=image_bytes,
            original_filename=(
                image.filename or "uploaded-image.jpg"
            ),
            prompt=banner_prompt,
            banner_format=request_data.banner_format,
            marketing_copy=marketing_copy,
        )

    except OpenAIBannerGenerationError as error:
        raise HTTPException(
            status_code=502,
            detail=str(error),
        ) from error
    
    # 저장된 생성 이미지의 접근 URL을 만든다.
    preview_url = (
        str(request.base_url).rstrip("/")
        + str(generated_banner["preview_path"])
    )

    # 처리 결과 반환
    return {
        "status": "completed",
        "message": "AI 배너를 생성했습니다.",
        "data": request_data.model_dump(
            mode="json"
        ),
        "prompt_preview": banner_prompt,
        "generated_copy": marketing_copy.model_dump(
            mode="json"
        ),
        "banner": {
            "banner_id": generated_banner[
                "banner_id"
            ],
            "preview_url": preview_url,
            "mock": generated_banner["mock"],
            "model": generated_banner["model"],
            "size": generated_banner["size"],
            "quality": generated_banner["quality"],
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