# -*- coding: utf-8 -*-
"""`validate_text_contents` — **정상 봉투**를 태우는 자리.

⚠ 이 파일이 왜 있나. 2026-08-16 main 병합에서 `executions.py` 의 `import hashlib` 이
사라졌는데 **1,039개 테스트가 전부 초록이었다.** 형식이 틀린 봉투는 해시를 계산하기 «전»에
조기 반환돼 살아남고, 해시 줄까지 도달하는 것은 **정상 봉투뿐**이라서다. 즉 기존 테스트가
전부 「거부해야 하는 입력」만 태우고 있었다.

그래서 여기서는 **통과해야 하는 입력**을 태운다. `BUSINESS_VALIDATION` 이 타는 유일한
검사기이고, 이게 죽으면 사업 검증 정상 요청이 500 으로 죽는다.
"""
import hashlib

from app.api.executions import validate_text_contents


def _content(key: str, text: str) -> dict:
    digest = "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()
    return {
        "contentKey": key,
        "contentType": "TEXT",
        "language": "ko-KR",
        "totalCharacters": len(text),
        "contentHash": digest,
        "chunks": [{"index": 0, "text": text, "characterCount": len(text),
                    "chunkHash": digest}],
    }


def test_정상_봉투는_통과한다():
    """None 을 돌려줘야 한다. 예외가 나면 그것이 곧 500 이다."""
    envelope = {"textContents": [_content("concept", "1인 가구 대상 소분 냉동 간편식 구독.")]}
    assert validate_text_contents(envelope) is None


def test_여러_조각도_이어_붙여_해시를_맞춘다():
    head, tail = "앞 조각이다. ", "뒤 조각이다."
    joined = head + tail
    digest = "sha256:" + hashlib.sha256(joined.encode("utf-8")).hexdigest()
    envelope = {"textContents": [{
        "contentKey": "concept", "contentType": "TEXT", "language": "ko-KR",
        "totalCharacters": len(joined), "contentHash": digest,
        "chunks": [
            {"index": 0, "text": head, "characterCount": len(head),
             "chunkHash": "sha256:" + hashlib.sha256(head.encode("utf-8")).hexdigest()},
            {"index": 1, "text": tail, "characterCount": len(tail),
             "chunkHash": "sha256:" + hashlib.sha256(tail.encode("utf-8")).hexdigest()},
        ],
    }]}
    assert validate_text_contents(envelope) is None


def test_조각_해시가_틀리면_거부한다():
    """거부 경로도 해시를 «계산»한다 — 여기도 hashlib 이 없으면 죽는다."""
    content = _content("concept", "본문이다.")
    content["chunks"][0]["chunkHash"] = "sha256:" + "0" * 64
    assert validate_text_contents({"textContents": [content]}) == "HASH_MISMATCH"
