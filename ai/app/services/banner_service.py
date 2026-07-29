from pathlib import Path
from uuid import uuid4


# ai/outputs 폴더 경로
OUTPUT_DIRECTORY = (
    Path(__file__).resolve().parents[2]
    / "outputs"
)

OUTPUT_DIRECTORY.mkdir(
    parents=True,
    exist_ok=True
)


def create_mock_banner(
    image_bytes: bytes,
    original_filename: str
) -> dict:
    """업로드 이미지를 Mock 배너 결과로 저장합니다."""

    extension = Path(
        original_filename
    ).suffix.lower()

    banner_id = uuid4().hex
    output_filename = (
        f"banner_{banner_id}{extension}"
    )

    output_path = (
        OUTPUT_DIRECTORY
        / output_filename
    )

    output_path.write_bytes(image_bytes)

    return {
        "banner_id": banner_id,
        "filename": output_filename,
        "preview_path": (
            f"/outputs/{output_filename}"
        )
    }