"""뱅크를 읽을 때 **잘린 줄 하나가 8,595명을 막지 않는다.** (2026-08-15)

실제로 겪은 일이라 남긴다. 뱅크 8,596줄 중 41행이 문장 한가운데서 잘려 있었고,
`json.loads` 가 거기서 터져 **뱅크 전체가 안 읽혔다.** 화면에는 「카드 뱅크가 서버에 붙어
있지 않다」가 떴는데 뱅크는 멀쩡히 붙어 있었다 — 원인과 문구가 어긋나 진단이 한참 늦었다.

지키는 것은 둘이다. **한 줄 썩었다고 조사를 막지 않는다**, 그리고
**많이 썩으면 반드시 막는다.** 뒤가 없으면 표본이 조용히 갈린 채로 조사가 계속된다.
"""

import csv
import json

import pytest

from app.providers import ProviderFailure
from app.twin import bank


@pytest.fixture(autouse=True)
def _clear_cache():
    bank._cache = None
    yield
    bank._cache = None


def _write(directory, cards, damaged=()):
    path = directory / bank.CARDS_FILE
    with open(path, "w", encoding="utf-8") as handle:
        for pid in cards:
            handle.write(json.dumps({"pid_hash": pid, "text": f"{pid} 카드 본문"},
                                    ensure_ascii=False) + "\n")
        for line in damaged:
            handle.write(line + "\n")
    with open(directory / bank.FRAME_FILE, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["pid_hash", "gender", "band"])
        writer.writeheader()
        for pid in cards:
            writer.writerow({"pid_hash": pid, "gender": "여", "band": "30대"})
    return path


def test_a_truncated_line_is_skipped_and_the_rest_of_the_bank_survives(tmp_path, monkeypatch):
    # 실제 41행과 같은 모양 — 문자열이 닫히기 전에 줄이 끝난다.
    _write(tmp_path, [f"p{i}" for i in range(200)],
           damaged=['{"pid_hash": "pX", "text": "잘린 문장이다'])
    monkeypatch.setenv("TWIN_BANK_DIR", str(tmp_path))

    cards, frame = bank.load()

    assert len(cards) == 200
    assert len(frame) == 200
    assert "pX" not in cards


def test_a_bank_that_is_mostly_damaged_still_fails_loudly(tmp_path, monkeypatch):
    # 1% 를 넘으면 막는다. 반쯤 썩은 뱅크로 도는 조사는 표본이 조용히 갈린 것이다.
    _write(tmp_path, [f"p{i}" for i in range(50)],
           damaged=['{"pid_hash": "bad%d", "text": "잘린' % i for i in range(20)])
    monkeypatch.setenv("TWIN_BANK_DIR", str(tmp_path))

    with pytest.raises(ProviderFailure) as failure:
        bank.load()

    assert failure.value.reason == "TWIN_BANK_UNAVAILABLE"


def test_a_clean_bank_reports_no_damage(tmp_path, monkeypatch):
    _write(tmp_path, [f"p{i}" for i in range(10)])
    monkeypatch.setenv("TWIN_BANK_DIR", str(tmp_path))

    cards, frame = bank.load()

    assert len(cards) == 10 and len(frame) == 10
