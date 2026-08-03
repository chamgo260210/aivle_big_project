# AI Server Boundary

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: AI orchestration, trust boundary and failure responsibilities
- Supersedes: Legacy AI integration documents
- Implementation Status: NOT_STARTED

## Responsibilities

AI Server는 Agent, MCP, model, prompt, AI 평가, provider client와 provider 오류 정규화를 관리한다. Korean Legal Review에서는 coordinated source adapter로 법제처 API의 공식 근거 확인과 법령 MCP의 검색·탐색을 조정한다. 법령 연동 secret은 AI Server 환경변수로만 주입하며 배포 secret mechanism을 사용하더라도 환경변수로 제공한다.

[Internal Spring–AI API v1 Contract](../contracts/INTERNAL_AI_API_V1_CONTRACT.md)에 따라 AI Server는 `/internal/v1/ai/executions`에서 한 TaskAttempt를 동기 실행하는 stateless service다. 업무 queue, callback, webhook 또는 durable idempotency를 소유하지 않는다. 실행은 network ambiguity로 중복될 수 있고 Spring만 결과를 단일 채택한다.

Spring이 bounded inline JSON으로 전달한 task input과 추출 text/chunk만 사용하며 Project, owner, DB entity, FILE bytes 또는 Storage object를 직접 조회하지 않는다. 결과는 Spring이 검증할 수 있는 identity, contract version, provenance, warning/error 방향을 포함한다. 법률 결과는 source channel, 조회 시각, 법령 식별자, 조문, degraded 상태와 전문가 검토 필요 방향을 반환한다.

## Hard prohibitions

- RDB driver, repository, SQL, migration
- S3/MinIO SDK, bucket credential, object key lookup
- presigned GET/PUT 또는 임의 Storage URL
- 업무 입력·결과의 local file persistence
- browser가 호출하는 public endpoint
- TaskRun 최종 상태나 사용자 결정을 자체 확정
- 사용자 JWT/session과 Spring entity serialization 수신
- FILE bytes/base64/binary payload 수신 또는 반환

## Trust boundary

Spring 입력도 AI Server 관점에서 size/schema/task allowlist, chunk 순서와 제한을 검증한다. MCP/provider 출력은 신뢰하지 않고 parsing, citation, length와 allowed result type을 검사한다. AI Server의 결과 역시 Spring에서 다시 검증한다. P2 계약은 provider/model/SDK/library-neutral이다.

## Failure isolation

Provider/MCP별 timeout과 오류는 다른 TaskAttempt나 PersonaInterview로 전파하지 않는다. 법령 MCP 또는 법제처 API 한쪽 실패는 가능한 근거와 누락 source를 표시한 degraded result로 격리한다. Persona 실행은 독립 context를 사용한다. retry 권고는 반환할 수 있지만 실제 retry와 최종 상태는 Spring이 결정한다. health는 process live와 provider/MCP dependency readiness를 구분한다.

## Current gap

현재 FastAPI artifact service의 presigned HTTP I/O와 mock banner local outputs는 Target에서 제거 대상이다. 초기 Target payload는 bounded inline JSON/text chunk이며 streaming/binary protocol은 후속 확장이다. model/provider/library는 각 provider-dependent slice 진입 전까지 DEFERRED 상태다.
