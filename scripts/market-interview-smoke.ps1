<#
.SYNOPSIS
    MARKET_INTERVIEW 실스택 스모크. 백엔드-AI 왕복을 실제로 태운다.

.DESCRIPTION
    통합 테스트도 컴포넌트 테스트도 구조적으로 못 보는 이음새가 있다:
    등록 분기, 긴 타임아웃 클라이언트 선택, 계약 검증, 뱅크 마운트, 경계 데이터의 생존.
    여기서만 잡힌다.

    기본은 **무료**다 — 거절 경로와 뱅크 점검은 LLM 을 한 번도 부르지 않는다.
    그런데도 배선 전체(라우팅·워커·클라이언트·오류 사상)를 지나간다.
    -Paid 를 주면 실제 인터뷰(n=20 = 20셀 + 주제 코딩 1회)를 태운다. **돈이 든다.**

.PARAMETER Paid
    실제 인터뷰를 실행한다. AI_API_KEY 지갑에서 비용이 나간다.

.EXAMPLE
    pwsh -File scripts/market-interview-smoke.ps1
    pwsh -File scripts/market-interview-smoke.ps1 -Paid
#>
[CmdletBinding()]
param(
    [switch]$Paid,
    [ValidateSet(20, 40, 80)][int]$SampleSize = 20,
    [string]$BaseUrl = "http://localhost:3000",
    [int]$BudgetSeconds = 660
)

$ErrorActionPreference = "Stop"
# 파이프로 native 프로세스에 보내는 본문도 UTF-8 이어야 한다. PowerShell 5.1 의 기본
# $OutputEncoding 은 ASCII 라 아래 파이썬 프로브의 한글이 «?» 로 뭉개진다. 그러면 컨셉보드가
# 통째로 «?» 가 되어 검사가 **검사하는 척만** 한다. 트윈 스모크에서 실제로 겪었다.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step { param([string]$Text) Write-Output "`n== $Text" }
function Add-Failure { param([string]$Text) $failures.Add($Text); Write-Output "  FAIL $Text" }
function Write-Pass { param([string]$Text) Write-Output "  ok   $Text" }

function Invoke-Json {
    param([string]$Method, [string]$Uri, $Headers, $Body)
    # 본문을 바이트로 만들어 보낸다. PowerShell 5.1 의 Invoke-RestMethod 는 문자열 본문을
    # ANSI 로 보내서 한글이 «????» 가 된다 — 컨셉보드가 뭉개지면 응답자는 물음표를 읽는다.
    $arguments = @{ Method = $Method; Uri = $Uri; Headers = $Headers
                    ContentType = "application/json; charset=utf-8"
                    UseBasicParsing = $true }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        $arguments.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    # ⚠ **응답도 UTF-8 로 «직접» 읽는다.** `Invoke-RestMethod` 는 응답 헤더에 charset 이
    #   없으면 PowerShell 5.1 에서 본문을 ISO-8859-1 로 디코딩한다. 그러면 한글이 전부
    #   깨지고, **한글을 대조하는 검사만 골라서** 거짓 실패를 낸다.
    #   2026-08-15 실측: 자극이 멀쩡히 실려 왔는데 `conceptBoard.rendered` 검사만 FAIL 이
    #   났다(원장으로 대조해 제품은 정상임을 확인). 스모크가 거짓 경보를 내면 다음 사람이
    #   스모크를 믿지 않게 되고, 그때부터 진짜 결함이 지나간다.
    $response = Invoke-WebRequest @arguments
    $bytes = $response.RawContentStream.ToArray()
    if ($bytes.Length -eq 0) { return $null }
    return [System.Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
}

# 유료 구간과 프로브가 함께 쓰는 자극. 확정 사업안이 없는 일회용 프로젝트에서도
# 인터뷰를 태울 수 있는 이유는 컨셉보드를 **요청 본문으로** 받기 때문이다.
$board = @{
    conceptName = "귀가 알림 밴드"
    targetUsers = "초등 저학년 자녀를 둔 맞벌이 부모"
    problemScenario = "아이가 학교에서 집까지 오는 30분 동안 연락이 닿지 않는다"
    featureSet = @("출발·도착 자동 알림", "경로 이탈 시 알림")
    differentiators = "아이에게 휴대전화를 사 주지 않아도 동작한다"
    priceKrw = 39000
}

# ── 0. 등록 세 곳이 맞물려 있나 (LLM 0회) ─────────────────────────────
Write-Step "registration"
$registrationProbe = @'
from app.api.executions import TASK_TYPES
from app.interview.models import MarketInterviewInput
from app.interview.questions import QUESTIONS
print("registered=" + str("MARKET_INTERVIEW" in TASK_TYPES))
print("questions=" + str(len(QUESTIONS)))
print("sizes=" + ",".join(
    str(s) for s in MarketInterviewInput.model_fields["sampleSize"].annotation.__args__))
'@
$registrationOutput = ($registrationProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output ($registrationOutput -split "`n" | ForEach-Object { "  $_" })
if ($registrationOutput -match "registered=True") { Write-Pass "TASK_TYPES 등록" }
else { Add-Failure "MARKET_INTERVIEW 가 TASK_TYPES 에 없다 — 등록 분기를 빠뜨렸다" }
if ($registrationOutput -match "questions=9") { Write-Pass "고정 9문항" }
else { Add-Failure "문항 수가 9가 아니다 — 가이드가 바뀌었으면 골든과 계약도 같이 봐야 한다" }
if ($registrationOutput -match "sizes=20,40,80") { Write-Pass "표본 20/40/80" }
else { Add-Failure "표본 값이 서버·DB CHECK 와 갈렸다" }

# ── 1. 뱅크가 컨테이너에 붙어 있나 ────────────────────────────────────
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

# ── 2. 뱅크가 없으면 시끄럽게 죽나 ────────────────────────────────────
#     조용히 빈 표본으로 도는 것이 이 기능에서 가장 위험한 실패다.
Write-Step "bank unavailable"
$unavailableProbe = @'
import asyncio, os
os.environ.pop("TWIN_BANK_DIR", None)
from app.interview import execute_market_interview
from app.providers import ProviderFailure
payload = {"sampleSize": 20, "conceptBoard": {
    "conceptName": "귀가 알림 밴드", "targetUsers": "맞벌이 부모",
    "problemScenario": "연락이 닿지 않는다", "featureSet": ["도착 알림"],
    "differentiators": "전화기가 필요 없다", "priceKrw": 39000}}
try:
    asyncio.run(execute_market_interview(payload, 60))
    print("NO_FAILURE")
except ProviderFailure as failure:
    print("reason=" + failure.reason)
'@
$unavailableOutput = ($unavailableProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output "  $unavailableOutput"
if ($unavailableOutput -match "TWIN_BANK_UNAVAILABLE") { Write-Pass "뱅크 미마운트 = 시끄러운 실패" }
else { Add-Failure "뱅크가 없는데 TWIN_BANK_UNAVAILABLE 이 아니다" }

# ── 3. 입력이 틀리면 LLM 앞에서 거절되나 (LLM 0회) ────────────────────
Write-Step "input contract"
$contractProbe = @'
import asyncio
from app.interview import execute_market_interview
from app.providers import ProviderFailure

BOARD = {"conceptName": "귀가 알림 밴드", "targetUsers": "맞벌이 부모",
         "problemScenario": "연락이 닿지 않는다", "featureSet": ["도착 알림"],
         "differentiators": "전화기가 필요 없다", "priceKrw": 39000}

def probe(name, payload):
    try:
        asyncio.run(execute_market_interview(payload, 60))
        print(name + "=NO_FAILURE")
    except ProviderFailure as failure:
        print(name + "=" + failure.reason)

# 우열 조사의 표본 값은 이 조사가 받지 않는다.
probe("legacySize", {"sampleSize": 300, "conceptBoard": BOARD})
# 가격은 원 단위 정수다. 실수는 canonical hash 앞에서 터지므로 여기서 막는다.
probe("floatPrice", {"sampleSize": 20, "conceptBoard": dict(BOARD, priceKrw=39000.5)})
# 옛 계약의 칸을 보내면 거절한다 — 조용히 무시하면 자극이 바뀐 줄 모른다.
probe("legacyField", {"sampleSize": 20, "conceptBoard": BOARD, "situation": "옛 칸"})
'@
$contractOutput = ($contractProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output ($contractOutput -split "`n" | ForEach-Object { "  $_" })
foreach ($case in @("legacySize", "floatPrice", "legacyField")) {
    if ($contractOutput -match "$case=FIELD_CONSTRAINT_VIOLATION") { Write-Pass "$case 거절 (LLM 0회)" }
    else { Add-Failure "$case 가 입력 계약을 통과했다" }
}

# ── 4. 경계 문구가 코드에서 나오나 (LLM 0회) ──────────────────────────
Write-Step "caveats"
$caveatProbe = @'
from app.interview import caveats
plain = caveats.build("이름: 귀가 알림 밴드")
green = caveats.build("이름: 친환경 세제")
print("count=%d" % len(plain))
print("notValidated=" + str(any("외적 타당성 시험을 거치지 않았다" in n for n in plain)))
print("noPercent=" + str(any("백분율로 환산하지 마라" in n for n in plain)))
print("noPricing=" + str(any("지불의사" in n for n in plain)))
print("ethicalFlagged=" + str(len(green) > len(plain)))
'@
$caveatOutput = ($caveatProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output ($caveatOutput -split "`n" | ForEach-Object { "  $_" })
foreach ($check in @("notValidated", "noPercent", "noPricing", "ethicalFlagged")) {
    if ($caveatOutput -match "$check=True") { Write-Pass $check }
    else { Add-Failure "경계 문구에서 $check 가 빠졌다" }
}

# ── 5. 계정·프로젝트 ──────────────────────────────────────────────────
Write-Step "account"
$suffix = [guid]::NewGuid().ToString("N")
$username = "mint" + $suffix.Substring(0, 12)
$password = "Q7!" + $suffix.Substring(0, 20)
Invoke-Json POST "$BaseUrl/api/v1/auth/signup" @{} @{
    username = $username
    password = $password
    displayName = "Interview Smoke"
    email = "mint-$suffix@example.com"
    organizationName = $null; departmentName = $null; jobTitle = $null
} | Out-Null
# 가입은 토큰을 주지 않는다 — 로그인이 별도다.
$login = Invoke-Json POST "$BaseUrl/api/v1/auth/login" @{} @{ username = $username; password = $password }
$headers = @{ "Authorization" = "Bearer $($login.data.tokens.accessToken)" }
$project = Invoke-Json POST "$BaseUrl/api/v1/projects" $headers @{
    title = "Interview smoke " + $suffix.Substring(0, 8)
    description = "disposable market interview smoke"
    industryCategory = "test"
}
$projectId = $project.data.id
Write-Pass "project $projectId"

function Wait-Terminal {
    param([int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $current = Invoke-Json GET "$BaseUrl/api/v2/projects/$projectId/market-interview/current" $headers $null
        $state = $current.data.run.state
        if ($state -eq "SUCCEEDED" -or $state -eq "FAILED") { return $current.data }
        Start-Sleep -Seconds 3
    }
    return $null
}

# ── 6. 컨셉보드 엔드포인트가 실제로 서 있나 ──────────────────────────
#     여기서 도는 프로젝트에는 확정된 사업안이 없다. 그러면 404 여야 한다 —
#     **견본으로 떨어지면 안 된다.** 조용한 기본값이 실제로 사고를 냈다(미용실 견본).
#     이 검사가 보는 것은 거절 자체가 아니라 그 앞의 배선이다: 라우팅·인증·컨트롤러.
Write-Step "board endpoint"
try {
    Invoke-Json GET "$BaseUrl/api/v2/projects/$projectId/market-interview/board" $headers $null | Out-Null
    Add-Failure "확정된 사업안이 없는데 컨셉보드가 나왔다 — 견본으로 떨어졌다"
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -eq 404) { Write-Pass "사업안 미확정 = 404 (LLM 0회)" }
    elseif ($status -eq 405 -or $status -eq 401) { Add-Failure "board 엔드포인트가 서 있지 않다: HTTP $status" }
    else { Add-Failure "예상 밖 응답: HTTP $status" }
}

# ── 7. 백엔드 입력 검증이 LLM 앞에 서 있나 ───────────────────────────
Write-Step "start endpoint validation"
try {
    Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/market-interview" $headers @{
        conceptBoard = $board; sampleSize = 300
    } | Out-Null
    Add-Failure "표본 300 이 백엔드를 통과했다 — DB CHECK 에서 500 이 된다"
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -eq 400) { Write-Pass "표본 300 거절 = 400" }
    else { Add-Failure "표본 검증이 400 이 아니다: HTTP $status" }
}
try {
    $wrong = @{ conceptName = "밴드"; extra = "옛 칸" }
    Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/market-interview" $headers @{
        conceptBoard = $wrong; sampleSize = 20
    } | Out-Null
    Add-Failure "여섯 칸이 아닌 컨셉보드가 통과했다"
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -eq 400) { Write-Pass "컨셉보드 칸 검증 = 400" }
    else { Add-Failure "컨셉보드 검증이 400 이 아니다: HTTP $status" }
}

# ── 8. 실제 인터뷰 (유료) ─────────────────────────────────────────────
if (-not $Paid) {
    Write-Output "`n(유료 구간 건너뜀 — 실제 인터뷰를 태우려면 -Paid)"
} else {
    Write-Step "paid interview n=$SampleSize"
    $started = Get-Date
    $run = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/market-interview" $headers @{
        conceptBoard = $board
        sampleSize = $SampleSize
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
        $answered = [int]$result.telemetry.answered

        # 응답 크기 — 원장을 실으면 여기서 터진다.
        $bytes = ([System.Text.Encoding]::UTF8.GetBytes(($result | ConvertTo-Json -Depth 20 -Compress))).Length
        if ($bytes -ge 2MB) { Add-Failure "응답이 2 MiB 이상이다: $bytes" }
        else { Write-Pass "응답 $([math]::Round($bytes / 1KB, 1)) KiB" }

        # 경계는 비면 안 된다. 계약이 이미 막지만 여기서 눈으로 본다.
        if (-not $result.caveats -or $result.caveats.Count -eq 0) {
            Add-Failure "caveats 가 비었다"
        } else { Write-Pass "caveats $($result.caveats.Count)줄" }
        if ($version.caveatCount -le 0) { Add-Failure "물질화된 caveatCount 가 0이다" }

        # 응답자가 본 자극이 결과에 실려 있나 — 없으면 답을 해석할 수 없다.
        if ($result.conceptBoard.rendered -like "*$($board.conceptName)*") {
            Write-Pass "자극(rendered) 이 결과에 실려 있다"
        } else { Add-Failure "conceptBoard.rendered 가 비었거나 자극과 다르다" }

        # 이해도 — 네 칸의 합이 답한 사람 수여야 한다.
        $c = $result.comprehension
        $sum = [int]$c.accurate + [int]$c.partial + [int]$c.misunderstood + [int]$c.unclassified
        if ($sum -ne $answered) { Add-Failure "이해도 합이 답한 사람 수와 다르다: $sum vs $answered" }
        else { Write-Pass "이해도 정확 $($c.accurate) · 부분 $($c.partial) · 오해 $($c.misunderstood) · 미판정 $($c.unclassified)" }

        # 언급 수는 사람 수다. 표본을 넘으면 LLM 이 센 것이다.
        $themes = @($result.themes)
        if ($themes.Count -eq 0) { Add-Failure "주제가 하나도 안 나왔다" }
        foreach ($theme in $themes) {
            if ([int]$theme.mentionCount -lt 1 -or [int]$theme.mentionCount -gt $answered) {
                Add-Failure "$($theme.label): 언급 수가 범위를 벗어났다 ($($theme.mentionCount)/$answered)"
            }
        }
        $axes = ($themes | ForEach-Object { $_.axis } | Sort-Object -Unique) -join ","
        Write-Pass "주제 $($themes.Count)개 · 축 [$axes]"

        # 인용문은 지어낸 것이 아니어야 한다 — 전원 응답에 같은 문장이 있어야 한다.
        $spoken = (@($result.transcripts) | ForEach-Object {
            @($_.firstImpression, $_.restatement, $_.like, $_.concern, $_.differentiation,
              $_.relevance, $_.usageScene, $_.barrier, $_.suggestion)
        }) -join "`n"
        $matched = 0
        foreach ($theme in $themes) {
            if ($theme.quote -and $spoken.Contains($theme.quote)) { $matched++ }
        }
        if ($matched -eq 0) { Add-Failure "응답 원문에 없는 인용문뿐이다 — 인용이 지어졌을 수 있다" }
        else { Write-Pass "인용문 대조 $matched 건 일치" }

        # ── 40/40 재발 검사 ───────────────────────────────────────────────
        # 2026-08-12 에 모든 주제가 40/40 으로 나왔고 계약도 화면도 그것을 통과시켰다.
        # 아래 넷은 그 고장이 되살아나면 여기서 멈추라고 있는 것이다.
        $saturated = @($result.telemetry.homogeneity.saturatedThemes)
        if ($saturated.Count -gt 0) {
            Add-Failure "포화: 전원이 든 축 또는 이름표가 하나뿐인 축이 있다 — $($saturated -join ' / ')"
        } else { Write-Pass "포화 없음 — 축마다 답이 갈렸다" }

        $altSum = 0
        foreach ($alt in @($result.alternatives)) { $altSum += [int]$alt.mentionCount }
        if ($altSum -gt $answered) {
            Add-Failure "대안 언급 합계가 사람 수를 넘었다 ($altSum/$answered) — 1인 1대안이 깨졌다"
        } else { Write-Pass "대안 합계 $altSum / $answered" }

        foreach ($axis in @("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION")) {
            $labels = @($themes | Where-Object { $_.axis -eq $axis })
            if ($labels.Count -lt 2) {
                Add-Failure "$axis 축의 이름표가 $($labels.Count)개뿐이다 — 코더가 결을 못 찾았다"
            }
        }

        $d = $result.differentiation
        $dTop = [Math]::Max([Math]::Max([int]$d.different, [int]$d.similar), [int]$d.unclear)
        if ($dTop -ge $answered) { Add-Failure "차별성 판정이 한 칸에 몰렸다 ($dTop/$answered)" }
        else { Write-Pass "차별성 다름 $($d.different) · 비슷 $($d.similar) · 모름 $($d.unclear)" }

        $resolvedMax = 0
        foreach ($theme in $themes) {
            if ([int]$theme.resolvedCount -gt $resolvedMax) { $resolvedMax = [int]$theme.resolvedCount }
        }
        if ($resolvedMax -ge $answered) {
            Add-Failure "「해소되면 사겠다」가 전원이다 ($resolvedMax/$answered) — 모델이 추측했다"
        } else { Write-Pass "장벽 해소 발언 최대 $resolvedMax / $answered" }

        # 대표 카드 — 프로필 파서가 조용히 죽으면 여기서 잡힌다.
        $cards = @($result.interviews)
        if ($cards.Count -eq 0) { Add-Failure "대표 응답자 카드가 비었다" }
        elseif ($cards.Count -gt 5) { Add-Failure "대표 카드가 5장을 넘는다 ($($cards.Count))" }
        else {
            $filled = 0
            foreach ($item in $cards) {
                $p = $item.profile
                if ($p.age -and $p.gender -and $p.household -and $p.region -and $p.income -and $p.job) {
                    $filled++
                }
            }
            $levels = ($cards | ForEach-Object { $_.comprehension } | Sort-Object -Unique) -join ","
            Write-Pass "대표 카드 $($cards.Count)장 · 프로필 6칸 완전 $filled 장 · 이해도 [$levels]"
            if ($filled -eq 0) { Add-Failure "프로필이 한 장도 채워지지 않았다 — 파서가 죽었다" }
        }

        Write-Output "  sampling: requested=$($result.sampling.requested) drawn=$($result.sampling.drawn) answered=$answered"
        Write-Output "  targeting: target=$($result.targeting.targetDrawn) nonTarget=$($result.targeting.nonTargetDrawn) shortfall=$($result.targeting.shortfall)"
        Write-Output "  criteria : $($result.targeting.criteriaText)"
        Write-Output "  telemetry: cells=$($result.telemetry.cells) formatViolations=$($result.telemetry.formatViolations) failures=$($result.telemetry.failures) llmCalls=$($result.telemetry.llmCalls)"
        Write-Output ""
        Write-Output "  ※ 조건식이 사업안의 「누구를 위한 것인가」와 맞는지는 사람만 판정할 수 있다."
        Write-Output "    원장을 켜 두었다면 재코딩은 공짜다: python -m app.tools.recode_ledger <원장>"
    }
}

# ── 결과 ──────────────────────────────────────────────────────────────
Write-Output ""
if ($failures.Count -gt 0) {
    Write-Output "MARKET INTERVIEW SMOKE FAILED ($($failures.Count))"
    $failures | ForEach-Object { Write-Output "- $_" }
    exit 1
}
Write-Output "MARKET INTERVIEW SMOKE PASSED"
