# Target Project Workflow

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Ordered workflow, gates, version and stale principles
- Supersedes: StructuredPlan-centered workflow documents
- Implementation Status: NOT_STARTED

## Canonical order

Idea Intake → Idea Normalization → Korean Legal Review → Concept Builder → Quick Assessment → Shortlist → Detailed Analysis → Concept Selection → Three-Layer Persona Cards → Independent Persona Interviews → Marketing Workspace → Persona-Based Marketing A/B Comparison → Persisted Final Report.

## Stage purposes

Idea 단계는 Source를 수집·추출한 뒤 optional AI Interpretation proposal을 생성하고, 사용자가 검토·수정·확정한 immutable IdeaVersion을 만든다. 초기 FILE은 DOCX와 일반 텍스트이며 Spring이 검증·저장·추출한다. InterpretationRun은 IdeaVersion을 자동 생성하지 않는다. Legal 단계는 법제처 API의 공식 근거 확인과 법령 MCP 탐색 결과, degraded 상태와 전문가 검토 필요 여부를 기록한다. Concept/Quick 단계는 모든 후보를 저비용 공통 입력으로 평가한 뒤 상세 분석 비용을 들일 shortlist를 정한다. Detailed 단계는 shortlist 후보에 analysis-specific 입력을 적용하며 Quick 결과를 상세 분석의 사실로 자동 승격하지 않는다.

Concept Selection은 AI 추천과 구별되는 사용자 gate다. Persona 단계는 Role and Context, Problem and Needs, Behavior and Decision layer를 사용하고 선택된 concept에 종속되며 각 interview는 독립 실행한다. Marketing 단계는 asset workspace와 상대 비교를 제공한다. Report 단계는 선택된 input/result version을 고정한 RDB snapshot이며 HTML view와 Spring이 Storage에 저장한 PDF export를 제공한다.

## Gate directions

- IdeaSource/Extraction 이후 사용자는 직접 USER_AUTHORED IdeaVersion을 확정하거나 AI Interpretation proposal을 검토·수정해 AI_ASSISTED IdeaVersion을 확정한다.
- 법률 실패 또는 수정 권고 후 입력 수정과 새 review run을 허용한다.
- Quick Assessment 이후 사용자가 shortlist를 확정한다.
- Detailed Analysis 이후 사용자가 concept를 선택한다.
- Persona Card와 Marketing asset은 downstream 실행 전에 사용자가 검토할 수 있어야 한다.
- Final Report 생성은 포함할 current versions를 Spring이 검증한 뒤 수행한다.

기본 여정은 순차적이지만 접근 가능성은 단일 Project stage enum만으로 결정하지 않는다. Project stage, resource/run status, 사용자 선택·확정 gate와 AI 실행 capability를 분리한다.

## Capability and gate matrix

Capability는 Spring이 Project lifecycle, owner scope, Service Policy, exact current reference, resource lifecycle, validity, user gate와 conflicting TaskRun을 사용해 계산한다. Stage는 사용자 위치 표시이며 capability의 별칭이나 유일 입력이 아니다.

| Workflow gate | Required capability | Required gate/reference | Blocking direction |
|---|---|---|---|
| Idea edit | `CAN_EDIT_IDEA` | owner-scoped active/editable Project | maintenance, archive, edit conflict |
| Idea interpretation | `CAN_INTERPRET_IDEA` | current validated IdeaSourceExtraction 하나 이상 | AI policy, bounded payload, same-input active TaskRun |
| Legal review | `CAN_RUN_LEGAL_REVIEW` | confirmed current IdeaVersion | stale/missing idea, policy, active same-input task |
| Concept generation | `CAN_GENERATE_CONCEPTS` | exact current IdeaVersion의 current `PASS` 또는 `PASS_WITH_CONDITIONS` LegalReviewRun | 다른 legal result는 legal gate 차단 |
| Quick assessment | `CAN_RUN_QUICK_ASSESSMENT` | current exact ConceptVersion | stale candidate/version 또는 task conflict |
| Shortlist | `CAN_SET_SHORTLIST` | USER가 exact ConceptVersion 하나 이상 선택 | AI ranking은 자동 gate 통과 불가 |
| Detailed analysis | `CAN_RUN_DETAILED_ANALYSIS` | current ShortlistDecision에 포함된 exact current ConceptVersion | shortlist 밖 또는 stale version 차단 |
| Concept selection | `CAN_SELECT_CONCEPT` | USER가 exact ConceptVersion 하나 선택 | AI recommendation은 자동 selection 불가 |
| Persona study | `CAN_CREATE_PERSONA_STUDY` | current ConceptSelection/selected ConceptVersion | stale selection 차단 |
| Persona Card generation | `CAN_GENERATE_PERSONA_CARDS` | current non-stale Study/Selection/selected ConceptVersion | AI policy 또는 conflicting generation Run 차단 |
| Persona interview | `CAN_RUN_PERSONA_INTERVIEW` | confirmed exact current PersonaCardVersion | Persona별 active task conflict; 다른 Persona는 독립 |
| Marketing | `CAN_USE_MARKETING_WORKSPACE` | current selection/persona/interview evidence | stale upstream이나 policy 차단 |
| Final report generation | `CAN_GENERATE_FINAL_REPORT` | 포함할 exact current upstream set과 user decisions | missing/stale upstream, active generation task 차단 |
| Final report export | `CAN_EXPORT_FINAL_REPORT` | persisted current available FinalReportVersion | runtime-only/browser 조립 결과는 허용하지 않음 |

법률 결과 `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED`는 concept generation을 허용하지 않는다. Correction은 기존 IdeaVersion을 수정하지 않고 새 IdeaVersion과 새 review chain을 만든다.

## Version and stale

업무 결과는 생성 당시 upstream version을 참조한다. backtracking을 허용하며 upstream 변경은 과거 결과를 삭제하지 않고 관련 downstream을 `STALE`로 표시한다. stale 결과는 history 조회에는 남지만 current 결과나 실행 gate 충족 근거로 사용하지 않는다. current pointer가 stale target을 가리키지 않도록 해제하거나 검증된 새 current reference로 교체한다. immutable history는 수정하지 않으며 재사용·복사·재실행은 명시적 capability와 사용자 결정으로 다룬다.

FinalReportVersion은 upstream stale 전파 후에도 immutable snapshot으로 남고 자동 재작성되지 않는다. 새 report는 current exact reference 집합을 다시 검증하는 명시적 generation command로만 생성한다.

## Failure and retry

Workflow 상태와 TaskRun 상태는 분리한다. AI-backed Domain Run은 요청 수락 후 TaskRun과 1:1로 결합한다. retry는 동일 TaskRun의 새 TaskAttempt이고 사용자의 rerun은 새 Domain Run과 새 TaskRun이다. TaskRun `SUCCEEDED`만으로 Domain result 성공을 확정하지 않고 exact input에 대한 검증·채택 TaskResult를 함께 요구한다. timeout/retry 후 성공한 attempt만으로 이전 사용자 결정을 암묵적으로 교체하지 않는다. 법령/AI 장애는 실패 provenance를 남기고 재시도 가능성을 Spring이 판단한다. Persona interview 하나의 실패가 다른 Persona 결과를 오염시키지 않는다.

Public command, status code와 JSON 표현은 [Public API v2 Contract](../contracts/PUBLIC_API_V2_CONTRACT.md)를 따른다. 모든 Workflow command는 `/api/v2/projects/{projectId}` owner scope 아래에서 capability와 exact current reference를 다시 검증한다.

## Exclusions

문서 완성률, 고정 section completion, Persona 토론, 시장반응/구매확률, 실제 A/B conversion, runtime-only report는 Target Workflow에 포함하지 않는다.
