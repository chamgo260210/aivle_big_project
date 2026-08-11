<#
.SYNOPSIS
    TWIN_SURVEY 실스택 스모크. 백엔드-AI 왕복을 실제로 태운다.

.DESCRIPTION
    통합 테스트도 컴포넌트 테스트도 구조적으로 못 보는 이음새가 있다:
    등록 분기, 900초 클라이언트 선택, 계약 검증, 뱅크 마운트, 경계 데이터의 생존.
    여기서만 잡힌다.

    기본은 **무료**다 — 거절 경로와 뱅크 점검은 LLM 을 한 번도 부르지 않는다.
    그런데도 배선 전체(라우팅·워커·클라이언트·오류 사상)를 지나간다.
    -Paid 를 주면 실제 조사(n=50·2쌍 = 약 400셀)를 태운다. **돈이 든다.**

.PARAMETER Paid
    실제 조사를 실행한다. AI_API_KEY 지갑에서 비용이 나간다.

.EXAMPLE
    pwsh -File scripts/twin-survey-smoke.ps1
    pwsh -File scripts/twin-survey-smoke.ps1 -Paid
#>
[CmdletBinding()]
param(
    [switch]$Paid,
    [ValidateSet(50, 100, 300)][int]$SampleSize = 50,
    [string]$BaseUrl = "http://localhost:3000",
    [int]$BudgetSeconds = 780
)

$ErrorActionPreference = "Stop"
# ⚠ **파이프로 native 프로세스에 보내는 본문도 UTF-8 이어야 한다.** PowerShell 5.1 의 기본
#   $OutputEncoding 은 ASCII 라 아래 파이썬 프로브의 한글이 «?» 로 뭉개진다. 그러면 자극의
#   속성이 양쪽 다 «?» 가 되어 게이트가 IDENTICAL 로 판정하고, 스모크는 **검사하는 척만** 한다.
#   Invoke-Json 이 본문을 바이트로 만들어 보내는 것과 같은 이유다(아래 주석 참조).
#   호출자가 pwsh 든 powershell.exe 든 여기서 못박는다.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step { param([string]$Text) Write-Output "`n== $Text" }
function Add-Failure { param([string]$Text) $failures.Add($Text); Write-Output "  FAIL $Text" }
function Write-Pass { param([string]$Text) Write-Output "  ok   $Text" }

function Invoke-Json {
    param([string]$Method, [string]$Uri, $Headers, $Body)
    # ⚠ **본문을 바이트로 만들어 보낸다.** PowerShell 5.1 의 Invoke-RestMethod 는 문자열
    #   본문을 ANSI 로 보내서 한글이 «????» 가 된다. 실측: 그 때문에 윤리·가치형 자극의
    #   속성 이름이 뭉개져 게이트를 그냥 통과했고, 막혔어야 할 조사가 실제로 돌았다
    #   (LLM 206회 = 돈). 스모크가 **경계를 검사하는 척만** 하게 되는 자리다.
    $arguments = @{ Method = $Method; Uri = $Uri; Headers = $Headers
                    ContentType = "application/json; charset=utf-8" }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        $arguments.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @arguments
}

# ── 0. 뱅크가 컨테이너에 붙어 있나 ────────────────────────────────────
Write-Step "bank mount"
$bankProbe = @'
import os
from app.twin.bank import load
print("TWIN_BANK_DIR=" + (os.getenv("TWIN_BANK_DIR") or "<unset>"))
cards, frame = load()
print("cards=%d frame=%d" % (len(cards), len(frame)))
'@
$bankOutput = $bankProbe | docker compose exec -T ai-server python -
Write-Output ($bankOutput | ForEach-Object { "  $_" })
if ($LASTEXITCODE -ne 0) {
    Add-Failure "뱅크가 붙어 있지 않다. compose 의 :ro 바인드와 TWIN_BANK_DIR 을 확인하라."
} else {
    Write-Pass "뱅크 로드"
}

# ── 1. 뱅크가 없으면 시끄럽게 죽나 ────────────────────────────────────
#     조용히 빈 표본으로 도는 것이 이 기능에서 가장 위험한 실패다.
Write-Step "bank unavailable"
$unavailableProbe = @'
import asyncio, os
os.environ.pop("TWIN_BANK_DIR", None)
from app.twin import execute_twin_survey
from app.providers import ProviderFailure
payload = {"situation": "가게에서 하나를 고릅니다.", "sampleSize": 50, "pairs": [{
    "pairId": "P1",
    "X": {"label": "A", "attrs": {"형태": "신선"}, "priceKrw": 4500},
    "Y": {"label": "B", "attrs": {"형태": "냉동"}, "priceKrw": 4500}}]}
try:
    asyncio.run(execute_twin_survey(payload, 60))
    print("NO_FAILURE")
except ProviderFailure as failure:
    print("reason=" + failure.reason)
'@
$unavailableOutput = ($unavailableProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output "  $unavailableOutput"
if ($unavailableOutput -match "TWIN_BANK_UNAVAILABLE") { Write-Pass "뱅크 미마운트 = 시끄러운 실패" }
else { Add-Failure "뱅크가 없는데 TWIN_BANK_UNAVAILABLE 이 아니다" }

# ── 1-2. 자극 초안이 판매 경계를 코드로 지키나 ────────────────────────
#     초안은 사용자가 첫 칸을 채우는 대신 고르는 자리다. 여기서 우열형이 아닌 쌍이
#     새 나가면, 사용자는 「AI 가 준 것」이라 믿고 고르고 조사는 그 뒤에 거절한다.
#     프롬프트로 부탁한 규칙은 회귀해도 조용하다 — 코드가 거르는지를 본다.
#     **무료다.** 제공자 호출을 가짜로 바꿔 LLM 을 한 번도 부르지 않는다.
Write-Step "stimulus draft gate"
$draftProbe = @'
import asyncio
from app.api.executions import TASK_TYPES
from app.providers import ProviderFailure
from app.twin import stimulus_draft
from app.twin.task_type import classify

print("registered=" + str("TWIN_STIMULUS_DRAFT" in TASK_TYPES))

CONCEPT = {"conceptName": "새벽 밀키트", "targetUsers": "1인 가구",
           "problemScenario": "장 볼 시간이 없다",
           "featureSet": ["보관 형태"], "differentiators": "신선 보관",
           "priceKrw": 9900}

def side(value):
    return {"label": value + " 안", "value": value}

def stub(pairs):
    async def prompt(*args, **kwargs):
        return {"situation": "가게에서 하나를 고릅니다.", "pairs": pairs}
    stimulus_draft.execute_structured_prompt = prompt

# (1) 우열형만 살아남나 — 윤리·가치형과 동일 쌍을 섞어 넣는다.
stub([{"axis": "보관 형태", "X": side("신선"), "Y": side("냉동"), "rationale": "묻는다"},
      {"axis": "친환경 인증", "X": side("있음"), "Y": side("없음"), "rationale": "묻는다"},
      {"axis": "배송", "X": side("새벽"), "Y": side("새벽"), "rationale": "묻는다"}])
result = asyncio.run(stimulus_draft.execute_twin_stimulus_draft(CONCEPT))
# 판정을 파워셸 문자열 비교에 맡기지 않는다 — 게이트에 다시 넣어 본다.
types = {classify({"pairId": p["pairId"], "X": p["X"], "Y": p["Y"]}).task_type
         for p in result["pairs"]}
print("kept=" + ",".join(p["axis"] for p in result["pairs"]))
print("onlyDominance=" + str(types == {"DOMINANCE"}))
print("dropped=" + ",".join(d["taskType"] for d in result["dropped"]))
print("equalPrice=" + str({p["X"]["priceKrw"] == p["Y"]["priceKrw"]
                           for p in result["pairs"]} == {True}))

# (2) 팔 수 있는 쌍이 0개면 정직하게 실패하나.
stub([{"axis": "친환경 인증", "X": side("있음"), "Y": side("없음"), "rationale": "묻는다"}])
try:
    asyncio.run(stimulus_draft.execute_twin_stimulus_draft(CONCEPT))
    print("empty=NO_FAILURE")
except ProviderFailure as failure:
    print("empty=" + failure.reason)
'@
$draftOutput = ($draftProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output ($draftOutput -split "`n" | ForEach-Object { "  $_" })
if ($draftOutput -match "registered=True") { Write-Pass "TASK_TYPES 등록" }
else { Add-Failure "TWIN_STIMULUS_DRAFT 가 TASK_TYPES 에 없다 — 등록 분기를 빠뜨렸다" }
if ($draftOutput -match "onlyDominance=True") { Write-Pass "초안이 우열형만 돌려준다" }
else { Add-Failure "초안에 우열형이 아닌 쌍이 섞였다" }
if ($draftOutput -match "dropped=ETHICAL_VALUE,IDENTICAL") { Write-Pass "버린 이유가 살아 있다" }
else { Add-Failure "버린 후보의 이유가 뭉개졌다" }
if ($draftOutput -match "equalPrice=True") { Write-Pass "가격이 양쪽 같다 — 지불의사가 만들어질 수 없다" }
else { Add-Failure "초안의 두 안에 다른 가격이 붙었다 — 가격형이 새 나간다" }
if ($draftOutput -match "empty=TWIN_STIMULUS_NO_SERVICEABLE_PAIR") {
    Write-Pass "0쌍 = 정직한 실패"
} else {
    Add-Failure "팔 수 있는 쌍이 0개인데 정직하게 실패하지 않았다"
}

# ── 2. 계정·프로젝트 ──────────────────────────────────────────────────
Write-Step "account"
$suffix = [guid]::NewGuid().ToString("N")
$username = "twin" + $suffix.Substring(0, 12)
$password = "Q7!" + $suffix.Substring(0, 20)
Invoke-Json POST "$BaseUrl/api/v1/auth/signup" @{} @{
    username = $username
    password = $password
    displayName = "Twin Smoke"
    email = "twin-$suffix@example.com"
    organizationName = $null; departmentName = $null; jobTitle = $null
} | Out-Null
# 가입은 토큰을 주지 않는다 — 로그인이 별도다. X-User-Id 헤더로는 통과하지 못한다.
$login = Invoke-Json POST "$BaseUrl/api/v1/auth/login" @{} @{ username = $username; password = $password }
$headers = @{ "Authorization" = "Bearer $($login.data.tokens.accessToken)" }
$project = Invoke-Json POST "$BaseUrl/api/v1/projects" $headers @{
    title = "Twin smoke " + $suffix.Substring(0, 8)
    description = "disposable twin survey smoke"
    industryCategory = "test"
}
$projectId = $project.data.id
Write-Pass "project $projectId"

function Wait-Terminal {
    param([int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $current = Invoke-Json GET "$BaseUrl/api/v2/projects/$projectId/twin-survey/current" $headers $null
        $state = $current.data.run.state
        if ($state -eq "SUCCEEDED" -or $state -eq "FAILED") { return $current.data }
        Start-Sleep -Seconds 3
    }
    return $null
}

# ── 2-2. 초안 엔드포인트가 실제로 서 있나 ────────────────────────────
#     여기서 도는 프로젝트에는 확정된 컨셉이 없다. 그러면 재료가 없으니 LLM 을 부르기 전에
#     404 로 막혀야 한다. **무료다.** 이 검사가 보는 것은 거절 자체가 아니라 그 앞의 배선이다
#     — 라우팅·인증·컨트롤러·서비스 진입. 트윈 조사에서 subjectId NOT NULL 을 잡은 것도
#     단위 테스트가 아니라 이 자리의 첫 POST 였다.
Write-Step "stimulus draft endpoint"
try {
    Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey/stimulus-draft" $headers @{} | Out-Null
    Add-Failure "컨셉이 없는데 초안이 만들어졌다 — 재료 없이 프롬프트가 나갔다"
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -eq 404) { Write-Pass "컨셉 없음 = 404 (LLM 0회)" }
    elseif ($status -eq 405 -or $status -eq 401) { Add-Failure "초안 엔드포인트가 서 있지 않다: HTTP $status" }
    else { Add-Failure "예상 밖 응답: HTTP $status" }
}

# ── 3. 윤리·가치형은 LLM 0회로 거절되나 ──────────────────────────────
#     영구 금지 유형이다. 실행 뒤에 거절하면 사용자는 기다린 뒤 빈손이 되고,
#     무엇보다 성적이 없는 유형에 돈이 나간다.
Write-Step "ethical stimulus refused"
$ethical = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey" $headers @{
    situation = "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
    sampleSize = 50
    pairs = @(@{
        pairId = "E1"
        X = @{ label = "인증 제품"; attrs = @{ "인증" = "친환경 인증" }; priceKrw = 4500 }
        Y = @{ label = "일반 제품"; attrs = @{ "인증" = "없음" }; priceKrw = 4500 }
    })
}
Write-Output "  enqueued taskRunId=$($ethical.data.taskRunId)"
$ethicalResult = Wait-Terminal 120
if ($null -eq $ethicalResult) {
    Add-Failure "윤리형 실행이 2분 안에 끝나지 않았다 — 거절은 즉시여야 한다(LLM 0회)"
} elseif ($ethicalResult.run.state -ne "FAILED") {
    Add-Failure "윤리형이 거절되지 않았다: $($ethicalResult.run.state) — 영구 금지 유형이다"
} elseif ($ethicalResult.run.errorCode -ne "TWIN_TASK_TYPE_NOT_SERVICEABLE") {
    # 「거절됐다」로는 부족하다. 이유가 살아 와야 화면이 왜 거절인지 말할 수 있다.
    # 실측: 이유가 화이트리스트에 없으면 AI_RESULT_INVALID 로 뭉개진다.
    Add-Failure "거절 이유가 뭉개졌다: $($ethicalResult.run.errorCode)"
} else {
    Write-Pass "윤리형 거절 errorCode=$($ethicalResult.run.errorCode)"
}

# ── 3-2. 가격형도 거절되나 ────────────────────────────────────────────
#     2026-08-10 차단. 계기 재측정에서 방향이 반전됐다(B3: CLI +0.23 / mini −0.68 / terra +1.00).
#     화면 게이트가 회귀해도 서버가 막아야 한다 — 그것을 여기서 확인한다.
Write-Step "price stimulus refused"
$price = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey" $headers @{
    situation = "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
    sampleSize = 50
    pairs = @(@{
        pairId = "B3"
        X = @{ label = "신선 냉장"; attrs = @{ "형태" = "신선(냉장)" }; priceKrw = 5000 }
        Y = @{ label = "냉동"; attrs = @{ "형태" = "냉동" }; priceKrw = 4500 }
    })
}
Write-Output "  enqueued taskRunId=$($price.data.taskRunId)"
$priceResult = Wait-Terminal 120
if ($null -eq $priceResult) {
    Add-Failure "가격형 실행이 2분 안에 끝나지 않았다 — 거절은 LLM 0회로 즉시여야 한다"
} elseif ($priceResult.run.state -ne "FAILED") {
    Add-Failure "가격형이 거절되지 않았다: $($priceResult.run.state)"
} elseif ($priceResult.run.errorCode -ne "TWIN_TASK_TYPE_NOT_SERVICEABLE") {
    Add-Failure "가격형 거절 이유가 뭉개졌다: $($priceResult.run.errorCode)"
} else {
    Write-Pass "가격형 거절 errorCode=$($priceResult.run.errorCode)"
}

# ── 4. 실제 조사 (유료) ───────────────────────────────────────────────
if (-not $Paid) {
    Write-Output "`n(유료 구간 건너뜀 — 실제 조사를 태우려면 -Paid)"
} else {
    Write-Step "paid survey n=$SampleSize pairs=2"
    $started = Get-Date
    $run = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey" $headers @{
        situation = "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
        sampleSize = $SampleSize
        # 둘 다 **우열형**이다 — 가격을 양쪽 같게 두고 속성 하나만 바꾼다.
        # 가격이 다른 쌍은 위 3-2 에서 거절되는 것을 이미 확인했다.
        pairs = @(
            @{ pairId = "P1"
               X = @{ label = "신선 냉장"; attrs = @{ "형태" = "신선(냉장)" }; priceKrw = 4500 }
               Y = @{ label = "냉동"; attrs = @{ "형태" = "냉동" }; priceKrw = 4500 } },
            @{ pairId = "P2"
               X = @{ label = "노르웨이산"; attrs = @{ "원산지" = "노르웨이산" }; priceKrw = 4500 }
               Y = @{ label = "칠레산"; attrs = @{ "원산지" = "칠레산" }; priceKrw = 4500 } }
        )
    }
    Write-Output "  enqueued taskRunId=$($run.data.taskRunId)"
    $done = Wait-Terminal $BudgetSeconds
    $spent = [int]((Get-Date) - $started).TotalSeconds

    if ($null -eq $done) {
        Add-Failure "예산 ${BudgetSeconds}초 안에 끝나지 않았다"
    } elseif ($done.run.state -ne "SUCCEEDED") {
        Add-Failure "실패했다: state=$($done.run.state) errorCode=$($done.run.errorCode)"
    } else {
        Write-Pass "COMPLETED in ${spent}s"
        $version = $done.version
        $result = $version.result

        # 응답 크기 — 셀 원장을 실으면 여기서 터진다.
        $bytes = ([System.Text.Encoding]::UTF8.GetBytes(($result | ConvertTo-Json -Depth 20 -Compress))).Length
        if ($bytes -ge 2MB) { Add-Failure "응답이 2 MiB 이상이다: $bytes" }
        else { Write-Pass "응답 $([math]::Round($bytes / 1KB, 1)) KiB" }

        # 경계는 **쌍마다** 있어야 한다. 계약이 이미 막지만 여기서 눈으로 본다.
        foreach ($pair in $result.pairs) {
            if (-not $pair.caveats -or $pair.caveats.Count -eq 0) {
                Add-Failure "$($pair.pairId): caveats 가 비었다"
            } else {
                Write-Pass "$($pair.pairId) $($pair.taskType) winner=$($pair.winner) measurable=$($pair.measurable) caveats=$($pair.caveats.Count)"
            }
            if ($pair.taskType -ne "DOMINANCE") {
                Add-Failure "$($pair.pairId): 우열형이 아닌 결과가 나왔다 — $($pair.taskType)"
            }

            # 인터뷰 — 화면이 «사람의 말»로 답하는 자리다. 비면 목업이 성립하지 않는다.
            $interviews = @($pair.interviews)
            if ($interviews.Count -eq 0) {
                Add-Failure "$($pair.pairId): interviews 가 비었다"
            } elseif ($interviews.Count -gt 5) {
                Add-Failure "$($pair.pairId): interviews 가 5장을 넘는다 ($($interviews.Count))"
            } else {
                $filled = 0
                foreach ($item in $interviews) {
                    if (-not $item.quote -or -not $item.quote.Trim()) {
                        Add-Failure "$($pair.pairId): 인용이 빈 인터뷰가 있다"
                    }
                    if ($item.quote -like "*선택:*") {
                        Add-Failure "$($pair.pairId): 인용에 «선택:» 줄이 남았다"
                    }
                    # 프로필 6칸이 실제로 채워지는지 — 파서가 조용히 죽으면 여기서 잡힌다.
                    $p = $item.profile
                    if ($p.age -and $p.gender -and $p.household -and $p.region -and $p.income -and $p.job) {
                        $filled++
                    }
                }
                $choices = ($interviews | ForEach-Object { $_.choice } | Sort-Object -Unique) -join ","
                Write-Pass "$($pair.pairId) 인터뷰 $($interviews.Count)장 · 프로필 6칸 완전 $filled 장 · 선택 [$choices]"
                if ($filled -eq 0) {
                    Add-Failure "$($pair.pairId): 프로필이 한 장도 채워지지 않았다 — 파서가 죽었다"
                }
            }
        }
        if ($version.caveatCount -le 0) { Add-Failure "물질화된 caveatCount 가 0이다" }
        Write-Output "  sampling: requested=$($result.sampling.requested) drawn=$($result.sampling.drawn)"
        Write-Output "  telemetry: cells=$($result.telemetry.cells) formatViolations=$($result.telemetry.formatViolations) failures=$($result.telemetry.failures)"
    }
}

# ── 결과 ──────────────────────────────────────────────────────────────
Write-Output ""
if ($failures.Count -gt 0) {
    Write-Output "TWIN SURVEY SMOKE FAILED ($($failures.Count))"
    $failures | ForEach-Object { Write-Output "- $_" }
    exit 1
}
Write-Output "TWIN SURVEY SMOKE PASSED"
