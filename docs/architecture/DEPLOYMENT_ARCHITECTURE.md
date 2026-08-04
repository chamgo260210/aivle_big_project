# Deployment Architecture

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Deployable boundaries, health, readiness and isolation
- Supersedes: Legacy local and Compose architecture documents
- Implementation Status: NOT_STARTED

## Deployable boundaries

Frontend, Spring WAS, AI Server, RDB, Object Storage를 분리한다. Browser는 Spring public endpoint만 접근한다. AI Server는 internal network에서 Spring 요청만 받고 provider/MCP outbound만 허용한다. RDB/Storage network policy는 Spring workload만 허용하는 방향이다.

## Health and readiness

| Component | Liveness | Readiness direction |
|---|---|---|
| Frontend | static server process | Spring public endpoint reachability는 별도 관측 |
| Spring | process/JVM | RDB, Storage와 필수 내부 configuration |
| AI Server | process/event loop | selected provider/MCP configuration; dependency별 상태 분리 |
| RDB | server | connection/validation |
| Object Storage | service | bucket/access/integrity probe |

Admin은 AI Server, Storage, 법령 API 연결 상태를 구분해 표시해야 한다. 외부 provider 장애가 Spring core readiness 전체를 반드시 내리지는 않으며 Service Policy/TaskRun 실패로 격리하는 방향이다.

## Failure isolation

- AI Server 장애 중에도 auth, Project 조회와 저장된 report 조회를 가능한 범위에서 유지한다.
- 법령 API 장애는 LegalReviewRun에 격리한다.
- PersonaInterview 또는 Marketing run 하나의 실패는 다른 run을 손상시키지 않는다.
- Storage 장애는 file/export 작업을 차단하되 RDB에 성공으로 기록하지 않는다.
- retry storm을 막기 위한 backoff, concurrency와 circuit 정책은 구현 Phase에서 결정한다.

## Current versus Target

현재 Compose와 health 설정은 baseline이며 Target network policy, secret manager, scaling, observability 또는 production 배포 완료를 의미하지 않는다. CI/CD workflow는 Phase 1.1에서 변경하지 않는다.
