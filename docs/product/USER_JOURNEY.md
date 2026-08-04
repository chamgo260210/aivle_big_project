# Target User Journey

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: End-to-end journey, decisions and correction loops
- Supersedes: Legacy validation and marketing flow documents
- Implementation Status: NOT_STARTED

| Step | Purpose | Input direction | Output direction | User decision |
|---|---|---|---|---|
| Idea Intake | 불완전한 아이디어 수집 | TEXT, FILE. 질문 응답 UI는 TEXT로 수집 | source provenance | source 추가·제외 |
| Idea Normalization | 검증 가능한 표현으로 정리 | 현재 IdeaSource set | IdeaVersion 방향 | 정규화 결과 확인·수정 |
| Korean Legal Review | 한국 법률 위험·제약 확인 | 특정 IdeaVersion | cited LegalReviewRun | 수정 후 재검토 또는 계속 |
| Concept Builder | 복수 대안 생성 | idea + legal context | ConceptCandidate/Version | 후보 검토 |
| Quick Assessment | 빠른 상대 평가 | candidate versions | 비교 가능한 assessment | shortlist 구성 |
| Shortlist | 상세 분석 범위 제한 | quick results | selected candidate set | 상세 분석 대상 확정 |
| Detailed Analysis | 시장/BM/기술운영/재무 검토 | shortlist versions | DetailedAnalysisRun | 결과 검토·재실행 |
| Concept Selection | 최종 concept 선택 | 분석 + AI 권고 | ConceptSelection | 사용자가 명시적으로 선택 |
| Persona Cards | 선택 concept의 관점 구성 | selected version | Three-Layer cards | 카드 검토·수정 방향 |
| Independent Interviews | 관점별 독립 반응 수집 | card + interview context | interview별 결과 | 개별 재실행 여부 |
| Marketing Workspace | 시안 생성·관리 | concept + Persona evidence | assets/versions | 시안 편집·선택 |
| Persona A/B Comparison | 시안 상대 비교 | asset variants + Persona | relative comparison | 시안 판단 |
| Final Report | 검증 과정 snapshot | 선택된 upstream versions | FinalReportVersion/export | version 생성·조회 |

## Correction and return

사용자는 source 또는 normalized idea를 수정할 수 있다. 수정은 기존 결과를 덮어쓰지 않고 새 IdeaVersion을 만든다. 법률 검토가 실패하거나 출처를 확보하지 못하면 실패를 보존하고, 사용자는 입력을 수정해 새 run을 요청하거나 정책상 허용되는 다음 행동을 선택한다. 허용 gate의 상세는 P2에서 정한다.

Concept 수정, shortlist 변경, Persona 수정 또는 Marketing asset 변경도 새 version/run을 생성하는 방향이다. 과거 결과는 당시 입력과 연결된 채 유지된다.

## Stale principle

Upstream version이 바뀌면 해당 version을 입력으로 사용한 downstream 결과는 자동으로 최신 결과가 되지 않는다. Spring이 stale 여부의 source of truth이며 UI는 stale 결과를 현재 결과처럼 표시하지 않는다. 자동 무효화 범위와 재실행 gate는 P2에서 확정한다.

## Failure experience

Task 실패는 업무 결과 없음, 재시도 가능, 사용자 입력 수정 필요, 외부 연결 장애를 구분한다. 부분 결과가 존재하는 경우 검증된 범위만 표시하며 AI Server 오류가 사용자의 최종 결정을 변경하지 않는다.
