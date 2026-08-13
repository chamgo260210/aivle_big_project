"""재코딩 하니스 — 원장의 응답을 그대로 두고 **코딩만** 다시 돌린다.

```
cd ai
python -m app.tools.recode_ledger <원장.jsonl>
python -m app.tools.recode_ledger <원장.jsonl> --stored          # 호출 0회, 저장된 것만 읽는다
python -m app.tools.recode_ledger <원장.jsonl> --diff <다른.jsonl>
```

응답 수집을 다시 하지 않으므로 유료 호출이 n+2 회에서 **⌈n/40⌉+1 회**로 떨어진다.
n=40 이면 42회가 2회다.

판정은 진단표 한 장으로 한다. 「40/40 이 사라졌나」를 묻는 자리이므로 파이프라인과 **같은**
`saturation.homogeneity` 를 쓴다 — 잣대가 갈리면 비교가 무의미하다.

⚠ 이 저장소의 측정 규율: **한 번에 하나만 바꾼다.** 코딩 프롬프트와 배치 크기를 같이 바꾸면
무엇이 들었는지 못 가린다.
"""

import argparse
import asyncio
import sys

from app.interview.coding import code_responses
from app.interview.ledger import read
from app.interview.models import AXES
from app.interview.saturation import homogeneity


def _table(themes: list[dict], alternatives: list[dict], answered: int) -> str:
    report = homogeneity(themes, alternatives, answered)
    lines = [f"응답자 {answered}명", "", f"{'축':<16}{'이름표':>6}{'최대 언급':>10}"]
    for axis in AXES:
        lines.append(f"{axis:<16}{report['axisLabelCounts'].get(axis, 0):>6}"
                     f"{report['maxMentionByAxis'].get(axis, 0):>10}")
    lines.append("")
    lines.append(f"대안 언급 합계 {report['alternativeSum']} / 응답자 {answered}"
                 + ("  ⚠ 1인 1대안이 깨졌다" if report["alternativeSum"] > answered else "  OK"))
    if report["saturatedThemes"]:
        lines.append("")
        lines.append("⚠ 포화 — 전원이 들었거나 이름표가 하나뿐인 축:")
        lines.extend(f"   {row}" for row in report["saturatedThemes"])
    else:
        lines.append("포화 없음")
    return "\n".join(lines)


def _stored(path: str) -> str:
    meta, answers, _profiles, coding = read(path)
    if not coding:
        return f"{path}: 코딩 줄이 없다"
    return _table(coding.get("themes", []), coding.get("alternatives", []),
                  meta.get("answered") or len(answers))


async def _recode(path: str) -> str:
    meta, answers, _profiles, _coding = read(path)
    if not answers:
        return f"{path}: 응답 줄이 없다"
    coded = await code_responses(meta["board"], answers, 300.0)
    themes = [{"axis": t["axis"], "label": t["label"],
               "mentionCount": len(t["respondentIds"])} for t in coded.themes]
    alternatives = [{"label": a["label"], "mentionCount": len(a["respondentIds"])}
                    for a in coded.alternatives]
    body = _table(themes, alternatives, len(answers))
    detail = "\n".join(f"   {t['axis']:<15} {t['label'][:34]:<36} {t['mentionCount']:>3}명"
                       for t in sorted(themes, key=lambda t: (AXES.index(t["axis"]),
                                                              -t["mentionCount"])))
    return f"{body}\n\n주제 전체:\n{detail}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="원장의 응답으로 코딩만 다시 돌린다")
    parser.add_argument("ledger")
    parser.add_argument("--stored", action="store_true",
                        help="다시 돌리지 않고 원장에 저장된 코딩의 진단표만 낸다 (호출 0회)")
    parser.add_argument("--diff", metavar="다른원장",
                        help="두 원장의 저장된 진단표를 나란히 낸다 (호출 0회)")
    args = parser.parse_args(argv)

    if args.diff:
        print(f"── {args.ledger}\n{_stored(args.ledger)}\n")
        print(f"── {args.diff}\n{_stored(args.diff)}")
        return 0
    print(_stored(args.ledger) if args.stored else asyncio.run(_recode(args.ledger)))
    return 0


if __name__ == "__main__":                              # pragma: no cover
    sys.exit(main())
