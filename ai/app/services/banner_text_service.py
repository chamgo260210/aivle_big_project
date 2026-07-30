from io import BytesIO
from pathlib import Path

from PIL import (
    Image,
    ImageDraw,
    ImageFont,
    UnidentifiedImageError,
)

from app.models.marketing import BannerFormat


AI_ROOT_DIR = Path(__file__).resolve().parents[2]

FONT_DIR = AI_ROOT_DIR / "assets" / "fonts"

BOLD_FONT_PATH = FONT_DIR / "NotoSansKR-Bold.ttf"
REGULAR_FONT_PATH = FONT_DIR / "NotoSansKR-Regular.ttf"


class BannerTextCompositionError(RuntimeError):
    """배너 문구 합성 과정에서 발생한 오류."""

    pass


def _load_font(
    font_path: Path,
    size: int,
) -> ImageFont.FreeTypeFont:
    """지정된 한글 폰트를 불러온다."""

    if not font_path.exists():
        raise BannerTextCompositionError(
            f"폰트 파일을 찾을 수 없습니다: {font_path}"
        )

    return ImageFont.truetype(
        str(font_path),
        size=size,
    )


def _get_text_width(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
) -> int:
    """문구의 실제 픽셀 너비를 계산한다."""

    left, _, right, _ = draw.textbbox(
        (0, 0),
        text,
        font=font,
    )

    return right - left


def _wrap_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
    max_width: int,
) -> list[str]:
    """
    문구를 지정된 픽셀 너비에 맞춰 줄바꿈한다.
    한글도 정확하게 줄바꿈할 수 있도록 글자 단위로 계산한다.
    """

    normalized_text = " ".join(text.split())

    if not normalized_text:
        return [""]

    lines: list[str] = []
    current_line = ""

    for character in normalized_text:
        candidate = current_line + character

        if _get_text_width(draw, candidate, font) <= max_width:
            current_line = candidate
            continue

        if current_line.strip():
            lines.append(current_line.strip())

        current_line = character.lstrip()

    if current_line.strip():
        lines.append(current_line.strip())

    return lines


def _fit_multiline_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font_path: Path,
    max_width: int,
    max_lines: int,
    maximum_size: int,
    minimum_size: int,
) -> tuple[ImageFont.FreeTypeFont, list[str], int]:
    """
    문구가 지정된 줄 수 안에 들어오도록
    폰트 크기를 자동으로 줄인다.
    """

    for font_size in range(
        maximum_size,
        minimum_size - 1,
        -2,
    ):
        font = _load_font(font_path, font_size)

        lines = _wrap_text(
            draw=draw,
            text=text,
            font=font,
            max_width=max_width,
        )

        if len(lines) <= max_lines:
            line_bbox = draw.textbbox(
                (0, 0),
                "한글Ag",
                font=font,
            )

            line_height = line_bbox[3] - line_bbox[1]

            return font, lines, line_height

    # 최소 글자 크기에서도 지정된 줄 수를 넘으면
    # 마지막 줄 끝에 말줄임표를 붙인다.
    font = _load_font(font_path, minimum_size)

    all_lines = _wrap_text(
        draw=draw,
        text=text,
        font=font,
        max_width=max_width,
    )

    lines = all_lines[:max_lines]

    if len(all_lines) > max_lines and lines:
        last_line = lines[-1]

        while (
            last_line
            and _get_text_width(
                draw,
                last_line + "…",
                font,
            )
            > max_width
        ):
            last_line = last_line[:-1]

        lines[-1] = last_line.rstrip() + "…"

    line_bbox = draw.textbbox(
        (0, 0),
        "한글Ag",
        font=font,
    )

    line_height = line_bbox[3] - line_bbox[1]

    return font, lines, line_height


def _get_text_panel(
    width: int,
    height: int,
    banner_format: BannerFormat,
) -> tuple[int, int, int, int]:
    """
    배너 형식별 문구 영역을 반환한다.

    가로형은 왼쪽,
    정사각형과 세로형은 위쪽에 문구를 배치한다.
    """

    if banner_format == BannerFormat.LANDSCAPE:
        return (
            int(width * 0.045),
            int(height * 0.12),
            int(width * 0.60),
            int(height * 0.88),
        )

    if banner_format == BannerFormat.SQUARE:
        return (
            int(width * 0.055),
            int(height * 0.07),
            int(width * 0.945),
            int(height * 0.58),
        )

    return (
        int(width * 0.055),
        int(height * 0.06),
        int(width * 0.945),
        int(height * 0.48),
    )


def add_text_to_banner(
    *,
    image_bytes: bytes,
    badge:str,
    headline:str,
    subheadline:str,
    cta:str,
    banner_format: BannerFormat,
) -> bytes:
    """
    AI가 생성한 배경 이미지 위에
    프로모션 이름, 메인 문구, 보조 문구를 합성한다.
    """

    try:
        source_image = Image.open(
            BytesIO(image_bytes)
        ).convert("RGBA")

    except (UnidentifiedImageError, OSError) as error:
        raise BannerTextCompositionError(
            "문구를 합성할 이미지가 올바르지 않습니다."
        ) from error

    width, height = source_image.size
    minimum_edge = min(width, height)

    # 반투명 문구 패널을 만든다.
    overlay = Image.new(
        "RGBA",
        source_image.size,
        (0, 0, 0, 0),
    )

    overlay_draw = ImageDraw.Draw(overlay)

    panel = _get_text_panel(
        width=width,
        height=height,
        banner_format=banner_format,
    )

    panel_radius = max(
        20,
        int(minimum_edge * 0.025),
    )

    overlay_draw.rounded_rectangle(
        panel,
        radius=panel_radius,
        fill=(12, 20, 32, 165),
    )

    composed_image = Image.alpha_composite(
        source_image,
        overlay,
    )

    draw = ImageDraw.Draw(composed_image)

    panel_left, panel_top, panel_right, _ = panel

    padding = max(
        30,
        int(minimum_edge * 0.045),
    )

    text_x = panel_left + padding
    text_y = panel_top + padding

    content_width = (
        panel_right
        - panel_left
        - padding * 2
    )

    # 왼쪽 포인트 선
    accent_width = max(
        6,
        int(minimum_edge * 0.008),
    )

    draw.rounded_rectangle(
        (
            text_x,
            text_y,
            text_x + accent_width,
            text_y + int(minimum_edge * 0.06),
        ),
        radius=accent_width // 2,
        fill=(78, 220, 190, 255),
    )

    # 프로모션 이름
    promotion_font, promotion_lines, promotion_height = (
        _fit_multiline_text(
            draw=draw,
            text=badge,
            font_path=BOLD_FONT_PATH,
            max_width=content_width - accent_width - 20,
            max_lines=1,
            maximum_size=int(minimum_edge * 0.034),
            minimum_size=20,
        )
    )

    draw.text(
        (
            text_x + accent_width + 18,
            text_y,
        ),
        promotion_lines[0],
        font=promotion_font,
        fill=(105, 240, 210, 255),
    )

    text_y += (
        promotion_height
        + int(minimum_edge * 0.065)
    )

    # 메인 문구
    main_font, main_lines, main_line_height = (
        _fit_multiline_text(
            draw=draw,
            text=headline,
            font_path=BOLD_FONT_PATH,
            max_width=content_width,
            max_lines=3,
            maximum_size=int(minimum_edge * 0.082),
            minimum_size=38,
        )
    )

    main_line_spacing = max(
        10,
        int(main_line_height * 0.22),
    )

    for line in main_lines:
        draw.text(
            (text_x, text_y),
            line,
            font=main_font,
            fill=(255, 255, 255, 255),
            stroke_width=1,
            stroke_fill=(0, 0, 0, 130),
        )

        text_y += (
            main_line_height
            + main_line_spacing
        )

    text_y += int(minimum_edge * 0.04)

    # 보조 문구
    supporting_font, supporting_lines, supporting_line_height = (
        _fit_multiline_text(
            draw=draw,
            text=subheadline,
            font_path=REGULAR_FONT_PATH,
            max_width=content_width,
            max_lines=2,
            maximum_size=int(minimum_edge * 0.038),
            minimum_size=22,
        )
    )

    supporting_spacing = max(
        8,
        int(supporting_line_height * 0.25),
    )

    for line in supporting_lines:
        draw.text(
            (text_x, text_y),
            line,
            font=supporting_font,
            fill=(230, 235, 242, 255),
        )

        text_y += (
            supporting_line_height
            + supporting_spacing
        )

        # CTA 버튼
    text_y += int(minimum_edge * 0.035)

    cta_font, cta_lines, _ = _fit_multiline_text(
        draw=draw,
        text=cta,
        font_path=BOLD_FONT_PATH,
        max_width=content_width,
        max_lines=1,
        maximum_size=int(minimum_edge * 0.032),
        minimum_size=20,
    )

    cta_text = cta_lines[0]

    cta_bbox = draw.textbbox(
        (0, 0),
        cta_text,
        font=cta_font,
    )

    cta_text_width = cta_bbox[2] - cta_bbox[0]
    cta_text_height = cta_bbox[3] - cta_bbox[1]

    button_padding_x = max(
        24,
        int(minimum_edge * 0.028),
    )

    button_padding_y = max(
        12,
        int(minimum_edge * 0.014),
    )

    button_width = (
        cta_text_width
        + button_padding_x * 2
    )

    button_height = (
        cta_text_height
        + button_padding_y * 2
    )

    button_box = (
        text_x,
        text_y,
        text_x + button_width,
        text_y + button_height,
    )

    draw.rounded_rectangle(
        button_box,
        radius=button_height // 2,
        fill=(78, 220, 190, 255),
    )

    # textbbox의 상단 여백을 고려해
    # 버튼 중앙에 글자를 배치한다.
    cta_text_x = text_x + button_padding_x

    cta_text_y = (
        text_y
        + button_padding_y
        - cta_bbox[1]
    )

    draw.text(
        (cta_text_x, cta_text_y),
        cta_text,
        font=cta_font,
        fill=(10, 28, 38, 255),
    )

    # 최종 결과를 JPEG 바이트로 반환한다.
    output_buffer = BytesIO()

    composed_image.convert("RGB").save(
        output_buffer,
        format="JPEG",
        quality=94,
        optimize=True,
    )

    return output_buffer.getvalue()