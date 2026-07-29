**erDiagram**

    USERS **||\-\-o\{** PROJECTS **:** "creates"

    USERS **||\-\-o\{** PERSONAS **:** "manages"

    PROJECTS **||\-\-||** PRODUCTS **:** "contains"

    PROJECTS **||\-\-||** LEGAL\_CHECKS **:** "checks legal/regulatory"

    PROJECTS **||\-\-o\{** FEASIBILITY\_ANALYSES **:** "evaluates"

    PROJECTS **||\-\-||** INTEGRATED\_REPORT\_DRAFTS **:** "accumulates for final report"

    PROJECTS **||\-\-o\{** SIMULATIONS **:** "runs"

    PROJECTS **||\-\-o\{** MARKETING\_MATERIALS **:** "generates"

    SIMULATIONS **||\-\-o\{** DISCUSSION\_LOGS **:** "records"

    SIMULATIONS **||\-\-o\{** MARKETING\_MATERIALS **:** "evaluates banner in"

    SIMULATIONS **\}o\-\-o\{** PERSONAS **:** "participates"

    PROJECTS **||\-\-o\{** REPORTS **:** "produces"

    FEASIBILITY\_ANALYSES **||\-\-o\{** REPORTS **:** "generates single/intermediate report"

    INTEGRATED\_REPORT\_DRAFTS **||\-\-o\{** REPORTS **:** "outputs final master report"

    USERS **\{**

        bigint id PK

        string email

        string password

        string name

        string role "ADMIN / USER"

        datetime created\_at

    **\}**

<br>    PERSONAS **\{**

        bigint id PK

        bigint user\_id FK "관리자\(ADMIN\) ID"

        string persona\_type "TARGET / MARKET"

        string name

        int age

        string gender

        string occupation

        jsonb spending\_habits "소비성향 및 가중치"

        text system\_prompt "LLM 프롬프트"

        datetime created\_at

    **\}**

<br>    PROJECTS **\{**

        bigint id PK

        bigint user\_id FK

        string title

        text business\_overview "사업개요"

        jsonb schedule\_and\_risk "일정 / 리스크"

        jsonb raw\_references "근거자료 목록"

        string status "DRAFT / ANALYZING / SIMULATING / COMPLETED"

        datetime created\_at

    **\}**

<br>    PRODUCTS **\{**

        bigint id PK

        bigint project\_id FK

        string product\_name

        string category

        text specification "제품/서비스 상세 스펙"

        text business\_model "비즈니스 모델"

        decimal unit\_cost "단위당 원가"

        decimal target\_price "희망 출시가"

        jsonb sales\_targets "판매 목표"

        jsonb target\_audience "목표 타겟"

    **\}**

<br>    LEGAL\_CHECKS **\{**

        bigint id PK

        bigint project\_id FK

        int status "0: 대기, 1: 승인\(Pass\), 2: 거부\(Reject\)"

        text ai\_raw\_response "AI 분석 결과 내용 \(단순 통과/거부 이유\)"

        datetime checked\_at

    **\}**

<br>    FEASIBILITY\_ANALYSES **\{**

        bigint id PK

        bigint project\_id FK

        string stage "MARKET / BM / TECH / FINANCIAL"

        int gate\_status "0: 보류/대기, 1: 승인\(Pass\), 2: 반려\(Reject\)"

        jsonb input\_params "AI 원본 응답 저장 \(Raw Response\)"

        jsonb result\_data "중간 보고서 및 평가용 구조화 데이터"

        datetime analyzed\_at

    **\}**

<br>    SIMULATIONS **\{**

        bigint id PK

        bigint project\_id FK

        string simulation\_type "FGD / CONCEPT\_TEST / AB\_TEST"

        int total\_rounds

        jsonb metrics "구매의향, PSM 적정가격 등 정량 결과"

        datetime executed\_at

    **\}**

<br>    SIMULATION\_PERSONAS **\{**

        bigint simulation\_id PK, FK

        bigint persona\_id PK, FK

    **\}**

<br>    DISCUSSION\_LOGS **\{**

        bigint id PK

        bigint simulation\_id FK

        bigint persona\_id FK

        int round\_number

        text statement "페르소나 발언 내용"

        datetime created\_at

    **\}**

<br>    MARKETING\_MATERIALS **\{**

        bigint id PK

        bigint project\_id FK

        bigint simulation\_id FK "연관된 시뮬레이션 \(선택\)"

        string banner\_title "광고 문구 / 카피"

        text image\_url "AI 생성 이미지 URL"

        jsonb ai\_generation\_params "생성 프롬프트/파라미터"

        jsonb persona\_feedback "배너/카피에 대한 페르소나 피드백"

        datetime created\_at

    **\}**

<br>    INTEGRATED\_REPORT\_DRAFTS **\{**

        bigint id PK

        bigint project\_id FK

        jsonb updated\_product\_info "타당성 검증 후 수정/추가된 프로덕트 정보"

        jsonb market\_result "FEASIBILITY\_ANALYSES \(MARKET\) result\_data"

        jsonb bm\_result "FEASIBILITY\_ANALYSES \(BM\) result\_data"

        jsonb tech\_result "FEASIBILITY\_ANALYSES \(TECH\) result\_data"

        jsonb financial\_result "FEASIBILITY\_ANALYSES \(FINANCIAL\) result\_data"

        jsonb simulation\_summary "SIMULATIONS 검증 결과 종합"

        jsonb marketing\_summary "AI 배너 및 카피 테스트 결과 요약"

        jsonb final\_combined\_content "최종 보고서용 통합 구조화 JSON"

        datetime updated\_at

    **\}**

<br>    REPORTS **\{**

        bigint id PK

        bigint project\_id FK

        bigint feasibility\_analysis\_id FK "단독/중간 보고서 연결용 \(선택\)"

        bigint integrated\_draft\_id FK "최종 통합 보고서 연결용 \(선택\)"

        string report\_type "INTERMEDIATE / FEASIBILITY\_SINGLE / INTEGRATED\_MASTER"

        jsonb summary\_json "렌더링용 최종 출력 데이터"

        text pdf\_url

        datetime generated\_at

    **\}**

<br><br>    VerificationInfo **\{**

        bigint verf\_id PK

        string email 

        text code

    **\}**

