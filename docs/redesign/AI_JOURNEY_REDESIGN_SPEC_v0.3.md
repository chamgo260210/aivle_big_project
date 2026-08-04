**AI 사업검증 플랫폼  
Idea·법률·Concept 재설계  
및 신속 구축 계획서**

Idea Clarification · 법제처 기반 Legal Precheck · 검증 통과 Concept 3개
생성

| **문서 버전** | v0.3                                             |
|---------------|--------------------------------------------------|
| **작성 기준** | 2026-08-03                                       |
| **문서 성격** | 현재 구현 범위 확정본 + 후속 분석 입력 연결 참고 |
| **적용 대상** | bp_new_2의 Idea·Legal·Concept 구간 재구축        |

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>이 문서의 사용법</strong></p>
<p>서비스의 세부 단계가 다시 바뀌더라도 먼저 이 문서의 핵심 불변식과
입력 출처 규칙을 유지한다. 세부 API·DB·화면 계약은 각 구현 묶음에서
필요한 만큼만 구체화한다. 처음부터 거대한 범용 Workflow Engine이나
완전한 최종 문서를 만들지 않는다.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

근거 자료: 지금까지의 재설계 대화, BM 입력 분류 문서, junwoo 브랜치 법률
파이프라인 분석, 현재 구축 범위에 대한 후속 결정.

# **문서 구성**

1.  핵심 결정과 범위

2.  목표 Journey와 두 번의 법률 검증

3.  입력을 관리하는 두 축: 출처 유형과 필요 시점

4.  단계별 사용자 입력 연결

5.  핵심 Domain Contract

6.  junwoo 법률 구현의 기술적 이식 범위

7.  Concept 생성·검증·폐기·대체 생성

8.  후속 시장·BM·기술운영·재무 입력 연결(참고)

9.  Version·잠금·Stale 전파

10. UX 재설계

11. 현재 범위의 신속하지만 제대로 된 구현 계획

12. Codex 작업 지시 운영 규칙과 템플릿

13. 부록: 확정/보류 정책·검증 시나리오·자료 출처

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>한 문장 요약</strong></p>
<p>사용자가 확인한 Idea Origin을 기준으로 1차 법률 제약을 만들고, 그
제약 안에서 Concept 초안을 생성한 뒤 Origin과 법률을 모두 통과한
Concept만 시장·BM·기술운영·재무 분석에 넘긴다.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# **1. 핵심 결정과 범위**

## **1.1 이번 재설계가 해결할 문제**

- AI 해석 결과가 부족하다고 알려주면서도 사용자가 긴 원문을 다시
  편집해야 하는 불편

- 사용자가 확정한 내용, AI가 추정한 내용, 외부 근거로 확인한 내용이
  뒤섞이는 문제

- Concept Builder가 사용자의 사업 본질이나 확정 가격·채널·수익모델을
  임의로 바꾸는 위험

- Idea 단계의 법률 검토가 단순 코멘트로 끝나 Concept 생성에 강제
  제약으로 작동하지 않는 문제

- 시장·BM·기술운영·재무 단계가 각각 필요한 사용자 입력을 언제 받아야
  하는지 연결되지 않은 문제

- AI Prompt·Python Schema·Java Validator·저장 Mapping 불일치가 실행
  시점의 502로 드러나는 문제

## **1.2 확정된 불변식**

| **불변식**              | **적용 규칙**                                                                                                                                     |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **Idea Origin 중심**    | Concept은 사용자의 자유 원문이 아니라 확정된 Idea Origin Snapshot에서 파생한다.                                                                   |
| **법률 검증은 2회**     | Idea Legal Precheck와 Concept Legal Validation만 둔다. 이번 범위는 두 검증을 통과한 Concept 3개 표시에서 끝난다.                                  |
| **실패 Concept 비노출** | Origin 또는 법률 검증에 실패한 Concept은 조건부 후보로 통과시키지 않고 폐기·대체 생성한다.                                                        |
| **Concept 동결**        | 두 검증을 통과한 Concept은 ELIGIBLE로 동결한다. 이번 범위에서는 후속 분석과 연결하지 않고, 이후 재설계에서도 Concept 정의를 추가·변경하지 않는다. |
| **사용자 값 우선**      | 사용자가 확정한 가격·수익모델·채널·역할·제약은 하위 단계가 덮어쓰지 못한다.                                                                       |
| **추정값 공개**         | 검색·Benchmark·AI 제안은 ESTIMATED/AI_PROPOSED로 표시하고 사용자 확인 전 확정값으로 쓰지 않는다.                                                  |
| **Spring 소유**         | Spring이 업무 RDB·Object Storage·Version·TaskRun·결과 검증을 소유하고 AI Server는 내부 실행만 담당한다.                                           |
| **변경은 추가형**       | 기존 Migration은 수정하지 않고 새 Migration을 추가한다. 실험 중 데이터 삭제를 기본 전략으로 쓰지 않는다.                                          |

## 1.3 현재 범위에서 확정한 정책

- 사용자에게 표시할 적격 Concept은 기본 3개로 한다.

- 최초 Concept 초안도 3개를 생성하고, Origin 또는 법률 검증에 실패한
  자리만 내부에서 대체 생성한다.

- 대체 생성은 기본 최대 2개 라운드, 전체 검사 후보는 최대 9개로
  제한한다. 숫자는 설정값으로 관리하되 현재 기본 정책으로 사용한다.

- 최대 시도 후에도 적격 Concept 3개를 확보하지 못하면 통과 기준을 낮추지
  않고 Idea Origin 또는 법률 입력 보완을 요청한다.

- Concept 검증은 점수제가 아니라 Origin Integrity PASS/FAIL과 Legal
  Validation PASS/FAIL로 운영한다.

- 이번 구축 범위는 적격 Concept 3개를 생성하고 사용자에게 표시하는
  단계까지다. Concept 선택 및 후속 분석 연결은 포함하지 않는다.

- 일반 웹 검색은 Idea 필수 입력 자동완성에 사용하지 않는다.
  카테고리·대안·경쟁사 후보를 찾는 선택형 보조 기능으로만 고려한다.

- 법률 근거는 일반 웹 검색이 아니라 Versioned Registry와 법제처 API를
  기준으로 확인한다.

- 화면은 기존 디자인 시스템과 레퍼런스를 보존하고 실제 상태와 행동이
  명확한 서비스 문구를 구현한다.

- CI는 현재 실험 기간에 의도적으로 보류한다. Journey와 Contract가 고정된
  뒤 최종 마감 전에 복구한다.

- Legacy는 새 Idea·Legal·Concept 흐름으로 완전히 대체된 코드만 기능 확인
  후 제거하고, 기존 후속 MVP 단계는 보존하되 이번 흐름과 연결하지
  않는다.

- 로컬 실행은 저장소의 env example을 복사해 .env를 만들고 AI·법제처·내부
  Token·DB/스토리지 값을 사용자가 입력하는 방식으로 안내한다.

## 1.4 후속 단계로 미루는 항목

- 시장·BM·기술운영·재무의 상세 Contract, 점수표와 판정 Threshold

- Tavily 등 일반 웹 검색 Provider의 본격 채택, 비용 정책과 근거 품질
  평가

- Concept 선택 이후 Persona·Marketing·Report를 포함한 후속 Journey
  재설계

- 후속 MVP 영역의 Legacy 제거와 전체 DB Cutover

- 최종 GitHub Actions CI, 운영 배포, Secret Manager, 모니터링·알림·확장
  설정

| **현재 범위 \|** Idea 입력·보완 → Idea Origin → 1차 법률 Precheck → Guardrail → Concept 생성 → Origin/법률 검증 → 적격 Concept 3개 표시 |
|-----------------------------------------------------------------------------------------------------------------------------------------|

# **2. 목표 Journey와 두 번의 법률 검증**

| **Idea 입력** | **→** | **보완 질문** | **→** | **Idea Origin** | **→** | **법률 Precheck** | **→** | **Guardrail** | **→** | **Concept 초안** |
|---------------|-------|---------------|-------|-----------------|-------|-------------------|-------|---------------|-------|------------------|

| **Concept 초안** | →   | **Origin 검사** | →   | **법률 검사** | →   | **ELIGIBLE** | →   | **적격 3개 표시** | →   | **현재 범위 종료** |
|------------------|-----|-----------------|-----|---------------|-----|--------------|-----|-------------------|-----|--------------------|

이번 구축에서는 적격 Concept 3개를 표시한 뒤 종료한다.
시장·BM·기술운영·재무와 Concept 선택 이후 Journey는 기존 MVP를 그대로
연결하지 않고 별도 재설계 후 단계적으로 다룬다.

## **2.1 단계 상태와 통과 규칙**

| **단계**                     | **통과 조건**                                                 | **실패 시 이동**                                           |
|------------------------------|---------------------------------------------------------------|------------------------------------------------------------|
| **Idea Clarification**       | Idea Origin 필수값이 사용자 확인 상태                         | 부족 질문에 답변                                           |
| **Idea Legal Precheck**      | 진행 허용 상태 + Guardrail 생성 가능                          | 질문 답변, Origin 수정, 또는 진행 중단                     |
| **Origin Integrity**         | 고객·문제·가치·고정요소·확정값 모두 보존                      | Concept 초안 폐기 후 대체 생성                             |
| **Concept Legal Validation** | Guardrail 위반 없음, 새 규제 행위가 허용 범위                 | Concept 초안 폐기 후 대체 생성                             |
| **ELIGIBLE 3개 표시**        | Origin·법률 검증을 모두 통과한 서로 구분되는 Concept 3개 확보 | 부족하면 내부 대체 생성, 최대 시도 후에는 사용자 보완 요청 |
| **후속 분석**                | 이번 구축에서 실행·연결하지 않음                              | 기존 MVP 코드는 보존하고 별도 재설계                       |

## **2.2 법률 Gate가 두 번인 이유**

- **Idea Legal Precheck:** 사업의 본질적인 금지·인허가·책임·필수 사용자
  정보와 Concept Builder용 제약을 만든다.

- **Concept Legal Validation:** 구체화 과정에서 추가된 결제, 채널, 기능,
  데이터 처리, 운영 역할이 1차 Guardrail을 위반하지 않는지 검사한다.

- ELIGIBLE 이후 미실행: ELIGIBLE 이후 시장·BM·기술운영·재무는 Concept을
  변경하지 않으므로 새로운 법률 검증 대상이 생기지 않아야 한다.

# **3. 입력을 관리하는 두 축**

## **3.1 축 A — 값의 생성 출처**

| **출처 유형**     | **정의**                                                               | **대표 예시**                                          |
|-------------------|------------------------------------------------------------------------|--------------------------------------------------------|
| USER_CONFIRMED    | 시스템이 대신 결정할 수 없으며 사용자가 확인하거나 결정한 값           | 핵심 문제, 판매 주체, 확정 가격, 보유 인력             |
| CONCEPT_GENERATED | 사용자가 비워둔 허용 범위 안에서 Concept Builder가 Concept별로 만든 값 | 세부 세그먼트, 포지셔닝, 기능 조합, 가격안             |
| UPSTREAM_OUTPUT   | 앞 단계의 검증·분석 결과가 다음 단계 입력으로 전달되는 값              | 법률 Guardrail, 시장 가격 근거, BM Cost Driver         |
| STAGE_LOCAL       | 현재 단계가 검색·추정·계산·비교를 위해 생성한 값                       | 검색 Query, 비용 Benchmark, Scenario, Validation Score |

## **3.2 축 B — 언제까지 필요한가**

| **필요 시점**               | **의미**                                                        |
|-----------------------------|-----------------------------------------------------------------|
| REQUIRED_FOR_IDEA_ORIGIN    | Idea Origin 확정 전에 사용자 확인이 반드시 필요                 |
| REQUIRED_FOR_LEGAL_PRECHECK | 관련 규제가 있을 때 1차 법률 검토 전에 동적으로 질문            |
| REQUIRED_FOR_CONCEPT_BUILD  | Concept 생성 전 확정 또는 제안 허용 여부를 결정                 |
| REQUIRED_FOR_MARKET         | 시장 범위·지역·시점·조사 질문을 고정                            |
| REQUIRED_FOR_BM             | 가격·수익·채널의 공식 분석 기준을 고정                          |
| REQUIRED_FOR_TECH_OPS       | 보유 자원·내재화·규모·필수 연동을 고정                          |
| REQUIRED_FOR_FINANCE        | 비용·목표·원가·CAC 등 계산 가정을 확정                          |
| REQUIRED_FOR_MARKETING      | 톤·사용/금지 키워드·표현 제약을 확정                            |
| OPTIONAL                    | 없어도 진행 가능하며 해당 단계가 제안·Benchmark 또는 빈 값 처리 |

## **3.3 값 상태·잠금·대체 정책**

| **구분**  | **값**                                                                                              |
|-----------|-----------------------------------------------------------------------------------------------------|
| 상태      | NOT_ASKED · MISSING · AI_PROPOSED · ESTIMATED · USER_CONFIRMED · VERIFIED · LOCKED                  |
| 대체 정책 | NO_FALLBACK · AI_MAY_PROPOSE · STAGE_MAY_BENCHMARK · USE_DEFAULT_WITH_DISCLOSURE · BLOCK_STAGE      |
| 우선순위  | 사용자 확정값 \> Legal hard constraint \> 동결 Concept \> 검증된 이전 단계 결과 \> 현재 단계 추정값 |

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"key": "pricing.userIntent",<br />
"value": 38000,<br />
"sourceType": "USER_CONFIRMED",<br />
"createdAtStage": "IDEA",<br />
"requiredForStages": ["BM", "FINANCE"],<br />
"status": "LOCKED",<br />
"fallbackPolicy": "AI_MAY_PROPOSE_IF_EMPTY",<br />
"sourceRef": "idea-origin-v3"<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# **4. 단계별 사용자 입력 연결**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>가장 중요한 정정</strong></p>
<p>USER_CONFIRMED는 “아이디어 화면에서 전부 받는 값”이 아니다. 시스템이
대신 확정할 수 없는 값의 출처 유형이며, 실제 입력 시점은
Idea·Legal·Market·BM·기술운영·재무·Marketing으로 분산한다.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

| **입력 수집의 핵심 정정 \|** USER_CONFIRMED는 “아이디어 첫 화면에서 전부 입력”을 뜻하지 않는다. 이번 범위에서 Idea가 받는 것은 Origin 필수값, 법률 검토에 필요한 조건부 값, 사용자가 이미 정한 선택값뿐이다. 비용·인력·CAC 같은 값은 후속 분석 단계에서 받는다. |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|

## **4.1 Idea Origin 확정에 꼭 필요한 입력**

| **필드**                  | **사용자에게 묻는 내용**               | **후속 사용**                    |
|---------------------------|----------------------------------------|----------------------------------|
| productServiceDescription | 무엇을 제공하는 사업인가               | 전 단계 공통 맥락                |
| problem                   | 누구의 어떤 불편·문제를 해결하는가     | 시장 수요·Concept·Persona        |
| target                    | 고객 유형, 세그먼트, 상황, 니즈        | 시장 범위·Concept 세분화·Persona |
| solution                  | 문제를 어떤 방식으로 해결하는가        | 경쟁 탐색·기능·기술운영          |
| coreValue                 | 어떤 Concept에서도 유지할 고객 가치    | Origin 검증·포지셔닝·Marketing   |
| primaryCategory           | 주 제품·서비스/시장 분야               | 법률 Route·직접시장·경쟁 검색    |
| targetRegion              | 초기 사업 대상 지역·국가               | 법률 적용 범위·시장 통계 범위    |
| fixedValues               | Concept이 달라져도 바뀌면 안 되는 요소 | Concept 생성·Origin Integrity    |

## **4.2 Idea에서 선택적으로 받되 입력되면 잠그는 값**

| **통합 필드**          | **없을 때 처리**                       | **실제 사용**        |
|------------------------|----------------------------------------|----------------------|
| alternatives           | 시장 분석이 대체재 조사                | 시장·Concept         |
| knownCompetitors       | 시장 분석이 별도 근거로 탐색           | 시장                 |
| differentiationIntent  | 시장/Concept이 후보 제안               | Concept·BM·Marketing |
| pricingIntent          | Concept별 가격안 또는 시장 근거로 제안 | Concept·BM·재무      |
| knownUnitCost          | 기술운영/BM이 Benchmark 제시           | BM·재무              |
| revenueModelIntent     | Concept별 수익모델 제안 허용           | Concept·BM·재무      |
| salesChannelIntent     | Concept별 채널안 제안 허용             | Concept·시장·BM      |
| marketEntryTiming      | 시장 분석 진입 전에 질문               | 시장·재무            |
| marketResearchQuestion | 기본 조사 범위 사용                    | 시장                 |
| internalConstraints    | 빈 값 허용                             | Concept·기술운영     |
| brandGuardrails        | Marketing 진입 전에 질문 가능          | Marketing            |

BM 입력 문서의 known_unit_cost, expected_price_text,
revenue_model_decision, sales_channel_decision은 이 선택 확정값에
해당한다. 사용자가 값이 있다고 명시한 경우 가장 높은 우선순위로
상속하고, 없을 때만 Concept Builder가 변주한다.

## **4.3 Legal Precheck 전에 동적으로 받는 입력**

| **상황**            | **동적 사용자 질문**                                        | **법률 판단 목적**          |
|---------------------|-------------------------------------------------------------|-----------------------------|
| 거래·중개           | 실제 판매자, 플랫폼 역할, 결제 수취자, 환불 책임, 정산 주체 | 전자상거래·중개·소비자 책임 |
| 개인정보·위치       | 수집 데이터, 위치 사용 방식, 지속 수집 여부, 보관 목적·기간 | 개인정보·위치정보 의무      |
| 실물·식품·규제 상품 | 제조/판매/중개 역할, 안전 책임, 효능 표현, 배송·픽업 방식   | 인허가·표시·안전·유통 책임  |
| 사업 범위           | 국내/해외, 미성년자, 사용자 생성 콘텐츠, 업로드 파일        | 관할·연령·저작권·보관 의무  |

## 4.4 시장 분석 사용자 입력 연결 — 후속 참고

| **입력**               | **필수성** | **설명**                                                         |
|------------------------|------------|------------------------------------------------------------------|
| targetRegion           | 필수       | Idea에서 확정되지 않았다면 시장 분석을 시작하기 전에 반드시 확인 |
| marketEntryTiming      | 권장       | 현재 시장만 볼지 출시 시점의 변화까지 반영할지 결정              |
| marketResearchQuestion | 선택       | 사용자가 가장 먼저 확인하고 싶은 조사 질문                       |
| relatedCategories      | 확인       | AI가 제안한 인접·대체시장 검색 범위를 사용자에게 확인            |

## 4.5 BM 사용자 입력 연결 — 후속 참고

- Concept에서 가격·수익모델·채널이 이미 동결됐다면 다시 묻지 않는다.

- 사용자 희망 가격과 시장 권장 범위가 크게 충돌하는 경우 공식 분석
  기준만 사용자에게 확인한다.

- 사용자 값이 없고 Concept이 가격·수익·채널 Variant를 생성했다면 해당
  Concept 정의를 분석 기준으로 사용한다.

## 4.6 기술·운영 사용자 입력 연결 — 후속 참고

| **입력**            | **Idea 필수 여부** | **사용 목적**              |
|---------------------|--------------------|----------------------------|
| 보유 인력·설비·기술 | 아님               | 추가 확보 자원과 초기 비용 |
| 자체 수행/외주 선호 | 아님               | 구현·운영 모델과 비용 구조 |
| 목표 처리 규모      | 아님               | Capacity·인프라·인력       |
| 필수 연동 시스템    | 선택               | 아키텍처·개발비·운영 위험  |
| 보안·가용성 요구    | 아님               | 인프라 수준과 비용         |
| 운영 불가 소재·기술 | Idea 선택값 가능   | 내부 제약과 대체 설계      |

## 4.7 재무 사용자 입력 연결 — 후속 참고

| **그룹**    | **입력**                                           | **초안 출처**                       |
|-------------|----------------------------------------------------|-------------------------------------|
| 고정 운영비 | 인건비, 임차/관리비, 고정 인프라비                 | 사용자·기술운영·Benchmark           |
| 초기 투자   | 개발/R&D, 설비/인프라, 특허/인허가                 | 사용자·기술운영·법률·Benchmark      |
| 사업 목표   | 3개년 판매량/활성 사용자/거래 수                   | 시장·BM·Capacity 초안 + 사용자 확인 |
| 획득 비용   | 고정 영업·마케팅비, 변동 CAC                       | BM·사용자 자료                      |
| 단위 경제성 | 판매가격, 제조/매입/물류원가 또는 유저/거래당 원가 | Concept·시장·BM·기술운영            |
| 재무 기준   | 할인율·Scenario 조정값                             | 기본값 공개 + 사용자 확인           |

## 4.8 입력 매핑 마스터 — 현재 범위와 후속 사용

| **필드/묶음**             | **출처 유형**  | **확정/생성 단계** | **사용 단계**          | **필수 시점** | **잠금**          | **Fallback**                |
|---------------------------|----------------|--------------------|------------------------|---------------|-------------------|-----------------------------|
| productServiceDescription | USER_CONFIRMED | Idea               | IDEA_ORIGIN            | 필수          | LOCKED            | 없으면 진행 차단            |
| problem                   | USER_CONFIRMED | Idea               | Idea·Market·Concept    | 필수          | LOCKED            | 없으면 진행 차단            |
| target                    | USER_CONFIRMED | Idea               | Market·Concept·Persona | 필수          | LOCKED            | 없으면 진행 차단            |
| solution                  | USER_CONFIRMED | Idea               | Concept·Market·TechOps | 필수          | LOCKED            | 없으면 진행 차단            |
| coreValue                 | USER_CONFIRMED | Idea               | Concept·Marketing      | 필수          | LOCKED            | 없으면 진행 차단            |
| primaryCategory           | USER_CONFIRMED | Idea               | Legal·Market           | 필수          | LOCKED            | AI 후보 후 사용자 확인 가능 |
| targetRegion              | USER_CONFIRMED | Idea/Market        | Legal·Market           | 시장 전 필수  | LOCKED            | 없으면 시장 차단            |
| fixedValues               | USER_CONFIRMED | Idea               | Concept Gate           | 필수          | LOCKED            | AI 변경 금지                |
| pricingIntent             | USER_CONFIRMED | Idea/BM            | Concept·BM·Finance     | 선택          | 값 있을 때 LOCKED | 없으면 Concept 제안         |
| revenueModelIntent        | USER_CONFIRMED | Idea/BM            | Concept·BM·Finance     | 선택          | 값 있을 때 LOCKED | 없으면 Concept 제안         |
| salesChannelIntent        | USER_CONFIRMED | Idea/BM            | Concept·Market·BM      | 선택          | 값 있을 때 LOCKED | 없으면 Concept 제안         |
| knownUnitCost             | USER_CONFIRMED | Idea/Finance       | BM·Finance             | 선택          | CONFIRMED         | 없으면 Benchmark            |
| marketEntryTiming         | USER_CONFIRMED | Idea/Market        | Market·Finance         | 시장 전 권장  | CONFIRMED         | 기본 현재 기준 공개         |

## 4.9 입력 매핑 마스터 — Concept·후속 분석 값

| **필드/묶음**                     | **출처 유형**     | **확정/생성 단계** | **사용 단계**       | **필수 시점** | **잠금**  | **Fallback**                 |
|-----------------------------------|-------------------|--------------------|---------------------|---------------|-----------|------------------------------|
| legal seller/payment/refund facts | USER_CONFIRMED    | Legal              | Legal·Concept       | 조건부 필수   | LOCKED    | 없으면 Legal 차단            |
| conceptPositioning                | CONCEPT_GENERATED | Concept            | Market·BM·Marketing | Concept 필수  | FROZEN    | 사용자 고정값 우선           |
| conceptFeatureSet                 | CONCEPT_GENERATED | Concept            | TechOps·Market      | Concept 필수  | FROZEN    | Origin/Guardrail 제한        |
| conceptPricingVariant             | CONCEPT_GENERATED | Concept            | Market·BM·Finance   | 조건부        | FROZEN    | 사용자 가격 있으면 생성 금지 |
| conceptRevenueModelVariant        | CONCEPT_GENERATED | Concept            | BM·Finance          | 조건부        | FROZEN    | 사용자 결정 있으면 생성 금지 |
| conceptChannelVariant             | CONCEPT_GENERATED | Concept            | Market·BM           | 조건부        | FROZEN    | 사용자 결정 있으면 생성 금지 |
| legalGuardrails                   | UPSTREAM_OUTPUT   | Legal Precheck     | Concept             | 필수          | VERSIONED | 위반 Concept 폐기            |
| marketEvidence/metrics            | UPSTREAM_OUTPUT   | Market             | BM·Finance          | 필수          | VERSIONED | 근거·신뢰도 포함             |
| bmCostDrivers                     | UPSTREAM_OUTPUT   | BM                 | TechOps·Finance     | 필수          | VERSIONED | Concept 변경 금지            |
| techOpsCostModel                  | UPSTREAM_OUTPUT   | TechOps            | Finance             | 필수          | VERSIONED | Capacity/원가 근거           |
| costBenchmark                     | STAGE_LOCAL       | BM/TechOps/Finance | 해당 단계           | 선택          | ESTIMATED | 사용자 승인 전 비확정        |
| financialAssumptions              | USER_CONFIRMED    | Finance            | Finance             | 필수          | LOCKED    | 초안·근거 확인 후 확정       |
| brandGuardrails                   | USER_CONFIRMED    | Idea/Marketing     | Marketing           | 선택          | LOCKED    | 없으면 Marketing 단계 질문   |

## 4.10 일반 웹 검색의 역할

| **기본 원칙 \|** 사용자만 알 수 있는 사업 결정은 검색으로 채우지 않는다. 공개 정보만 후보로 제안하고, 사용자가 선택한 경우에만 USER_CONFIRMED로 승격한다. |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|

- 검색으로 채우지 않는 값: problem, target, solution, coreValue,
  fixedValues, 판매·결제·환불 책임, 내부 원가, 보유 자원, 확정
  가격·채널·수익모델.

- 선택형 검색 보조가 가능한 값: 주/연관 카테고리 후보, 기존 대안, 경쟁사
  후보, 업계 용어와 공개된 수익·채널 사례.

- 검색 결과는 WEB_DISCOVERED 또는 AI_PROPOSED로 별도 저장하고 사용자
  확인 전 Idea Origin에 포함하지 않는다.

- 일반 검색 Provider 장애가 Idea → Legal → Concept 진행을 막지 않도록
  기본 흐름의 필수 의존성으로 두지 않는다.

- 이번 구축 범위에서는 일반 웹 검색 연동을 제외한다. 법률 Source는
  Registry와 법제처 API로 확인한다.

| **상태**           | **의미**                                |
|--------------------|-----------------------------------------|
| **WEB_DISCOVERED** | 웹에서 발견했지만 사용자 확인 전인 후보 |
| **AI_PROPOSED**    | AI가 구조화·추천한 후보                 |
| **USER_CONFIRMED** | 사용자가 선택·수정·승인한 값            |
| **REJECTED**       | 사용자가 반영하지 않기로 한 후보        |

# **5. 핵심 Domain Contract**

## **5.1 Idea Origin Snapshot**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"ideaOriginVersionId": 3,<br />
"productServiceDescription": "...",<br />
"problem": ["..."],<br />
"target": {<br />
"customerTypes": ["B2C"],<br />
"segment": "...",<br />
"situation": "...",<br />
"needs": ["..."]<br />
},<br />
"solution": ["..."],<br />
"coreValue": ["..."],<br />
"primaryCategory": "...",<br />
"relatedCategories": ["..."],<br />
"targetRegion": "KR",<br />
"fixedValues": [{"field": "fulfillment", "value": "매장 직접
픽업"}],<br />
"confirmedValues": {},<br />
"assumptions": [],<br />
"sourceIdeaVersionId": 7<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

Idea Origin은 AI가 자동 확정하지 않는다. AI가 구조화한 뒤 사용자 확인을
거쳐 새 Version으로 저장한다.

## **5.2 Clarification Question / Answer**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"questionId": 15,<br />
"targetField": "transaction.refundResponsibility",<br />
"requirement": "REQUIRED_FOR_LEGAL_PRECHECK",<br />
"question": "취소·환불 책임은 음식점과 플랫폼 중 누구에게
있습니까?",<br />
"reason": "전자상거래 책임 주체를 판정하기 위해 필요합니다.",<br />
"answer": "음식점이 실제 판매자이며 플랫폼은 중개만 합니다.",<br />
"source": "사용자 확인",<br />
"status": "USER_CONFIRMED"<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

질문 답변은 원문에 자동 삽입하지 않고 구조화된 확정값으로 저장한다.
사용자가 “보완 내용 반영”을 실행할 때 Idea Origin 새 Version에 포함한다.

## **5.3 Idea Legal Precheck Result**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"status": "PASS_WITH_CONDITIONS",<br />
"summary": "...",<br />
"requiredUserInputs": [],<br />
"findings": [],<br />
"guardrails": {<br />
"hardConstraints": [],<br />
"prohibitedPatterns": [],<br />
"conditionalConstraints": [],<br />
"requiredDisclosures": [],<br />
"requiredOperationalControls": []<br />
},<br />
"evidence": [],<br />
"conceptBuilderAllowed": true,<br />
"registryVersion": "legal-registry-v1"<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **5.4 Concept Draft와 추적 정보**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"conceptDraftId": "D-12",<br />
"conceptName": "...",<br />
"targetSegment": {},<br />
"positioning": "...",<br />
"featureSet": [],<br />
"pricing": {},<br />
"revenueModel": {},<br />
"channels": [],<br />
"operatingModel": {},<br />
"newAssumptions": [],<br />
"newBusinessActivities": [],<br />
"originTrace": [],<br />
"legalTrace": []<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **5.5 Concept Validation 결과**

| **검증**                 | **가능한 결과**    | **처리**                                              |
|--------------------------|--------------------|-------------------------------------------------------|
| Origin Integrity         | PASS / FAIL_ORIGIN | FAIL이면 Draft 폐기 후 다른 Concept 생성              |
| Concept Legal Validation | PASS / FAIL_LEGAL  | FAIL이면 Draft 폐기 후 위반 패턴을 제외하고 대체 생성 |
| 최종 상태                | ELIGIBLE           | 두 PASS를 모두 받은 Concept만 사용자 후보로 노출      |

# **6. junwoo 법률 구현의 기술적 이식 범위**

기존 저장소의 Entity와 Controller를 복사하는 것이 아니라, 실제로 구현된
법률 조사 파이프라인과 결과 설명 기술을 현재 TaskRun·Idea Origin·Concept
구조에 맞게 옮긴다.

## **6.1 실제로 이식할 기술 자산**

| **기술 자산**            | **현재 서비스 적용**                                                                 |
|--------------------------|--------------------------------------------------------------------------------------|
| 규제 Route Registry      | 사업 특성별 규제 영역, 조사 법령, 조문 Focus Keyword를 Versioned Asset으로 관리      |
| Category Map / Rules     | Route→Category와 조문 제목→Category를 분리하여 같은 근거의 무차별 복제를 방지        |
| LLM Route 판정           | Idea Origin 또는 Concept에서 관련 규제 Route, 적용 가능성, 근거 인용, 부족 정보 추출 |
| 근거 인용 검증           | LLM 인용문이 실제 Snapshot에 존재하는지 부분문자열/Statement ID로 확인               |
| 법제처 API Adapter       | 법령 검색, 현행 식별, 시행일, 조문 조회, 공식 링크, 조회 시점 저장                   |
| 조문 Normalizer/Selector | 조문을 REQUIREMENT·SANCTION·SCOPE·SUPPORTING·EXCLUDE로 분류                          |
| 구조화 Evidence          | 법령명·조문·쉬운 설명·사업 관련 이유·발췌·시행일·URL·검증 상태 저장                  |
| 5단 Reasoning            | 입력 근거→규제 영역→의무→위반 결과→필요 조치                                         |
| 질문·수정 제안           | 판정에 필요한 질문과 Origin 수정 제안을 구조화하여 사용자 확인으로 연결              |
| 결과 이중 검증           | AI Server Pydantic + Spring Validator가 Category·Citation·enum·필수 배열을 모두 검사 |

## **6.2 현재 아키텍처에 맞게 바꿀 부분**

| **junwoo 기반**            | **현재 적용**                                              |
|----------------------------|------------------------------------------------------------|
| StructuredPlan             | IdeaOriginVersion 및 ConceptVersion                        |
| AnalysisJob                | 기존 TaskRun / TaskAttempt / TaskResult                    |
| /api/v1 법률 API           | /api/v2 Journey API + /internal/v1/ai/executions           |
| LEGAL_REVIEW 단일 작업     | IDEA_LEGAL_PRECHECK / CONCEPT_LEGAL_VALIDATION 두 Contract |
| 장시간 동기 호출           | 202 + taskRunId + 상태 Polling을 기본으로 사용             |
| ReviewCycle 발행 중심      | Idea Origin 보완과 Guardrail Version 중심                  |
| 법률 보고서 후 조건부 진행 | 실패 Concept 비노출·대체 생성                              |

## **6.3 법제처 API 연결 원칙**

- API Key는 AI Server 환경변수로만 관리하고 Frontend·공개
  API·TaskResult·로그에 노출하지 않는다.

- LLM이 법령명·조문번호를 생성하는 구조가 아니라 Registry와 법제처 API가
  법령 존재를 결정한다.

- Evidence에는 조회 시점, 시행일, 공식 링크, Registry Version을
  기록한다.

- Registry에 관련 Route가 없거나 법령 조회가 불완전하면 REGISTRY_GAP
  또는 SOURCE_PARTIAL을 명시한다.

- 법률 결과는 법률 자문이 아닌 사업기획 단계의 사전검토라는 책임 한계를
  UI와 결과에 유지한다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>MOLEG_API_KEY=...<br />
MOLEG_API_BASE_URL=...<br />
LEGAL_REGISTRY_VERSION=legal-registry-v1<br />
LEGAL_PROVIDER_TIMEOUT_SECONDS=...<br />
# 키 값이나 요청 원문 전체는 로그에 남기지 않는다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **6.4 그대로 가져오지 않을 결함**

- Java 요청에는 있으나 실제 Pipeline HTTP Body에서
  mode·rerunCategories·confirmedFacts가 누락되는 계약 불일치

- 한 요청을 장시간 유지하고 Semaphore 1개로 직렬화하는 실행 방식

- Mock 기반으로만 전체 Loop가 통과한 상태를 실제 Provider 검증 완료로
  간주하는 방식

- Registry를 완전한 법률 지식베이스처럼 취급하는 방식

- 법률 Provider·Prompt·Registry Version을 결과에 남기지 않는 방식

# **7. Concept 생성·검증·폐기·대체 생성**

| **초안 생성** | →   | **Origin 검사** | →   | **법률 검사** | →   | **ELIGIBLE** | →   | **적격 3개** |
|---------------|-----|-----------------|-----|---------------|-----|--------------|-----|--------------|

| **현재 생성 정책 \|** 목표 적격 Concept 3개 · 최초 초안 3개 · 실패한 수만큼 내부 대체 생성 · 최대 대체 2라운드 · 최대 검사 후보 9개 |
|-------------------------------------------------------------------------------------------------------------------------------------|

## **7.1 생성 입력**

- Idea Origin Snapshot

- 사용자가 이미 확정하여 잠긴 가격·수익모델·채널·역할·내부 제약

- Legal Guardrail Set

- 생성 설정: targetEligibleCount=3, maxReplacementRounds=2,
  maxInspectedCandidates=9. 변주 축과 출력 형식은 STAGE_LOCAL 값으로
  관리한다.

## **7.2 Concept Builder가 변경할 수 없는 값**

| **구분**      | **변경 금지 항목**                                               |
|---------------|------------------------------------------------------------------|
| Origin        | problem, coreValue, fixedValues, 대상의 본질, 핵심 해결 방식     |
| 사용자 확정값 | 확정 가격, 수익모델, 채널, 판매·결제·환불 역할, 데이터 처리 방식 |
| 법률          | hardConstraints, prohibitedPatterns, 필수 고지·운영 통제         |

## **7.3 실패 Concept 처리**

1\. 최초 3개 초안을 생성하고 각 초안을 Origin Integrity → Concept Legal
Validation 순서로 검사한다.

2\. 검증 실패 Draft는 내부 기록만 남기고 일반 사용자 후보 목록에는
노출하지 않는다.

3\. 실패한 수만큼만 대체 후보를 생성한다. 통과한 후보를 다시 생성하여
비용과 지연을 늘리지 않는다.

4\. 실패 사유와 위반 구조 키를 다음 생성의 negative constraint로
전달하고 동일·유사 후보 재생성을 막는다.

5\. 대체 생성은 최대 2라운드까지 수행하며 전체 검사 후보는 최대 9개다.

6\. 적격 후보 3개를 확보하면 즉시 종료하고 사용자에게 세 후보를 함께
표시한다.

7\. 최대 시도 후 3개를 확보하지 못하면 FAIL 후보를 조건부 통과시키지
않고, 부족한 Origin 또는 법률 입력을 안내한다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>app:<br />
journey:<br />
concept:<br />
target-count: 3<br />
max-replacement-rounds: 2<br />
max-inspected-candidates: 9</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **7.4 ELIGIBLE 이후 불변성**

Origin과 법률 검증을 모두 통과한 Concept만 ELIGIBLE이 된다. ELIGIBLE
Concept은 고객·문제·핵심가치·기능·가격안·수익모델·채널·거래 구조·데이터
처리 방식이 동결된다.

이번 구축은 ELIGIBLE 3개를 표시하는 시점에서 끝난다. 후속 분석이
재설계될 때도 Concept을 분석할 수는 있지만 새 사업 내용을 추가하거나
정의를 바꾸면 안 된다.

# 8. 후속 시장 → BM → 기술·운영 → 재무 입력 연결 — 현재 범위 제외

| **후속 참고 \|** 이 장은 전달받은 입력을 버리지 않고 다음 재설계에 사용할 연결 기준이다. 이번 코드 구축에서 시장·BM·기술운영·재무 API, 화면, 검색 Provider를 새 Concept 흐름에 연결하지 않는다. |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|

## **8.1 시장 분석**

| **입력**                                                               | **출력**                                                                                          |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Idea Origin 공통 맥락 + ELIGIBLE Concept 정의 + 지역·진입시점·조사질문 | 직접/인접/대체시장, 경쟁·대안, 가격 분포, 시장 규모/성장 근거, Trend, 추천 가격 범위, 신뢰도·한계 |

- 동일 시장 정의·지역·기관·기간의 시계열 근거가 있을 때만 정식 성장률로
  제공한다.

- 조건이 부족하면 인접시장 또는 대체 성장 신호로 구분한다.

- 외부 근거는 source ID, 원문 URL/제목, 조사 시점, 직접/인접/대체 구분을
  남긴다.

## **8.2 BM 분석**

| **입력**                                                                         | **출력**                                                                                                  |
|----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| 동결 Concept + 사용자 확정 가격/수익/채널 + 시장 근거 + 법률 가격/채널/책임 제약 | 가격 분석, 수익원 분석, 채널 전략 분석, 정산 흐름, Cost Driver, CAC 구조, Unit Economics Frame, 위험·검증 |

BM은 Concept에 없는 광고·구독·중개 모델을 새로 추가하지 않는다. 분석
결과로 구조가 불가능하면 REWORK_REQUIRED를 반환한다.

## **8.3 기술·운영 분석**

| **입력**                                                                              | **출력**                                                                                                      |
|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| 동결 기능·운영 모델 + BM 거래/정산/채널 + Legal 운영 제약 + 사용자 보유 자원/Capacity | 운영 모델, 인력, 인프라, Capacity, 변동·고정 비용 Driver, 초기 구축비, 규제 구현비, 단위 원가, 기술·운영 위험 |

## **8.4 재무 분석**

| **사업 유형** | **매출원가 기본 구조**                                     |
|---------------|------------------------------------------------------------|
| 실물·단품     | 연간 판매수량 × 단위 제조·매입·물류 원가                   |
| 구독·SaaS·앱  | 활성 사용자 × 사용자당 월간 서버·API·지원 원가 × 12        |
| 중개 플랫폼   | 연간 거래 수 × 건당 결제·정산·알림·지원 비용 + 기본 인프라 |

- 고정 운영비: 연간 고정 인건비, 임차/관리비, 고정 인프라비

- 초기 투자금: 개발/R&D, 설비/인프라, 특허/인허가

- 3개년 목표: UNIT_SALES, ACTIVE_USERS, TRANSACTIONS 등 사업모델에 맞는
  Metric Type

- CAC는 고정 영업·마케팅비와 변동 획득비를 분리하여 판관비 중복 계산을
  방지

- Benchmark 값은 ESTIMATED로 노출하고 사용자 승인 후에만 계산 확정값으로
  사용

재무 출력: 매출, 원가, 매출총이익, 고정비, 판관비, 영업이익, 공헌이익,
BEP, Runway, 최대 필요자금, ROI, NPV, IRR, Scenario, 민감도, 계산 불가
사유.

## **8.5 같은 개념의 값을 덮어쓰지 않는 예**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>{<br />
"pricing": {<br />
"userIntent": 38000,<br />
"conceptVariant": 35000,<br />
"marketObservedRange": {"min": 32000, "max": 42000},<br />
"bmRecommended": 39000,<br />
"financialConfirmed": 38000<br />
}<br />
}</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

각 값은 의미와 출처가 다르며 마지막 값을 앞의 값 위에 덮어쓰지 않는다.
재무 계산에는 사용자가 확인한 financialConfirmed만 사용한다.

# **9. Version·잠금·Stale 전파**

| **변경 원인**        | **STALE 처리 대상**                                     |
|----------------------|---------------------------------------------------------|
| Idea Origin 변경     | Legal Precheck, Guardrail, 모든 Concept, 모든 후속 분석 |
| Legal Guardrail 변경 | 기존 Concept, 시장·BM·기술운영·재무                     |
| Concept 새 Version   | Origin/법률 재검증, 시장·BM·기술운영·재무               |
| 시장 분석 새 Version | BM·기술운영·재무                                        |
| BM 분석 새 Version   | 기술운영·재무                                           |
| 기술운영 새 Version  | 재무                                                    |
| 재무 가정 변경       | 재무 결과만 새 Version                                  |

- 현재 결과는 “가장 최신 생성 시각”이 아니라 입력 Version과 Hash가 현재
  Snapshot과 일치하는 결과다.

- 사용자가 확정한 값은 locked=true로 저장하며 AI·후속 단계가 변경 요청을
  제출할 수는 있어도 직접 덮어쓰지 못한다.

- 분석 단계가 REWORK_REQUIRED를 반환하면 Concept Builder로 돌아가 새
  ConceptVersion을 만들며 기존 ELIGIBLE Concept을 직접 수정하지 않는다.

# **10. UX 재설계**

## **10.1 Idea Clarification Workspace**

| **화면 영역**  | **표시 내용/행동**                                                      |
|----------------|-------------------------------------------------------------------------|
| 이해한 사업    | 문제·고객·해결·핵심가치·카테고리·거래 구조를 구조화 표시                |
| 필수 보완 질문 | 질문별 답변과 확인 출처 입력, 관련 단계와 필요한 이유 표시              |
| 사용자 확정값  | 잠긴 값과 어느 단계에서 사용되는지 표시                                 |
| AI 가정        | 확정 사실과 분리하고 승인·수정·삭제 가능                                |
| 원문 편집      | 보조 기능으로 유지하되 기본 보완 경로로 사용하지 않음                   |
| 진행 준비도    | Idea Origin·Legal Precheck·Concept Build 각각 READY/NEEDS_INPUT/BLOCKED |

## **10.2 Legal Precheck 화면**

- 종합 상태와 Concept Builder 진행 가능 여부

- 규제 Category별 적용 여부·위험도·신뢰도

- Idea 근거→규제 영역→의무→제재→Guardrail의 5단 설명

- 법제처 근거 조문, 쉬운 설명, 관련 이유, 시행일, 공식 링크

- 추가 질문과 Origin 수정 제안

- 최종 생성된 Legal Guardrail Set

## **10.3 Concept Builder 화면**

- 생성 중 Draft와 내부 실패 후보를 일반 사용자에게 실시간 후보로
  노출하지 않는다.

- 검증을 통과한 적격 Concept 3개만 한 번에 표시하고, 각 후보의 Origin
  보존 내역과 Guardrail 반영 내역을 확인할 수 있게 한다.

- 최대 내부 대체 생성 후에도 적격 Concept이 3개보다 적으면 통과 기준을
  낮추지 않고 부족 사유와 필요한 Origin·법률 입력 보완을 안내한다.

## **10.4 후속 분석 단계의 사용자 입력 UX — 참고**

각 단계에서 필요한 입력을 시작 화면에 한꺼번에 요구하지 않고, 해당 단계
진입 시 “현재 확정값 / 앞 단계 초안 / 외부 Benchmark / 사용자가 확인할
값”을 나란히 보여준다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>재무 예시</strong></p>
<p>사용자 값이 없으면 기술운영 추정과 외부 Benchmark 범위를 먼저
제시한다. 사용자는 값을 수정하거나 승인한다. 승인 전에는 재무 결과를
공식 결과로 저장하지 않는다.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# **11. 신속하지만 제대로 구축하는 구현 계획**

구현 계획은 거대한 Phase와 장시간 검증을 한 작업에 묶지 않는다. 각
묶음은 실제 사용자 흐름 하나를 완성하거나 다음 묶음의 Contract를
안정시키는 수준으로 제한한다. Codex는 코드 구축에 집중하고, 장시간
Build·Test·Docker·Git 작업은 사용자가 별도로 수행한다.

## **11.1 공통 구축 원칙**

- 분석은 5~10분 내 기존 계약과 변경 파일을 확인한 뒤 바로 코드 수정에
  들어간다.

- 한 묶음에서 Domain·API·AI Schema·Frontend를 모두 필요 이상으로
  확장하지 않는다.

- 기존 Landing·Auth·Admin·완료된 Journey 기능을 불필요하게 재작성하지
  않는다.

- 공통 Framework를 먼저 크게 만들기보다 현재 필요한 두 Legal Task와 입력
  Metadata에 맞는 얇은 공통 구조를 만든다.

- 실제 Provider와 법제처 API를 사용하며 성공을 꾸미는 Fixture Fallback을
  만들지 않는다.

- 기존 Migration을 수정하지 않고 추가 Migration만 사용한다.

- 각 묶음 완료 보고에는 변경 파일, 핵심 결정, 사용자가 실행할 명령,
  확인할 화면·로그, 실행하지 않은 항목을 포함한다.

## 11.2 현재 범위 구현 묶음 개요

| **묶음**                         | **핵심 범위**                                                                         | **완료 의미**                              |
|----------------------------------|---------------------------------------------------------------------------------------|--------------------------------------------|
| **1. Idea Origin·Clarification** | 입력 두 축, Origin Version, 보완 질문·답변, 사용자 확정/AI 가정 UI                    | 원문 재작성 없이 Idea Origin을 확정·복원   |
| **2. Legal Precheck v2**         | junwoo Registry·Category Rule 이식, 법제처 API, Evidence·Reasoning·Guardrail, TaskRun | 실제 공식 근거로 Concept Builder 제약 생성 |
| **3. Concept Eligibility Loop**  | 초안 생성, Origin/법률 PASS·FAIL, 내부 대체 생성, 적격 3개 표시                       | 실패 후보 비노출, 검증 통과 3개 확보       |
| **4. 통합·현재 범위 마감**       | Route·상태 복원, Version/Stale, 대체된 Idea/Legal/Concept Legacy 정리, env 안내       | 현재 범위가 새로고침 후 안정적으로 동작    |

## 11.3 묶음 1 — Idea Origin과 Clarification

- 기존 IdeaSource·IdeaVersion을 보존하면서 IdeaOriginVersion과 입력
  Metadata를 추가한다.

- USER_CONFIRMED 등 출처 유형과 requiredForStages를 한 Contract로
  저장하되 범용 Workflow Engine을 먼저 만들지 않는다.

- 현재 Idea Interpretation을 Origin Draft로 변환하고 누락 필드만
  질문으로 생성한다.

- 질문 답변·출처를 저장하고 “보완 내용 반영” 시 한 번에 새 Origin
  Version을 만든다.

- 사용자 확정값, AI 가정, 미확정값을 기존 디자인 시스템으로 구분한다.

완료 기준: 사용자가 긴 원문을 다시 작성하지 않고 Idea Origin 필수값을
채워 확정하고, 새로고침 후 같은 Version을 확인할 수 있다.

## 11.4 묶음 2 — Legal Precheck v2

- junwoo 브랜치에서 구현한 Registry·Category Map·Category Rules·법령
  조회·조문 선별 구조를 현재 ai/legal 모듈과 TaskRun 계약으로 이식한다.

- 법제처 API Client와 Cache, 법령/조문 Normalizer, 오류·불완전 Source
  상태를 구현한다.

- Idea Origin의 근거 문장 검증, 구조화 Evidence, 5단 Reasoning, 추가
  질문·수정 제안을 구현한다.

- Spring이 결과를 재검증하고 Legal Guardrail Set을 Version으로 저장한다.

- INSUFFICIENT_INFORMATION·REVISION_REQUIRED·PROHIBITED에서는 Concept
  Builder를 차단한다.

완료 기준: 실제 법제처 근거가 저장·복원되고, Concept Builder가 사용할
hardConstraints와 prohibitedPatterns가 생성된다.

## 11.5 묶음 3 — Concept Eligibility Loop

- Concept Builder 입력을 Idea Origin + 잠긴 사용자값 + Legal Guardrail로
  교체한다.

- Origin Trace, Legal Trace, New Assumptions, New Activities를 결과
  Contract에 포함한다.

- Origin Integrity와 Concept Legal Validation을 PASS/FAIL로 구현한다.

- 최초 3개, 실패 수만큼 대체, 최대 2라운드·9후보 정책을 구현한다.

- 실패 Draft는 내부 기록만 남기고 적격 3개 또는 적격 후보 부족 결과만
  사용자에게 표시한다.

완료 기준: 법률 문제가 있는 후보에 코멘트를 붙여 통과시키지 않고, 서로
구분되는 적격 Concept 3개를 사용자에게 보여준다.

## 11.6 묶음 4 — 통합·현재 범위 마감

- Idea Origin 변경 시 Legal Precheck·Guardrail·Concept를 STALE 처리하고
  current 결과를 입력 Version/Hash로 판정한다.

- TaskRun 진행·실패·재시도·복원 상태를 Idea·Legal·Concept 화면에서
  일관되게 표시한다.

- 기존 Landing·Auth·공용 UI·후속 MVP 화면을 재작성하지 않는다.

- 새 흐름으로 완전히 대체된 Idea·Legal·Concept의 중복
  Route·Hook·Prompt·API만 기능 확인 후 제거한다.

- 시장 이후 단계로 자동 이동하거나 기존 MVP Snapshot을 억지로 연결하지
  않는다.

- 로컬 env example 복사와 필요한 Key 입력 방법, 사용자 실행 명령과 확인
  항목을 정리한다.

완료 기준: Idea 입력부터 적격 Concept 3개 표시까지 로컬 사용 흐름이 복원
가능하고, 후속 MVP는 손상 없이 분리되어 있다.

## 11.7 현재 범위에서 실행하지 않는 작업

- 시장·BM·기술운영·재무의 새 API·화면·검색 Provider 연결

- Concept 선택과 Persona·Interview·Marketing·Final Report 재연결

- 전체 Legacy 제거, Drop Migration, 서버 PDF와 운영 배포 마감

- GitHub Actions CI 복구와 전체 Test Matrix 실행

| **CI 운영 \|** 현재 CI 제외는 의도된 결정이다. 새 Journey와 Contract가 고정된 뒤 마감 작업을 시작할 때 복구한다. |
|------------------------------------------------------------------------------------------------------------------|

## 11.8 로컬 실행 환경 안내

저장소에 존재하는 env example 파일을 복사해 .env를 만들고 실제 Secret은
Git에 포함하지 않는다. 파일명은 저장소 기준으로 확인한다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>Copy-Item .env.example .env<br />
# 또는 저장소에 .env.demo.example만 있다면<br />
Copy-Item .env.demo.example .env</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>AI_PROVIDER=openai<br />
AI_API_KEY=...<br />
AI_MODEL=...<br />
AI_INTERNAL_SERVICE_TOKEN=...<br />
JWT_SECRET=...<br />
MOLEG_API_KEY=...<br />
MOLEG_API_BASE_URL=...<br />
LEGAL_REGISTRY_VERSION=legal-registry-v1</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

실제 변수명은 Spring 설정, AI Server 설정과 docker-compose가 참조하는
이름을 기준으로 맞춘다. 운영 Domain·HTTPS·Secret Manager·운영
DB·모니터링은 이번 범위에 포함하지 않는다.

# **12. Codex 작업 지시 운영 규칙과 템플릿**

## **12.1 Codex가 실제로 수행하는 것**

- 기존 코드와 Contract의 짧은 확인

- 필요한 코드·Migration·Prompt·Schema·UI 수정

- 정적 수준의 import, 타입, 호출 계약 확인

- 변경 파일과 사용자가 실행할 검증 명령 보고

## **12.2 기본적으로 수행하지 않는 것**

| **금지/보류 작업**   | **운영 방식**                                                            |
|----------------------|--------------------------------------------------------------------------|
| Git 작업             | branch, status, diff, add, commit, push, PR을 Codex가 수행하지 않는다.   |
| Docker 실행          | compose build/up/down과 장시간 로그 대기를 Codex 작업에 포함하지 않는다. |
| 전체 Build/Test/Lint | 실행 명령과 확인 기준만 보고하고 사용자가 수행한다.                      |
| 광범위한 정리        | 현재 묶음과 무관한 formatting, rename, 구조 재편을 하지 않는다.          |
| 가짜 성공            | 실제 Provider 실패를 fixture나 하드코딩 결과로 성공 처리하지 않는다.     |

## **12.3 빌드 오류 수정 예외**

사용자가 실제 Build 오류 로그를 제공하여 “빌드 오류를 고쳐라”라고 지시한
작업만 예외다. 이 경우 Codex는 오류를 재현·확인하기 위한 가장 작은
Compile 또는 Build 명령만 실행할 수 있다. 오류가 해결되면 전체
Test·Docker까지 확장하지 않고 중단한다.

- Backend: 전체 test가 아니라 필요한 경우 compileJava 또는 지정된 단일
  test

- Frontend: 전체 test가 아니라 Build 오류 확인에 필요한 npm build 또는
  해당 파일의 정적 확인

- AI Server: 전체 suite가 아니라 import/구문 또는 지정된 단일 contract
  test

## **12.4 작업 지시문 공통 서두**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>[작업명]<br />
저장소: C:\Users\seewo\Desktop\big_proj_01\new_2<br />
<br />
목표:<br />
- 이번 묶음의 사용자 흐름과 Contract만 구현한다.<br />
<br />
작업 방식:<br />
- 기존 파일·계약 확인은 최대 5~10분 후 바로 코드 수정한다.<br />
- 로컬 파일만 수정한다.<br />
- 기존 Migration은 수정하지 않고 새 Migration만 추가한다.<br />
- 실제 Provider·법제처 API 경로를 유지한다.<br />
<br />
금지:<br />
- Git/commit/push/PR/status/diff<br />
- Docker 실행<br />
- 전체 Build/Test/Lint<br />
- 현재 범위와 무관한 정리·리팩터링<br />
- Fixture 또는 하드코딩 성공 Fallback<br />
- 기존 Landing/Auth/Journey 기능의 임의 재작성<br />
<br />
예외:<br />
- 본 작업이 “제공된 빌드 오류 수정”인 경우에만 최소 Compile을 실행할 수
있다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **12.5 완료 보고 형식**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th>완료 보고:<br />
1. 구현한 사용자 흐름<br />
2. 핵심 Domain/API/AI Contract 변경<br />
3. 수정·추가 파일<br />
4. 기존 기능 보존 여부<br />
5. 사용자가 실행할 최소 Build/Test/Docker 명령<br />
6. 각 명령에서 확인할 항목<br />
7. 브라우저 확인 순서<br />
8. 예상 로그와 실패 시 확인 위치<br />
9. 실행하지 않은 항목<br />
<br />
Codex는 명령을 직접 오래 실행하지 않는다.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## **12.6 사용자 검증 명령 예시**

실제 명령은 해당 작업 묶음의 변경 범위에 맞게 최소화해서 보고한다.
아래는 형식 예시다.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th># Backend compile 또는 지정 테스트 — 사용자가 실행<br />
cd C:\Users\seewo\Desktop\big_proj_01\new_2\backend<br />
.\gradlew.bat compileJava<br />
# 필요 시 지정된 test만 실행<br />
<br />
# Frontend — 사용자가 실행<br />
cd ..\frontEnd<br />
npm.cmd run build<br />
<br />
# AI contract — 사용자가 실행<br />
cd ..\ai<br />
python -m pytest &lt;지정된 테스트 파일&gt; -q<br />
<br />
# 변경 서비스만 Docker rebuild — 사용자가 실행<br />
cd ..<br />
docker compose up -d --build ai-server backend frontend<br />
<br />
# 요청 ID 중심 로그 확인<br />
docker compose logs backend --since 5m --tail 400<br />
docker compose logs ai-server --since 5m --tail 400</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>신속 구축의 의미</strong></p>
<p>검증을 생략한다는 뜻이 아니라, 코드를 만드는 작업과 오래 걸리는
실행·검증을 분리한다는 뜻이다. Codex는 수정에 집중하고 사용자는 명시된
최소 명령과 브라우저 시나리오로 즉시 확인한다.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 부록 A. 현재 확정 정책과 후속 보류 항목

| **항목**            | **현재 결정**                                                     |
|---------------------|-------------------------------------------------------------------|
| **적격 Concept 수** | 3개로 확정                                                        |
| **대체 생성**       | 내부 처리, 실패한 수만큼 생성                                     |
| **대체 한도**       | 기본 2라운드, 최대 검사 후보 9개                                  |
| **Concept 판정**    | Origin PASS/FAIL + Legal PASS/FAIL, 점수 Threshold 없음           |
| **일반 웹 검색**    | 이번 범위 제외. 향후 카테고리·대안·경쟁사 선택형 보조로만 고려    |
| **법률 Source**     | Versioned Registry + 법제처 API를 현재 범위에 포함                |
| **후속 분석**       | 시장·BM·기술운영·재무 및 점수표는 별도 재설계                     |
| **UI 문구·디자인**  | 기존 디자인 레퍼런스를 따르며 상태·행동 문구를 구현 과정에서 완성 |
| **Legacy**          | 대체된 Idea·Legal·Concept 코드만 확인 후 제거, 후속 MVP 보존      |
| **CI**              | 현재 의도적 보류, Journey 확정 후 마감 전에 복구                  |
| **배포**            | 로컬 env 안내만 포함, 운영 배포 세부값은 후속                     |

# **부록 B. 핵심 브라우저 검증 시나리오**

1\. 자유 입력을 저장하면 AI가 Origin 필드를 구조화하고 사용자 확정값·AI
가정·누락값을 분리해 표시한다.

2\. 누락 질문에 답한 뒤 원문 전체를 다시 작성하지 않고 새 Idea Origin
Version을 확정한다.

3\. 법률 판단에 필요한 조건부 정보가 없으면 Legal Precheck가 진행되지
않고 정확한 질문을 표시한다.

4\. Legal Precheck가 실제 법제처 법령·조문과 구조화 Evidence를 저장하고
새로고침 후 복원한다.

5\. Legal Guardrail 위반 또는 Origin 훼손 Concept Draft가 사용자 후보에
노출되지 않는다.

6\. 실패한 후보 수만큼 내부 대체 생성하며 최대 한도 안에서 적격 Concept
3개가 함께 표시된다.

7\. 최대 시도 후 적격 3개를 만들지 못하면 실패 후보를 통과시키지 않고
보완해야 할 Origin·법률 정보를 안내한다.

8\. Idea Origin을 수정하면 Legal Precheck·Guardrail·Concept 결과가 STALE
처리되고 새 current 결과만 표시된다.

9\. 일반 웹 검색이 연결되지 않아도 Idea → Legal → Concept 흐름이 정상
진행된다.

10\. Concept 결과 화면에서 후속 분석으로 자동 이동하거나 기존 MVP 다음
단계에 잘못된 입력을 전달하지 않는다.

# **부록 C. 자료 출처와 반영 범위**

| **자료**                                                | **반영 내용**                                                                                            |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| **재설계 대화**                                         | Journey, 법률 Gate 2회, Concept 실패 폐기, 입력 4유형, 신속 구축 운영 원칙                               |
| **bm_input.docx**                                       | 사용자 확정값/Concept 생성값/이전 분석 전달값, 가격·채널 상속 규칙, 시장→BM 전달 구조                    |
| **junwoooooooo/aivle_big_project · junwoo branch 분석** | 법률 Registry, 법제처 API, Route/Category, Evidence, 5단 Reasoning, 질문·수정·증분 검토의 실제 구현 기술 |
| **bp_new_2 현 구조**                                    | Spring 업무 소유, FastAPI 내부 실행, TaskRun, Version, /api/v2 기반 적용                                 |
| **v0.3 후속 결정**                                      | Concept 3개, 내부 대체 생성 정책, 현재 범위 종료점, 일반 웹 검색 보조 원칙, 선택적 Legacy 정리, env 안내 |

이 문서는 Idea 입력부터 법제처 기반 법률 Precheck와 적격 Concept 3개
표시까지의 현재 구축 기준이다. 후속 분석 재설계가 시작되면 본 문서의
입력 출처·잠금·Concept 불변성 원칙을 유지하면서 별도 버전으로 확장한다.
