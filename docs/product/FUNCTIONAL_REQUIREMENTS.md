# Functional Requirements

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Identified target capabilities without detailed schemas
- Supersedes: Legacy feature and product requirement documents
- Implementation Status: NOT_STARTED

| ID | Requirement | Planned phase |
|---|---|---|
| FR-001 | 인증 사용자는 owner-scoped Project를 생성·조회·수정·삭제한다. | Stable Core/P3 |
| FR-002 | Project는 여러 IdeaVersion과 IdeaSource provenance를 보유한다. | P4 |
| FR-003 | IdeaSource logical type은 TEXT와 FILE이며 질문 응답 UI 입력은 TEXT source로 기록한다. 초기 FILE allowlist는 DOCX와 일반 텍스트로 제한한다. | P2/P4 |
| FR-004 | 사용자 입력 수정은 과거 version을 덮어쓰지 않고 새 IdeaVersion을 만든다. | P4 |
| FR-005 | 시스템은 exact IdeaSourceExtraction을 AI로 해석·정규화한 proposal을 제공하고, 인증된 사용자는 이를 그대로 또는 수정해 별도 immutable IdeaVersion으로 확정한다. | P4 |
| FR-006 | 법률 검토는 법제처 API의 원문·식별자·현재성 확인과 법령 MCP의 검색·탐색을 조정하고 source channel, 조회 시각, 조문, degraded 상태와 `EXPERT_REVIEW_REQUIRED`를 추적한다. | P4 |
| FR-007 | 법률 실패·수정 후 새 IdeaVersion 또는 LegalReviewRun으로 반복한다. | P4 |
| FR-008 | 복수 ConceptCandidate와 ConceptVersion을 생성·조회한다. | P5 |
| FR-009 | Quick Assessment는 모든 Concept 후보에 shared core snapshot을 사용하는 저비용 공통 상대 평가를 제공한다. | P5 |
| FR-010 | 사용자는 shortlist를 명시적으로 확정한다. | P5/P6 |
| FR-011 | shortlist 후보에 analysis-specific 입력·출력으로 시장·BM·기술운영·재무 Detailed Analysis를 수행하며 Quick 결과를 사실로 자동 승격하지 않는다. | P6 |
| FR-012 | AI 권고와 별도로 사용자가 ConceptSelection을 기록한다. | P6 |
| FR-013 | 선택 concept에 Role and Context, Problem and Needs, Behavior and Decision의 Three-Layer Persona Card를 생성·검토하며 실제 고객 통계나 구매확률로 표현하지 않는다. | P7 |
| FR-014 | 각 PersonaInterview는 다른 Persona와 독립적으로 실행·재시도된다. | P8 |
| FR-015 | MarketingWorkspace는 asset 생성, 편집, version 조회를 지원한다. | P9 |
| FR-016 | MarketingComparisonRun은 Persona 기반 시안 상대 비교를 제공한다. | P9 |
| FR-017 | 비교를 실제 사용자 A/B, 구매확률 또는 전환율로 표현하지 않는다. | P9 |
| FR-018 | FinalReportVersion은 구조화 snapshot과 provenance를 RDB에 저장한다. | P10 |
| FR-019 | 현재·이전 report version을 조회한다. | P10 |
| FR-020 | Final Report는 persisted snapshot 기반 HTML view를 제공하고 Spring이 생성한 PDF export를 Object Storage에 저장한다. 초기 Markdown export는 제공하지 않는다. | P10 |
| FR-021 | state + capability model에 따라 backtracking을 허용하고 upstream 변경 시 관련 downstream을 `STALE`로 판정하며 사용자 gate와 AI 실행 capability를 분리한다. | P2/P4–P10 |
| FR-022 | Admin은 사용자/역할, Project, audit, TaskRun, Storage, AI/법령 연결을 운영한다. | P11 |
| FR-023 | Service Policy는 maintenance, Project 생성, upload, AI, report 생성 제어 방향을 제공한다. | P11 |
| FR-024 | legacy Workflow API/route에 신규 compatibility 경로를 만들지 않고 P12에서 제거한다. | P3–P12 |
| FR-025 | TaskRun은 업무 요청과 현재 최종 상태를, TaskAttempt는 개별 실행·retry·timeout·오류·응답을 소유하고 polling/event wake에 중립적인 실행 계약을 제공한다. | P3 |
| FR-026 | AI-backed Domain Run은 요청 수락 후 정확히 하나의 TaskRun과 연결하며 retry는 같은 TaskRun의 새 TaskAttempt, 사용자 rerun은 새 Domain Run과 새 TaskRun으로 기록한다. | P2/P3–P10 |
| FR-027 | Spring은 Project status, owner scope, Service Policy, exact current reference, lifecycle, validity, user gate와 conflicting TaskRun으로 14개 canonical capability를 계산한다. Capability는 업무 source of truth로 별도 저장하지 않는다. | P2/P3–P10 |
| FR-028 | Domain Run 성공은 TaskRun `SUCCEEDED`만으로 확정하지 않고 exact binding/input, 검증·채택된 TaskResult와 domain validation을 요구한다. Execution lifecycle과 `CURRENT`/`STALE` validity를 분리한다. | P2/P3–P10 |
| FR-029 | Legal, shortlist, detailed, selection, persona, marketing과 report gate는 exact current non-stale reference와 명시적인 사용자 결정을 적용한다. | P2/P4–P10 |
| FR-030 | Public/domain 오류는 validation, missing, conflict, stale, capability/policy/gate, task/idempotency, payload, timeout, AI unavailable/invalid 결과를 stable code로 구분하고 provider raw body와 비밀값을 노출하지 않는다. | P2/P3–P10 |
| FR-031 | PersonaStudy 생성 후 별도 PersonaCardGenerationRun이 exact Study/Selection/ConceptVersion을 입력으로 하나 이상의 versioned synthetic PersonaCard를 생성하며, 사용자 편집은 새 PersonaCardVersion으로 기록한다. | P2/P7 |
| FR-032 | IdeaVersion creation은 USER_AUTHORED와 AI_ASSISTED를 구분하고, client-supplied createdBy/confirmedByUser가 아니라 authenticated confirmation command를 확정 근거로 사용한다. | P2/P4 |

Logical field semantics와 cardinality는 P2.2, workflow/task/status/error semantics는 P2.3에서 정의한다. 상세 public command/query JSON schema와 error envelope는 P2.4, internal Spring–AI JSON contract는 P2.5에서 결정한다.
