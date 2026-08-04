# Target Route Map Direction

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Route families without detailed frontend implementation
- Supersedes: Legacy current route map
- Implementation Status: NOT_STARTED

Public/auth/account/admin의 stable route는 재사용 가능성을 유지한다. 신규 Project Workflow route는 /app/projects/:projectId 아래에서 Idea, Legal, Concepts, Analysis/Selection, Personas/Interviews, Marketing, Report 영역을 표현한다.

정확한 path와 nesting은 Phase 2 이후 결정한다. legacy Workflow route에 compatibility redirect를 추가하지 않으며 전환 완료 후 제거한다. 이 문서는 현재 router 구현을 뜻하지 않는다.
