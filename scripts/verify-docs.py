"""저장소 문서의 사실 주장을 코드와 대조한다.

왜 있나: 문서가 코드와 어긋난 사례가 11건 확인됐다(ppt/99_MISSING_MATERIALS.md E절).
        낡은 문서를 옮겨 적으면 발표에서 사실 오류가 된다. 그래서 **코드가 정답**인 항목만
        여기에 모아 기계가 다시 센다.

쓰는 법: python scripts/verify-docs.py   (저장소 루트에서)
        종료코드 0 = 전부 통과, 1 = 하나라도 어긋남

넣는 기준: **코드/파일에서 기계로 확인 가능한 것만.** 설계 의도·평가·서술은 여기 못 들어온다.
          측정 원장(runs/)은 .gitignore 라 없을 수 있어 SKIP 으로 빠진다.
"""
import io
import json
import os
import re

import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS = []


def rel(*p):
    return os.path.join(ROOT, *p)


def check(name, expected, actual, note=""):
    ok = expected == actual
    RESULTS.append((ok, name, expected, actual, note))
    return ok


def skip(name, why):
    RESULTS.append((None, name, "-", "-", why))


def read(path, enc="utf-8"):
    with io.open(path, encoding=enc, errors="replace") as f:
        return f.read()


def count_files(pattern_dir, suffix):
    d = rel(*pattern_dir)
    if not os.path.isdir(d):
        return None
    return len([f for f in os.listdir(d) if f.endswith(suffix)])


def grep_count(pattern, paths, flags=0):
    """저장소 안에서 정규식 일치 건수. paths 는 (dir, ext) 목록."""
    n = 0
    rx = re.compile(pattern, flags)
    for d, ext in paths:
        base = rel(*d)
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [x for x in dirnames
                           if x not in ("__pycache__", "node_modules", "build", "dist", ".git")]
            for fn in filenames:
                if ext and not fn.endswith(ext):
                    continue
                try:
                    n += len(rx.findall(read(os.path.join(dirpath, fn))))
                except OSError:
                    pass
    return n


# ---------------------------------------------------------------- 1. 개수

# 04_DATA_MODEL.md, 07, 99
mig = rel("backend", "src", "main", "resources", "db", "migration")
migs = sorted(f for f in os.listdir(mig) if f.endswith(".sql"))
check("마이그레이션 파일 수 (04:V1-V21)", 21, len(migs))
nums = sorted(int(re.match(r"V(\d+)__", f).group(1)) for f in migs)
check("마이그레이션 최대 번호 (04: 다음은 V22)", 21, max(nums))
check("마이그레이션 번호 연속 (빠진 번호 없음)", list(range(1, 22)), nums)

tables = set()
for f in migs:
    for m in re.finditer(r"create\s+table\s+(?:if\s+not\s+exists\s+)?([a-zA-Z_]+)",
                         read(os.path.join(mig, f)), re.I):
        tables.add(m.group(1).lower())
check("테이블 수 (04: 57개)", 57, len(tables))

check("Java 마이그레이션 디렉터리 없음 (99 X-01)", False,
      os.path.isdir(rel("backend", "src", "main", "java", "db", "migration")))

# 02_FEATURE_SPEC.md
tt = read(rel("backend", "src", "main", "java", "com", "aivle", "backend",
              "taskrun", "domain", "TaskType.java"))
tt_vals = [x for x in re.findall(r"^\s{4}([A-Z][A-Z0-9_]*),?\s*$", tt, re.M)]
check("TaskType 종수 (02: 18종)", 18, len(tt_vals))

# 05, 06, 07
check("rules JSON 수 (05/06/07: 23개)", 23, count_files(
    ("ai", "app", "research", "research2", "rules"), ".json"))
tools_n = count_files(("ai", "app", "research", "research2", "tools"), ".py")
check("tools .py 수 (06: 24개 중 2개는 측정도구 아님 -> 22)", 24, tools_n)
check("eval.py 는 tools/ 가 아니라 research2 루트 (06)", True,
      os.path.isfile(rel("ai", "app", "research", "research2", "eval.py")))
check("tools/eval.py 는 없다 (06)", False,
      os.path.isfile(rel("ai", "app", "research", "research2", "tools", "eval.py")))

# 03_ARCHITECTURE.md
compose = read(rel("compose.yaml"))
svc_block = compose.split("services:", 1)[1].split("\nnetworks:", 1)[0] if "services:" in compose else ""
services = re.findall(r"^  ([a-z0-9-]+):", svc_block, re.M)
check("compose 서비스 수 (03: 6개)", 6, len(services))
check("compose 서비스 목록 (03)",
      ["postgres", "minio", "minio-init", "ai-server", "backend", "frontend"], services)

# 07 규모
check("컨트롤러 수 (02: 31개)", 31, grep_count(r"class \w*Controller\b", [(("backend", "src", "main"), ".java")]))
py_n = sum(len([f for f in fs if f.endswith(".py")])
           for _, ds, fs in os.walk(rel("ai", "app"))
           for _ in [ds.__setitem__(slice(None), [d for d in ds if d != "__pycache__"])])
check("ai/app 파이썬 파일 수 (07: 197개)", 197, py_n)

check("ai 테스트 함수 수 (07/10: 487개)", 487,
      grep_count(r"def test_", [(("ai", "tests"), ".py")]))
check("backend @Test 수 (07/10: 469개)", 469,
      grep_count(r"@Test\b", [(("backend", "src", "test"), ".java")]))

fe_tests = sum(1 for dp, dn, fn in os.walk(rel("frontEnd", "src"))
               for f in fn if re.search(r"\.test\.[jt]sx?$", f))
check("frontEnd 테스트 파일 수 (07/10: 70개)", 70, fe_tests)

# 10_TEST_AND_CI.md
tdb = json.loads(read(rel("frontEnd", "test-debt-baseline.json")))
check("test-debt allowedFailures (10: 22건)", 22, len(tdb["allowedFailures"]))
check("test-debt 만료일 (10: 2026-09-30)", "2026-09-30", tdb["expiresOn"])
pkg = json.loads(read(rel("frontEnd", "package.json")))
check("test:baseline 스크립트 존재 (10)", True, "test:baseline" in pkg.get("scripts", {}))

ci = read(rel(".github", "workflows", "ci.yml"))
check("CI 잡 3개 (10)", ["frontend", "ai", "backend"],
      re.findall(r"^  (frontend|ai|backend):", ci, re.M))
check("CI 에 deploy 잡 없음 (03 §7)", 0, len(re.findall(r"^  deploy", ci, re.M)))
check("workflow 파일 1개 (03 §7)", 1,
      len([f for f in os.listdir(rel(".github", "workflows")) if f.endswith((".yml", ".yaml"))]))

# 02 §2-2 컨셉 개수
api = read(rel("backend", "src", "main", "java", "com", "aivle", "backend", "pipeline",
               "conceptportfolio", "api", "ConceptPortfolioApiModels.java"))
check("maxConcepts 제약 @Min(1) @Max(5) (02)", True,
      bool(re.search(r"@Min\(1\)\s*@Max\(5\)\s*Integer maxConcepts", api)))
check("maxConcepts 기본값 5 (02)", True, "maxConcepts == null ? 5" in api)

# 03 §1 라우트
router = read(rel("frontEnd", "src", "app", "routing", "AppRouter.jsx"))
check("AppRouter 경로가 app/routing/ (99 X-03)", True,
      os.path.isfile(rel("frontEnd", "src", "app", "routing", "AppRouter.jsx")))
# ⚠ app/router/AppRouter.jsx 도 디스크에 있다. 쓰이는 것은 routing/ 쪽이다(App.jsx 가 import).
check("App.jsx 가 import 하는 것은 routing/ (99 X-10)", True,
      "./routing/AppRouter.jsx" in read(rel("frontEnd", "src", "app", "App.jsx")))
check("app/router/AppRouter.jsx 는 죽은 중복본으로 남아 있다 (99 X-10)", True,
      os.path.isfile(rel("frontEnd", "src", "app", "router", "AppRouter.jsx")))
check("죽은 중복본을 import 하는 곳 0곳 (99 X-10)", 0,
      grep_count(r"app/router/AppRouter|\./router/AppRouter|\.\./router/AppRouter",
                 [(("frontEnd", "src"), ".jsx"), (("frontEnd", "src"), ".js")]))
check("Route 선언 수 (02: 52개)", 52, len(re.findall(r"<Route", router)))
check("path= 지정 수 (02: 39개)", 39, len(re.findall(r'path="', router)))

# ---------------------------------------------------------------- 2. grep 음성 (0이어야 하는 것)

CODE = [(("ai",), ".py"), (("backend", "src"), ".java"),
        (("frontEnd", "src"), ".js"), (("frontEnd", "src"), ".jsx")]

check("RAG 흔적 0건 — embedding/pgvector/faiss/chroma/retriever (05/README)", 0,
      grep_count(r"\b(pgvector|faiss|chromadb|SentenceTransformer|retriever)\b", CODE, re.I))
check("파인튜닝 흔적 0건 — torch/transformers (00/06)", 0,
      grep_count(r"^\s*(import|from)\s+(torch|transformers)\b", CODE, re.M))
check("precision/recall/f1 계산 0건 (00/06)", 0,
      grep_count(r"\b(f1_score|precision_score|recall_score|classification_report)\b", CODE))
check("CONCEPT_TARGET_ELIGIBLE_COUNT 0건 (99 X-08)", 0,
      grep_count(r"CONCEPT_TARGET_ELIGIBLE_COUNT", CODE))
check("레거시 라우트 0건 — structured-plan/validate/feasibility 경로 (99 X-07)", 0,
      len(re.findall(r'path="(plan|structured-plan|documents|report|validate)', router)))

# 03 §5 — AS_BUILT 가 있다고 적은 파일들이 실제로 없다 (99 X-04)
for f in ["api/tasks.py", "api/marketing.py", "services/task_service.py", "services/banner_service.py"]:
    check("AS_BUILT §5 가 말한 %s 는 없다 (99 X-04)" % f, False,
          os.path.isfile(rel("ai", "app", *f.split("/"))))

# ---------------------------------------------------------------- 3. 서술형 주장 — 코드로 확인

# 03 §3-2 채택은 정확히 한 번
svc = rel("backend", "src", "main", "java", "com", "aivle", "backend", "taskrun")
adopt_src = ""
for dp, dn, fn in os.walk(svc):
    for f in fn:
        if f.endswith(".java"):
            adopt_src += read(os.path.join(dp, f))
check("adopt() 가 LATE_OR_DUPLICATE_RESULT 로 거부 (03 §3-2)", True,
      "LATE_OR_DUPLICATE_RESULT" in adopt_src)

# ⚠ 트랜잭션 가드와 금칙 필드 검사는 taskrun/ 이 아니라 journey/ 의 **개별 워커**에 있다.
#    AS_BUILT 는 "TaskRunWorker 가 한다"고 적었지만 그 클래스는 없다 (99 X-11).
guard_files = sorted(f for f in os.listdir(rel("backend", "src", "main", "java", "com", "aivle",
                                               "backend", "journey"))
                     if f.endswith(".java")
                     and "isActualTransactionActive" in read(rel("backend", "src", "main", "java",
                                                                 "com", "aivle", "backend",
                                                                 "journey", f)))
check("트랜잭션 가드를 가진 journey 클래스 (03 §3-3 — 전부가 아니라 3곳)",
      ["MarketResearchWorker.java", "TwinSurveyStimulusDraftService.java", "TwinSurveyWorker.java"],
      guard_files)
check("공용 TaskRunWorker 클래스는 없다 (03 §3-4, 99 X-11)", False,
      any("TaskRunWorker.java" == f
          for dp, dn, fn in os.walk(rel("backend", "src", "main")) for f in fn))

forb_files = sorted(f for f in os.listdir(rel("backend", "src", "main", "java", "com", "aivle",
                                              "backend", "journey"))
                    if f.endswith(".java")
                    and "FORBIDDEN_FIELDS" in read(rel("backend", "src", "main", "java", "com",
                                                       "aivle", "backend", "journey", f)))
check("금칙 필드 검사를 가진 journey 클래스 (03 §1 — 2곳뿐)",
      ["MarketResearchWorker.java", "TwinSurveyWorker.java"], forb_files)

# ⚠ 부동소수점은 **금지되지 않았다**. 거부되는 것은 비유한(NaN/Infinity)뿐이다 (99 X-09).
hasher = read(rel("backend", "src", "main", "java", "com", "aivle", "backend",
                  "taskrun", "service", "CanonicalInputHasher.java"))
check("유한 부동소수점은 허용된다 — 거부는 비유한만 (99 X-09)", True,
      "non-finite JSON number is not canonical task input" in hasher
      and "isFloatingPointNumber() && !Double.isFinite" in hasher.replace("\n", " "))
check("'부동소수점 금지' 문구는 코드에 없다 (99 X-09)", 0,
      grep_count(r"floating-point JSON numbers are not canonical", [(("backend", "src"), ".java")]))
check("AI 서버도 유한 숫자를 허용한다 (99 X-09)", True,
      "canonical JSON with finite numbers" in read(rel("ai", "app", "api", "executions.py")))

# 03 §5 Mock 없음
envx = read(rel(".env.example"))
check("AI_FIXTURE_MODE=false (03 §5)", True,
      bool(re.search(r"AI_FIXTURE_MODE\s*=\s*false", envx, re.I)))

# 03 §5 provider 제한
ai_all = ""
for dp, dn, fn in os.walk(rel("ai", "app")):
    dn[:] = [d for d in dn if d != "__pycache__"]
    for f in fn:
        if f.endswith(".py"):
            ai_all += read(os.path.join(dp, f))
check("provider 는 openai / openai-compatible 만 (03 §5)", True,
      "openai-compatible" in ai_all)
check("temperature 0.1 고정 (03 §5)", True,
      bool(re.search(r"temperature[\"']?\s*[:=]\s*0\.1", ai_all)))

# 03 §4 응답 12필드
check("성공 봉투 12필드 (03 §4)", 12, len(re.findall(
    r'"(contractVersion|taskType|taskSchemaVersion|taskRunId|taskAttemptId|correlationId|'
    r'canonicalInputHash|resultSchemaVersion|result|warnings|provenance|usage)"',
    re.search(r"SUCCESS_FIELDS[^;]*;", adopt_src, re.S).group(0)
    if re.search(r"SUCCESS_FIELDS[^;]*;", adopt_src, re.S) else "")))

# 05 §3 BM 9칸 라우팅
vocab_p = rel("ai", "app", "research", "research2", "harness", "vocab.json")
if os.path.isfile(vocab_p):
    vocab = json.loads(read(vocab_p))
    canvas = vocab.get("canvas", {})
    check("BM canvas 라우팅 표 존재 (05 §3)", True, bool(canvas))
else:
    skip("BM canvas 라우팅", "vocab.json 없음")

# 05 §1-4 성적표 7과목
fill = json.loads(read(rel("ai", "app", "research", "research2", "rules", "fill.v2.json")))
items = [k for k in fill.get("항목", {}) if re.match(r"^\d_", k)]
check("성적표 과목 수 (05 §1-4: 7과목)", 7, len(items))
check("7번 과목은 '항상' (05 §1-4)", "항상", fill["항목"]["7_못찾은것"]["문턱"])

# 06 §2 사전등록
exp = read(rel("ai", "app", "research", "research2", "expected.md"))
check("expected.md 에 alpha=.05/검정력 80% (06 §2-2)", True,
      ("α=.05" in exp or "alpha=.05" in exp) and "80%" in exp)
check("expected.md M1 대조군 0.091 (06 §2-3, 07 §5)", True, "0.091" in exp)

# ---------------------------------------------------------------- 4. 측정 원장 (gitignore 대상)

led = [
    ("funnel 기준선", ("ai", "app", "research", "research2", "runs-generated",
                     "smoke-collect-01", "funnel_before.json")),
    ("agreement", ("ai", "app", "research", "research2", "runs", "harness-agreement",
                   "agreement.json")),
]
for nm, p in led:
    fp = rel(*p)
    if not os.path.isfile(fp):
        skip(nm, ".gitignore 대상 — 다른 클론에는 없다")
        continue
    d = json.loads(read(fp))
    if nm == "funnel 기준선":
        check("funnel 절단 도달률 0.5413 (07 §2)", 0.5413, d["절단"]["도달률"])
        check("funnel extract 수율 0.0227 (07 §2)", 0.0227, d["어댑터"]["extract"]["수율"])
        check("funnel extract 발췌진입 44 (07 §2)", 44, d["어댑터"]["extract"]["발췌진입"])
        check("funnel 은 직접 측정이 아님 — note 역산 (07 §2, M-07)", "note 역산", d["_원천"])
    else:
        check("골든 대조 평균 0.678 (07 §3-1)", 0.678, d["요약"]["골든_대조_평균"])
        check("재생성 안정성 평균 0.845 (07 §3-1)", 0.8448, d["요약"]["재생성_안정성_평균"])

# ---------------------------------------------------------------- 5. 문서 지도 무결성
#
# 색인이 썩으면 그것도 거짓말이다. 라우팅 표가 가리키는 곳이 실제로 있는지 센다.

claude_md = read(rel("CLAUDE.md"))
check("CLAUDE.md 200줄 이하 (권장치)", True, len(claude_md.splitlines()) <= 200,
      "실제 %d줄" % len(claude_md.splitlines()))

# 층1 — 라우팅 표가 가리키는 경로가 전부 실재하는가
routed = sorted(set(re.findall(r"`(docs/[A-Za-z0-9_./-]+|ppt/[A-Za-z0-9_./-]+)`", claude_md)))
missing = [p for p in routed
           if not (os.path.isfile(rel(*p.split("/"))) or os.path.isdir(rel(*p.split("/"))))]
check("문서 지도가 가리키는 경로가 전부 실재 (층1)", [], missing)

# 층2 — 하위 CLAUDE.md (그 폴더 파일을 읽을 때 자동 로딩)
for d in [("backend",), ("ai",), ("frontEnd",), ("ai", "app", "research", "research2")]:
    check("하위 CLAUDE.md 존재: %s (층2)" % "/".join(d), True,
          os.path.isfile(rel(*d, "CLAUDE.md")))

# 층3 — 스킬. frontmatter 의 name/description 만 상주하므로 그 둘이 성립해야 한다
skills_dir = rel(".claude", "skills")
skills = sorted(os.listdir(skills_dir)) if os.path.isdir(skills_dir) else []
check("스킬 3개 (층3)", ["ai-module-integration", "doc-truth-check",
                          "market-research-discipline"], skills)
for s in skills:
    body = read(os.path.join(skills_dir, s, "SKILL.md"))
    fm = re.match(r"^---\n(.*?)\n---\n", body, re.S)
    check("스킬 frontmatter 있음: %s" % s, True, bool(fm))
    if not fm:
        continue
    head = fm.group(1)
    name = re.search(r"^name:\s*(\S+)", head, re.M)
    desc = re.search(r"^description:\s*(.+)$", head, re.M)
    check("스킬 name 이 폴더명과 같음: %s" % s, s, name.group(1) if name else None)
    # description 은 상주 비용이다. 1,536자를 넘으면 잘려서 호출 판단이 망가진다
    dlen = len(desc.group(1)) if desc else 0
    check("스킬 description 1536자 이하: %s" % s, True, 0 < dlen <= 1536,
          "실제 %d자" % dlen)

# ---------------------------------------------------------------- 출력

ok = sum(1 for r in RESULTS if r[0] is True)
bad = [r for r in RESULTS if r[0] is False]
sk = [r for r in RESULTS if r[0] is None]

out = sys.stdout
for r in RESULTS:
    if r[0] is True:
        out.write("PASS  %s\n" % r[1])
    elif r[0] is None:
        out.write("SKIP  %s  (%s)\n" % (r[1], r[4]))
    else:
        out.write("FAIL  %s\n        문서 주장: %r\n        실제     : %r\n" % (r[1], r[2], r[3]))

out.write("\n%d PASS / %d FAIL / %d SKIP  (총 %d)\n" % (ok, len(bad), len(sk), len(RESULTS)))
sys.exit(1 if bad else 0)
