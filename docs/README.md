# Canonical Documentation Index

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Documentation authority, status and navigation
- Supersedes: docs/current/README.md and docs/current/DOCUMENT_INDEX.md
- Implementation Status: NOT_STARTED

## Authority

- `TARGET_CANONICAL`: 목표 제품과 아키텍처의 결정 원천이다. 구현 완료를 뜻하지 않는다.
- `CURRENT_BASELINE`: Phase 0에서 코드로 확인한 현재 상태다.
- `DRAFT_CONTRACT`: 확정된 불변조건과 Phase 2 결정 범위만 정의한다.
- `REFERENCE_ONLY`: 디자인 원본에만 사용한다.

Target과 Current가 충돌하면 제품 방향은 Target을 따르되, 실행 가능 여부 판단은 Current를 따른다.

## Canonical sets

- Product: [비전](product/PRODUCT_VISION.md), [범위](product/PRODUCT_SCOPE.md), [사용자 여정](product/USER_JOURNEY.md), [Workflow](product/PROJECT_WORKFLOW.md), [요구사항](product/FUNCTIONAL_REQUIREMENTS.md), [비기능](product/NON_FUNCTIONAL_REQUIREMENTS.md), [용어](product/TERMINOLOGY.md), [미결정](product/OPEN_DECISIONS.md)
- Domain drafts: [개요](domain/DOMAIN_OVERVIEW.md)
- Architecture: [시스템](architecture/SYSTEM_ARCHITECTURE.md), [Spring](architecture/SPRING_WAS_BOUNDARY.md), [AI Server](architecture/AI_SERVER_BOUNDARY.md), [데이터·스토리지](architecture/DATA_AND_STORAGE_ARCHITECTURE.md), [보안](architecture/SECURITY_ARCHITECTURE.md), [배포](architecture/DEPLOYMENT_ARCHITECTURE.md)
- Contracts: [개요](contracts/CONTRACT_OVERVIEW.md), [Public API 원칙](contracts/PUBLIC_API_PRINCIPLES.md), [Public API v2 JSON 계약](contracts/PUBLIC_API_V2_CONTRACT.md), [Internal AI 원칙](contracts/INTERNAL_AI_API_PRINCIPLES.md), [Internal Spring–AI API v1 계약](contracts/INTERNAL_AI_API_V1_CONTRACT.md), [Internal AI v1 fixtures](contracts/fixtures/internal-ai-v1/README.md), [상태·오류](contracts/STATUS_AND_ERROR_CONTRACT.md), [Provenance](contracts/PROVENANCE_CONTRACT.md)
- UI/UX: [정보구조](uiux/INFORMATION_ARCHITECTURE.md), [목표 route](uiux/TARGET_ROUTE_MAP.md), [화면 inventory](uiux/SCREEN_INVENTORY.md), [Workflow UX](uiux/WORKFLOW_UX.md)
- Quality: [테스트 전략](quality/TEST_STRATEGY.md), [Stable Core](quality/STABLE_CORE_REGRESSION.md), [품질 게이트](quality/QUALITY_GATES.md), [인수 기준](quality/ACCEPTANCE_CRITERIA.md)
- Migration: [Current→Target](migration/CURRENT_TO_TARGET_MAPPING.md), [Legacy 제거](migration/LEGACY_REMOVAL_PLAN.md), [Phase 순서](migration/IMPLEMENTATION_PHASES.md), [문서 제거 manifest](migration/DOCUMENT_REMOVAL_MANIFEST.md)
- Current implementation: [CURRENT_BASELINE](CURRENT_BASELINE.md)

## Governance and operations

- Governance: [Program status](governance/PROGRAM_STATUS.md), [Phase status](governance/PHASE_STATUS.md), [Phase 0 audit](governance/PHASE0_REPOSITORY_AUDIT.md), [Decision log](governance/DECISION_LOG.md), [Change impact](governance/CHANGE_IMPACT_LEDGER.md), [Verification evidence](governance/VERIFICATION_EVIDENCE.md)
- Stable Core operations: [Administration](operations/ADMINISTRATION_POLICY.md), [Session and reauthentication](operations/SESSION_AND_REAUTH_POLICY.md), [Audit](operations/AUDIT_POLICY.md), [Service policy](operations/SERVICE_POLICY.md)

## Non-canonical retained inputs

- `api/openapi.yaml`: CI와 backend test가 읽는 legacy implementation contract. 신규 `/api/v2` 구현 Phase에서 대체한다.
- `guide/*.docx`, `example/*.docx`: frontend 빌드가 배포 resource로 복사하는 legacy 사업계획서 자료. 해당 코드가 제거되기 전까지만 유지한다.
- `reference/design/`: 원본 디자인 자료. 목표 기능·도메인의 사실 원천으로 사용하지 않는다.

오래된 문서는 저장소 내부에 archive하지 않는다. 삭제 이력은 Git history가 보존한다.
