# Venture Verify Re-foundation

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Repository entry point and documentation status
- Supersedes: Previous root README
- Implementation Status: NOT_STARTED

Venture Verify의 목표는 하나의 아이디어를 입력부터 법률 검토, 대안 생성·평가, 독립 Persona 인터뷰, 마케팅 시안 비교, 저장 가능한 최종 보고서까지 검증하는 Project 단위 제품을 만드는 것이다.

목표 Workflow는 다음과 같다.

`Idea Intake → Idea Normalization → Korean Legal Review → Concept Builder → Quick Assessment → Shortlist → Detailed Analysis → Concept Selection → Three-Layer Persona Cards → Independent Persona Interviews → Marketing Workspace → Persona-Based Marketing A/B Comparison → Persisted Final Report`

이 Workflow는 아직 구현되지 않았다. 현재 코드는 DOCX, StructuredPlan, 12개 고정 section, 법률·타당성·재무 분석, fixed-cluster Persona, 예상 인터뷰·시장반응, 마케팅 콘텐츠 및 runtime report로 이어지는 legacy Workflow를 포함한다. 실제 상태는 [CURRENT_BASELINE](docs/CURRENT_BASELINE.md)에서 확인한다.

## Documentation

- Canonical 문서와 상태: [docs/README.md](docs/README.md)
- 제품 비전: [PRODUCT_VISION](docs/product/PRODUCT_VISION.md)
- 목표 Workflow: [PROJECT_WORKFLOW](docs/product/PROJECT_WORKFLOW.md)
- 목표 시스템 경계: [SYSTEM_ARCHITECTURE](docs/architecture/SYSTEM_ARCHITECTURE.md)
- 전환 계획: [IMPLEMENTATION_PHASES](docs/migration/IMPLEMENTATION_PHASES.md)
- 미결정 항목: [OPEN_DECISIONS](docs/product/OPEN_DECISIONS.md)
- 프로그램 상태: [PROGRAM_STATUS](docs/governance/PROGRAM_STATUS.md)
- Phase 상태: [PHASE_STATUS](docs/governance/PHASE_STATUS.md)
- 결정·변경·검증: [Governance index](docs/README.md#governance-and-operations)
- Stable Core 운영 정책: [ADMINISTRATION_POLICY](docs/operations/ADMINISTRATION_POLICY.md)

`docs/reference/design/`은 디자인 원본만 보관한다. `docs/api/openapi.yaml`, `docs/guide/`, `docs/example/`은 CI 또는 빌드 스크립트가 직접 읽기 때문에 임시 유지하는 legacy machine-consumed 입력이며 canonical 문서가 아니다.

## Current local execution

현재 구현에 한해서 다음 명령이 유효하다.

```powershell
docker compose up --build
```

개별 모듈은 `backend/gradlew`, `frontEnd/package.json`, `ai/requirements.txt`에 정의된 현재 명령을 따른다. 환경변수는 저장소의 `.env.*.example`을 기준으로 별도 주입하며 비밀값을 커밋하지 않는다.
