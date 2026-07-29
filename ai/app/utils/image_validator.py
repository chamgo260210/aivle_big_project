from pathlib import Path

from fastapi import HTTPException, UploadFile, status


# 확장자별로 허용할 Content-Type
ALLOWED_IMAGE_TYPES = {
    ".png": {"image/png"},
    ".jpg": {"image/jpeg"},
    ".jpeg": {"image/jpeg"},
    ".webp": {"image/webp"},
}

# 최대 이미지 크기: 10MB
MAX_IMAGE_SIZE = 10 * 1024 * 1024

# 이미지를 한 번에 읽지 않기 위한 단위
READ_CHUNK_SIZE = 1024 * 1024


async def read_and_validate_image(
    image: UploadFile
) -> bytes:
    """이미지 형식과 용량을 검사한 후 바이트 데이터를 반환합니다."""

    filename = image.filename or ""
    extension = Path(filename).suffix.lower()
    content_type = image.content_type or ""

    # 허용하지 않는 확장자 검사
    if extension not in ALLOWED_IMAGE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=(
                "PNG, JPG, JPEG, WEBP 이미지만 "
                "업로드할 수 있습니다."
            )
        )

    # 확장자와 Content-Type 일치 여부 검사
    if content_type not in ALLOWED_IMAGE_TYPES[extension]:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="이미지 확장자와 Content-Type이 일치하지 않습니다."
        )

    image_data = bytearray()

    # 이미지가 10MB를 넘는지 확인
    while chunk := await image.read(READ_CHUNK_SIZE):
        image_data.extend(chunk)

        if len(image_data) > MAX_IMAGE_SIZE:
            await image.seek(0)

            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="이미지 크기는 최대 10MB까지 허용됩니다."
            )

    # 이후 과정에서 다시 읽을 수 있도록 위치 초기화
    await image.seek(0)

    if not image_data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="빈 이미지 파일은 업로드할 수 없습니다."
        )

    return bytes(image_data)