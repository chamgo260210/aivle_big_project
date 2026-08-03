# Public API v2 JSON Contract

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: ad94d6ba1fe92ebc98e81a69a399753f784c2997
- Scope: Implementation-ready public `/api/v2` workflow endpoint, JSON schema and transport contract
- Supersedes: Legacy workflow portions of `docs/api/openapi.yaml`; that machine-consumed file remains unchanged until implementation replacement
- Implementation Status: PARTIAL

P3 implementation note: only owner-scoped TaskRun GET/retry/cancel and the v2 envelope are implemented. P4-P10 workflow endpoints remain unimplemented.

이 문서는 Target public API 계약이며 현재 Controller나 OpenAPI 구현이 존재한다는 뜻이 아니다. Stable Core Project CRUD와 인증 계약은 기존 `/api/v1`을 유지할 수 있고, 신규 Workflow resource만 `/api/v2/projects/{projectId}` 아래에 둔다. Compatibility redirect나 legacy compatibility endpoint는 만들지 않는다.

## 1. Representation and ownership rules

- 기존 Bearer JWT 인증과 Project owner scope를 재사용한다. Project와 모든 nested resource에 cross-owner access가 발생하면 `RESOURCE_NOT_FOUND`/404로 응답한다.
- JSON field는 lowerCamelCase, enum은 UPPER_SNAKE_CASE, identifier는 의미·물리 형식을 노출하지 않는 opaque string이다.
- timestamp는 RFC 3339 UTC string, date-only 값은 `YYYY-MM-DD`다.
- 금액과 정밀 소수는 JSON number가 아니라 decimal string과 ISO 4217 currency code로 표현한다.
- Required field는 반드시 존재한다. Optional field는 omitted 가능하다. Nullable로 명시한 field만 `null`을 허용한다. Empty array는 “알고 있는 원소가 0개”, omitted array는 “이 표현에서 요청·계산하지 않음”이다.
- Storage object key, local path, presigned URL, AI Server identifier, TaskAttempt/worker/lease/provider identity, provider prompt/raw body와 secret을 public 표현에 포함하지 않는다.
- External legal citation의 `officialSourceUrl`은 provenance URL이며 Storage transport가 아니다.

### Public/Internal consistency registry

아래 표는 P2.6 validator가 Internal 계약과 exact set equality를 검사하는 public canonical registry다.

| Registry | Values |
|---|---|
| Legal Result | `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` |
| Analysis Type | `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL` |
| Report Decision | `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` |
| Provenance Category | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION` |
| Marketing Asset Type | `HEADLINE`, `BODY_COPY`, `CTA`, `CAMPAIGN_CONCEPT` |

| Invariant | Contract value |
|---|---|
| FINANCIAL_DETERMINISTIC_INPUT_OWNERSHIP | `SPRING_ONLY` |
| PERSONA_SYNTHETIC_DISCLOSURE | `REQUIRED` |
| MARKETING_PROBABILITY_CLAIMS | `FORBIDDEN` |
| MARKETING_STATISTICAL_AB_CLAIM | `FORBIDDEN` |

## 2. Headers and media types

| Header/media | Contract |
|---|---|
| `Authorization` | 기존 `Bearer <JWT>`; 실제 token 값은 문서·log·error에 기록하지 않음 |
| `X-Correlation-Id` | Client optional. ASCII letter/digit와 `-`, `_`, `.`, `:`만 허용하고 1~128 characters. 없거나 invalid이면 Spring이 새 opaque value를 생성하며 모든 success/error response header와 envelope에 반환 |
| `Idempotency-Key` | 모든 비멱등 command POST에 필수. 1~128 printable ASCII characters, leading/trailing whitespace 금지. Credential·PII·업무 내용을 포함하지 않음 |
| Idempotency scope | authenticated owner + Project + HTTP method + normalized path. Query ordering은 canonicalize하고 JSON은 semantic canonical form, multipart FILE은 verified byte digest와 canonical metadata를 사용 |
| Idempotency replay | 같은 key/scope와 같은 canonical request는 최초 response status/body를 재사용. 다른 canonical request면 `IDEMPOTENCY_CONFLICT` |
| Retention | 실제 보존 기간은 P3 운영 구현에서 확정하되 만료 전 replay/충돌 의미는 바뀌지 않음 |
| JSON | Request/response `application/json` |
| FILE upload | `multipart/form-data` 전용 |
| PDF download | `application/pdf`; Spring authenticated endpoint가 bytes를 전달 |

## 3. Common success envelopes

Single resource, synchronous creation and command status response:

```json
{
  "data": {},
  "meta": {
    "correlationId": "opaque-string"
  }
}
```

Cursor list:

```json
{
  "data": [],
  "page": {
    "nextCursor": null,
    "hasMore": false
  },
  "meta": {
    "correlationId": "opaque-string"
  }
}
```

Accepted asynchronous command:

```json
{
  "data": {
    "taskRun": {
      "id": "task-run-id",
      "taskType": "LEGAL_REVIEW",
      "subject": {
        "type": "LEGAL_REVIEW_RUN",
        "id": "domain-run-id"
      },
      "state": "QUEUED",
      "retryable": false,
      "cancelable": true,
      "correlationId": "opaque-string",
      "createdAt": "2026-08-01T13:00:00Z"
    },
    "resultResource": {
      "type": "LEGAL_REVIEW_RUN",
      "id": "domain-run-id"
    }
  },
  "meta": {
    "correlationId": "opaque-string"
  }
}
```

조회는 200, 동기 immutable Version/Decision 또는 root 생성은 201, AI-backed command 수락은 202를 사용한다. 202 response에는 `TaskRunPublicView`와 `resultResource`가 항상 존재한다.

## 4. Error envelope and mapping

```json
{
  "error": {
    "code": "CAPABILITY_NOT_AVAILABLE",
    "message": "Command cannot be executed in the current state.",
    "correlationId": "opaque-string",
    "taskRunId": null,
    "details": [
      {
        "field": null,
        "reason": "MISSING_CURRENT_LEGAL_REVIEW",
        "resourceType": "PROJECT",
        "resourceId": "project-id",
        "capability": "CAN_GENERATE_CONCEPTS",
        "gate": "LEGAL"
      }
    ]
  }
}
```

Primary error code는 하나다. `details`에는 권한 확인된 identifier와 안전한 structured context만 둔다. Field validation은 `field`와 stable `reason`을 사용하고 rejected raw value는 반환하지 않는다. `taskRunId`는 TaskRun 생성 뒤 발생한 오류에만 owner scope 확인 후 제공한다. TaskAttempt/provider/worker/lease identifier, provider raw body, prompt, secret, stack trace와 internal object key는 금지한다.

| Code | HTTP | Applies to | Representative detail reason |
|---|---:|---|---|
| `VALIDATION_ERROR` | 400 | 모든 command/query parameter와 body | `REQUIRED_FIELD`, `INVALID_ENUM`, `INVALID_FORMAT` |
| `RESOURCE_NOT_FOUND` | 404 | 존재하지 않거나 cross-owner인 Project/nested resource | `RESOURCE_NOT_FOUND` |
| `CONFLICT` | 409 | revision/lifecycle/current-pointer 충돌 | `REVISION_MISMATCH`, `WORKSPACE_ALREADY_EXISTS`, `TERMINAL_STATE` |
| `STALE_RESOURCE` | 409 | stale exact reference를 사용하는 command | `UPSTREAM_REFERENCE_STALE` |
| `CAPABILITY_NOT_AVAILABLE` | 409 | capability-gated command | `CAPABILITY_DISABLED`, `RETRY_NOT_ALLOWED` |
| `POLICY_BLOCKED` | 403 또는 temporary maintenance 503 | upload/AI/report/project policy | `FILE_UPLOAD_DISABLED`, `AI_EXECUTION_DISABLED`, `MAINTENANCE_MODE` |
| `UPSTREAM_NOT_READY` | 409 | required current/adopted upstream 부재 | `MISSING_CURRENT_REFERENCE`, `RESULT_NOT_ADOPTED` |
| `LEGAL_GATE_BLOCKED` | 409 | concept generation | `LEGAL_RESULT_NOT_PASSING` |
| `TASK_ALREADY_RUNNING` | 409 | 같은 subject/input active TaskRun 충돌 | `ACTIVE_TASK_EXISTS` |
| `IDEMPOTENCY_CONFLICT` | 409 | 같은 key와 다른 canonical request | `IDEMPOTENCY_REQUEST_MISMATCH` |
| `PAYLOAD_TOO_LARGE` | 413 | JSON/chunk, upload, bounded options | `REQUEST_LIMIT_EXCEEDED`, `FILE_TOO_LARGE` |
| `TASK_TIMEOUT` | HTTP request 자체 timeout이면 504; accepted TaskRun GET은 200 | gateway/request deadline 또는 TaskRun terminal `errorSummary` | `HTTP_REQUEST_DEADLINE_EXCEEDED`, `TASK_DEADLINE_EXCEEDED` |
| `AI_SERVICE_UNAVAILABLE` | Task 수락 전 503; accepted TaskRun GET은 200 | TaskRun 생성 전 dependency unavailable 또는 terminal `errorSummary` | `AI_SERVER_UNAVAILABLE` |
| `AI_RESULT_INVALID` | accepted TaskRun GET은 200; synchronous pre-accept boundary에서만 502 | received result validation/adoption failure summary | `RESULT_SCHEMA_INVALID`, `RESULT_DOMAIN_INVALID` |

202로 수락된 뒤 TaskRun이 `FAILED`, `TIMED_OUT`, `CANCELLED` 등 terminal state가 되어도 TaskRun GET은 200이다. 업무 실패는 `state`와 nullable `errorSummary.code`(`TASK_TIMEOUT`, `AI_SERVICE_UNAVAILABLE`, `AI_RESULT_INVALID`)로 표현하며 502/503/504로 resource 조회를 대체하지 않는다. TaskRun 생성 전 AI dependency unavailable로 command 자체를 수락할 수 없을 때만 503이고 `taskRunId`는 null이다. HTTP 504는 public request/gateway deadline 자체가 초과된 경우에만 사용한다.

## 5. Pagination, sorting and revisions

- History/list endpoint는 `cursor`, `limit`, `sort` query를 받는다. `limit` default는 20, maximum은 100이며 1 미만 또는 100 초과는 `VALIDATION_ERROR`다.
- Cursor는 opaque하고 client가 해석하거나 조합하지 않는다. Offset pagination은 기본 계약이 아니다.
- `sort`는 `CREATED_AT_DESC` 또는 `CREATED_AT_ASC`; default는 newest first인 `CREATED_AT_DESC`다. Stable tie-breaker는 opaque identifier다.
- Current singleton/resource 조회에는 pagination을 사용하지 않는다.
- Mutable root/current pointer를 바꾸는 command는 `expectedProjectRevision`, `expectedWorkspaceRevision`, `expectedCandidateRevision`처럼 해당 root의 non-negative revision을 요구한다. 불일치는 `CONFLICT`/409와 `REVISION_MISMATCH`다.
- ETag/If-Match는 초기 필수 계약이 아니며 request-body revision과 혼용하지 않는다.

## 6. Workflow summary

`GET /api/v2/projects/{projectId}/workflow` → 200 `WorkflowSummaryResponse`.

Response는 `projectId`, `projectStatus`, `workflowStage`, `projectRevision`, 14개 `capabilities`, `currentReferences`, `blockers`, `staleResourceSummary`, `activeTaskRuns`, `updatedAt`을 포함한다. Current references는 ideaVersion, legalReviewRun, shortlistDecision, conceptSelection, personaStudy, marketingWorkspaceVersion, finalReportVersion을 nullable `ResourceReference`로 표현한다. Capability는 Spring이 계산한 결과이며 저장된 business state가 아니다.

## 7. Endpoint catalog and capability binding

`Idem`의 `R`은 `Idempotency-Key` required, `N`은 command 자체가 idempotent하여 required가 아님을 뜻한다. 모든 path는 owner-scoped `{projectId}` resource다.

| Method | Path | Operation | Sync | Success | Idem | Capability |
|---|---|---|---|---:|---|---|
| GET | `/api/v2/projects/{projectId}/workflow` | workflow summary | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/idea-sources/text` | TEXT source 생성 | sync | 201 | R | `CAN_EDIT_IDEA` |
| POST | `/api/v2/projects/{projectId}/idea-sources/files` | FILE source upload | sync | 201 | R | `CAN_EDIT_IDEA` |
| GET | `/api/v2/projects/{projectId}/idea-sources` | source history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/idea-sources/{ideaSourceId}` | source 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/idea-interpretation-runs` | AI interpretation/normalization proposal | async | 202 | R | `CAN_INTERPRET_IDEA` |
| GET | `/api/v2/projects/{projectId}/idea-interpretation-runs` | interpretation history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/idea-interpretation-runs/{runId}` | interpretation run 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/idea-versions` | immutable IdeaVersion 생성 | sync | 201 | R | `CAN_EDIT_IDEA` |
| GET | `/api/v2/projects/{projectId}/idea-versions` | version history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/idea-versions/{ideaVersionId}` | version 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/legal-review-runs` | legal review 실행 | async | 202 | R | `CAN_RUN_LEGAL_REVIEW` |
| GET | `/api/v2/projects/{projectId}/legal-review-runs` | legal run history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/legal-review-runs/{legalReviewRunId}` | legal run 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/concept-generation-runs` | concept 생성 | async | 202 | R | `CAN_GENERATE_CONCEPTS` |
| GET | `/api/v2/projects/{projectId}/concept-generation-runs` | generation history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/concept-generation-runs/{runId}` | generation 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/concept-candidates` | candidate list | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/concept-candidates/{candidateId}` | candidate 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/concept-candidates/{candidateId}/versions` | concept versions | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/concept-candidates/{candidateId}/versions` | user-edited version | sync | 201 | R | `CAN_GENERATE_CONCEPTS` |
| POST | `/api/v2/projects/{projectId}/quick-assessment-runs` | single concept quick assessment | async | 202 | R | `CAN_RUN_QUICK_ASSESSMENT` |
| GET | `/api/v2/projects/{projectId}/quick-assessment-runs` | quick history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/quick-assessment-runs/{runId}` | quick run 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/shortlist-decisions` | shortlist decision | sync | 201 | R | `CAN_SET_SHORTLIST` |
| GET | `/api/v2/projects/{projectId}/shortlist-decisions` | shortlist history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/shortlist-decisions/{decisionId}` | shortlist 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/detailed-analysis-runs` | detailed analysis | async | 202 | R | `CAN_RUN_DETAILED_ANALYSIS` |
| GET | `/api/v2/projects/{projectId}/detailed-analysis-runs` | detailed history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/detailed-analysis-runs/{runId}` | detailed run 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/concept-selections` | final concept selection | sync | 201 | R | `CAN_SELECT_CONCEPT` |
| GET | `/api/v2/projects/{projectId}/concept-selections` | selection history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/concept-selections/{selectionId}` | selection 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/persona-studies` | empty study root 생성 | sync | 201 | R | `CAN_CREATE_PERSONA_STUDY` |
| GET | `/api/v2/projects/{projectId}/persona-studies` | study history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/persona-studies/{studyId}` | study 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/persona-studies/{studyId}/persona-cards` | card versions 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/persona-card-generation-runs` | Persona Card generation | async | 202 | R | `CAN_GENERATE_PERSONA_CARDS` |
| GET | `/api/v2/projects/{projectId}/persona-card-generation-runs` | generation history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/persona-card-generation-runs/{runId}` | generation run 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/persona-studies/{studyId}/persona-cards/{personaCardId}/versions` | user-edited card version | sync | 201 | R | `CAN_GENERATE_PERSONA_CARDS` |
| POST | `/api/v2/projects/{projectId}/persona-interview-runs` | independent interview | async | 202 | R | `CAN_RUN_PERSONA_INTERVIEW` |
| GET | `/api/v2/projects/{projectId}/persona-interview-runs` | interview history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/persona-interview-runs/{runId}` | interview 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/persona-studies/{studyId}/synthesis-runs` | AI synthesis | async | 202 | R | `CAN_RUN_PERSONA_INTERVIEW` |
| GET | `/api/v2/projects/{projectId}/persona-studies/{studyId}/syntheses` | synthesis history | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/marketing-workspace` | logical workspace 생성 | sync | 201 | R | `CAN_USE_MARKETING_WORKSPACE` |
| GET | `/api/v2/projects/{projectId}/marketing-workspace` | workspace 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/marketing-workspace/versions` | workspace version 생성 | sync | 201 | R | `CAN_USE_MARKETING_WORKSPACE` |
| POST | `/api/v2/projects/{projectId}/marketing-generation-runs` | asset generation | async | 202 | R | `CAN_USE_MARKETING_WORKSPACE` |
| GET | `/api/v2/projects/{projectId}/marketing-assets` | asset list | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/marketing-assets/{assetId}` | asset 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/marketing-assets/{assetId}/versions` | asset versions | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/marketing-assets/{assetId}/versions` | user-edited asset version | sync | 201 | R | `CAN_USE_MARKETING_WORKSPACE` |
| POST | `/api/v2/projects/{projectId}/marketing-comparison-runs` | Persona relative comparison | async | 202 | R | `CAN_USE_MARKETING_WORKSPACE` |
| GET | `/api/v2/projects/{projectId}/marketing-comparison-runs` | comparison history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/marketing-comparison-runs/{runId}` | comparison 조회 | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/final-report-generation-runs` | report generation | async | 202 | R | `CAN_GENERATE_FINAL_REPORT` |
| GET | `/api/v2/projects/{projectId}/final-report` | logical report 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/final-report/versions` | report version history | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/final-report/versions/{reportVersionId}` | report version 조회 | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/final-report/versions/{reportVersionId}/view` | persisted HTML view | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/final-report/versions/{reportVersionId}/exports/pdf` | PDF export 생성 | async/existing | 202/200 | R | `CAN_EXPORT_FINAL_REPORT` |
| GET | `/api/v2/projects/{projectId}/final-report/versions/{reportVersionId}/exports/pdf` | PDF download | sync | 200 | N | authenticated owner |
| GET | `/api/v2/projects/{projectId}/task-runs/{taskRunId}` | public task view | sync | 200 | N | authenticated owner |
| POST | `/api/v2/projects/{projectId}/task-runs/{taskRunId}/retry` | same TaskRun new Attempt | async | 202 | R | computed retry capability |
| POST | `/api/v2/projects/{projectId}/task-runs/{taskRunId}/cancel` | idempotent cancel | sync | 200 | N | computed cancelability |

History/list GET은 cursor pagination을 적용한다. Nested current singleton GET은 적용하지 않는다. Query endpoint principal errors는 validation, not-found/cross-owner이며 command endpoint는 위 capability와 함께 conflict/stale/policy/upstream/task/idempotency 오류 subset을 적용한다.

## 8. Command and resource details

### 8.1 Idea

TEXT source command body:

| Field | Presence | Contract |
|---|---|---|
| `text` | required, non-null | non-blank bounded string; 질문 응답 UI도 이 field로 capture |
| `sourceLabel` | optional, non-null if present | user-facing bounded label |

FILE source는 `file` part가 required이고 `metadata` JSON part는 optional이다. 초기 media type은 DOCX와 `text/plain`만 허용한다. Spring이 size, filename normalization, content type/magic bytes, checksum, malware policy를 검증하고 저장·추출한다. Response는 `IdeaSourceView`와 `IdeaSourceExtractionView`만 포함하며 object key, local path, Storage/presigned URL을 포함하지 않는다.

Idea Interpretation command body:

```json
{
  "ideaSourceExtractionIds": ["extraction-id"],
  "interpretationOptions": {},
  "expectedProjectRevision": 12
}
```

Extraction id는 같은 Project의 exact `CURRENT` validated record 하나 이상이고 중복할 수 없다. `interpretationOptions`는 optional bounded allowlist이며 unknown option은 `VALIDATION_ERROR`다. FILE bytes, Storage URL/object key/presigned URL/local path는 받지 않는다. Spring이 verified extracted text/chunks만 internal AI request로 전달한다.

Principal command errors는 `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `STALE_RESOURCE`, `CAPABILITY_NOT_AVAILABLE`, `POLICY_BLOCKED`, `UPSTREAM_NOT_READY`, `TASK_ALREADY_RUNNING`, `IDEMPOTENCY_CONFLICT`, `PAYLOAD_TOO_LARGE`, `AI_SERVICE_UNAVAILABLE`다. 202 accepted 이후 실패는 TaskRun GET 200의 terminal state와 `errorSummary`로 표현한다.

IdeaInterpretationRun은 TaskRun과 1:1로 생성되고 AI proposal만 만든다. Adopted result 전에는 readiness와 normalized content가 nullable이다. Result는 original source summary, normalizedDescription, structured facts/assumptions/constraints/openQuestions, readiness, warnings, evidenceNeeds와 provenance를 구분한다. `UNDER_SPECIFIED`는 필요한 정보/open question, `APPROPRIATE`는 적정 정규화, `OVER_SPECIFIED`는 사용자 제약을 보존하면서 과도한 세부를 정리한다. AI는 불명확한 항목을 fact로 승격하거나 사용자 constraint를 임의 삭제하지 않는다.

IdeaVersion create는 `creationMode`에 따라 분리된다. 공통 required field는 `ideaSourceIds`, `ideaSourceExtractionIds`, `expectedProjectRevision`, `confirmation`이다. `confirmation`은 authenticated user가 수행하며 client actor id를 받지 않는다.

```json
{
  "accepted": true,
  "editRationale": "optional bounded text"
}
```

`accepted`는 항상 true여야 한다. Proposal 거절은 Version 생성 없이 종료한다. `editRationale`은 optional non-null bounded text다.

| Creation mode | Required fields | Forbidden fields | Server-derived provenance |
|---|---|---|---|
| `USER_AUTHORED` | `normalizedDescription`, structured `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness` | `interpretationRunId`, `adoptedInterpretationResultId`, `createdBy`, `confirmedByUser`, actor id | `createdBy=USER`, authenticated confirmation actor/time |
| `AI_ASSISTED` | `interpretationRunId`, `adoptedInterpretationResultId`, final `normalizedDescription`, structured `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness` | `createdBy`, `confirmedByUser`, actor id | `createdBy=AI_ASSISTED`, exact proposal/result, authenticated confirmation과 proposal 대비 변경 |

AI_ASSISTED는 exact `CURRENT` IdeaInterpretationRun의 validated/adopted result만 허용한다. Proposal을 그대로 또는 수정해 확정할 수 있지만 final snapshot 전체를 전송하고 변경 provenance를 보존한다. USER_AUTHORED는 InterpretationRun 없이 가능하다. IdeaVersion은 항상 immutable이며 새 확정은 새 Version과 downstream stale propagation을 만든다.

Structured idea item 공통 shape:

| Field | Presence | Contract |
|---|---|---|
| `key` | required, non-null | response/request snapshot 안에서 안정적인 bounded local key |
| `text` | required, non-null | bounded statement |
| `provenanceCategory` | required | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION` 중 해당 값 |
| `sourceReferences` | required, non-null array | exact public-safe ResourceReference; 근거 없음은 empty array |
| `confidence` | optional, non-null | bounded qualitative enum direction; fact 확정 의미가 아님 |
| `uncertainty` | optional, non-null | bounded explanation |
| `verificationNeeded` | required | boolean |

P2.5 internal contract와 P2.6 fixtures는 이 `IdeaStatementItem` 의미를 동일하게 재사용한다.

### 8.2 Legal review

LegalReview command body는 required non-null `ideaVersionId`와 `expectedProjectRevision`만 받는다. Exact current IdeaVersion만 허용하며 LegalReviewRun과 TaskRun을 1:1로 생성한다.

`LegalReviewRunView`는 `execution` TaskRun projection, `validity`, nullable `legalResult`, `findings`, `sourceReferences`, `sourceCoverage`, `warnings`, nullable public-safe `adoptedResult`를 분리한다. `legalResult`는 adopted domain validation 전까지 null이다.

Legal source는 sourceChannel, lawIdentifier, lawName, article, observedAt, currentness, authoritative, degraded와 optional `officialSourceUrl`을 제공한다. Official URL은 external provenance이며 Storage URL이 아니다. Secret과 raw MCP/provider response는 금지한다.

### 8.3 Concept generation and versioning

Generation body:

| Field | Presence | Contract |
|---|---|---|
| `ideaVersionId` | required | exact current IdeaVersion |
| `legalReviewRunId` | required | same IdeaVersion의 current adopted `PASS`/`PASS_WITH_CONDITIONS` run |
| `candidateCount` | optional | integer 1~10; omitted default 3 |
| `generationOptions` | optional | bounded string-keyed public options; unknown key는 validation error |
| `expectedProjectRevision` | required | Project concurrency guard |

ConceptVersion user-edit body는 `baseConceptVersionId`, title, targetProblem, targetUserContext, valueProposition, solutionOutline, differentiators, constraints, assumptions, evidenceNeeds와 `expectedCandidateRevision`을 받는다. Text fields는 required non-null, collection fields는 required non-null arrays다. 생성 provenance는 항상 `USER_EDITED`; client가 AI provenance를 지정할 수 없다.

### 8.4 Quick assessment and shortlist

Quick command 하나는 required `conceptVersionId` 하나와 `expectedProjectRevision`을 받는다. Batch ids는 허용하지 않는다. Frontend는 후보별 command를 제출하여 failure, cancel과 retry를 분리한다. 결과는 AI proposal이고 shortlist decision이 아니다.

Shortlist body:

| Field | Presence | Contract |
|---|---|---|
| `selectedConceptVersionIds` | required | 하나 이상, 중복 금지 |
| `consideredConceptVersionIds` | required | decision snapshot의 전체 검토 집합; selected를 포함 |
| `rejectedConceptVersionIds` | optional | present하면 selected와 disjoint |
| `reviewedQuickAssessmentRunIds` | required | 검토한 exact run ids; empty 허용 여부는 capability가 판단 |
| `rationale` | required | 사용자 rationale |
| `expectedProjectRevision` | required | Project concurrency guard |

Decision은 immutable history다. AI ranking은 selected set을 자동 결정하지 않는다.

### 8.5 Detailed analysis

Common body는 `conceptVersionId`, `shortlistDecisionId`, `analysisType`, `analysisSpecificInput`, `expectedProjectRevision`을 required non-null로 받는다. `analysisType`은 `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL`이며 input/output discriminator다.

| Input schema | Required fields | Optional fields | Nullable fields |
|---|---|---|---|
| `MarketAnalysisInput` | `marketDefinition`, `geographies`, `customerSegments`, `questions` | `timeHorizon`, `knownEvidenceRefs` | none |
| `BusinessModelAnalysisInput` | `valueExchange`, `revenueModelHypotheses`, `costDriverHypotheses`, `channelHypotheses` | `partnerDependencies`, `knownEvidenceRefs` | none |
| `TechnicalOperationAnalysisInput` | `requiredCapabilities`, `systemConstraints`, `operationalProcesses`, `externalDependencies` | `complianceConstraints`, `knownEvidenceRefs` | none |
| `FinancialAnalysisInput` | `currencyCode`, `horizonMonths`, `deterministicInputs`, `calculationRuleVersion`, `scenarioInputs` | `knownEvidenceRefs` | none |

각 string/array는 bounded되고 empty 허용 여부는 schema별로 명시한다. `geographies`, `customerSegments`, `questions`, business hypotheses, technical capability/process/dependency, financial deterministic/scenario inputs는 하나 이상이어야 한다.

`DetailedAnalysisRunView.result`는 analysisType별 schema를 사용한다.

| Output schema | Required fields | Optional fields | Nullable fields |
|---|---|---|---|
| `MarketAnalysisResult` | `findings`, `evidence`, `assumptions`, `uncertainties`, `evidenceNeeds` | `segmentComparisons` | none |
| `BusinessModelAnalysisResult` | `modelAssessment`, `risks`, `dependencies`, `assumptions`, `evidenceNeeds` | `alternativeModels` | none |
| `TechnicalOperationAnalysisResult` | `feasibilityAssessment`, `capabilityGaps`, `operationalRisks`, `dependencies`, `evidenceNeeds` | `optionComparisons` | none |
| `FinancialAnalysisResult` | `deterministicInputs`, `calculationRuleVersion`, `deterministicResults`, `aiExplanation`, `assumptions`, `evidenceNeeds` | `scenarioComparisons` | none |

Financial decimal values는 decimal string이다. `deterministicInputs`와 `deterministicResults` 원소는 `metricCode`, `value`, optional `currencyCode` 또는 `unit`, optional `period`를 사용한다. `aiExplanation`은 `summary`, `drivers`, `risks`를 required arrays로 구분하며 deterministic result를 바꾸지 않는다.

### 8.6 Concept selection

Body는 required `selectedConceptVersionId`, `shortlistDecisionId`, `reviewedDetailedAnalysisRunIds`, `alternativesConsidered`, `rationale`, `expectedProjectRevision`을 받는다. Selection은 USER provenance인 immutable history이며 AI recommendation은 별도 evidence reference일 뿐 자동 selection이 아니다. 새 selection은 기존 Persona/Marketing/Report를 stale 처리한다.

### 8.7 Persona

PersonaStudy create는 AI execution을 시작하지 않는 동기 201 command다. Body는 exact current `conceptSelectionId`, selected `conceptVersionId`, `expectedProjectRevision`이다. 생성된 Study는 빈 Card collection을 가질 수 있다.

PersonaCardGenerationRun create body:

| Field | Presence | Contract |
|---|---|---|
| `personaStudyId` | required | current non-stale PersonaStudy |
| `conceptSelectionId` | required | Study가 고정한 exact current ConceptSelection |
| `conceptVersionId` | required | selection의 exact selected ConceptVersion |
| `personaCount` | required | integer 1~10 |
| `generationOptions` | optional, non-null | bounded allowlisted options; unknown option은 validation error |
| `expectedStudyRevision` | required | Study concurrency guard |

Command는 PersonaCardGenerationRun과 1:1 TaskRun을 만들고 202를 반환한다. 성공한 adopted result는 하나 이상의 PersonaCard logical identity와 각 identity의 initial PersonaCardVersion을 만든다. Retry는 같은 TaskRun의 새 TaskAttempt이고 user rerun은 새 GenerationRun/TaskRun이다.

PersonaCardVersion create body는 `basePersonaCardId`, exact `baseVersion`, `roleAndContext`, `problemAndNeeds`, `behaviorAndDecision`, `editRationale`, `expectedStudyRevision`을 required non-null로 받는다. Path `personaCardId`는 `basePersonaCardId`와 같아야 한다. 새 version은 `USER_EDITED` provenance와 synthetic disclosure를 유지하고 기존 version을 수정하지 않는다.

PersonaCard view는 logical identity/current version reference를 제공하고 PersonaCardVersion view는 required `roleAndContext`, `problemAndNeeds`, `behaviorAndDecision`, `syntheticDisclosure`, `provenance`, `validity`를 분리한다. 실제 고객 조사, 구매확률과 market share를 표현하지 않는다.

PersonaInterview body는 required `personaStudyId`, exact `personaCardId`, exact `personaCardVersionId`, `questionSet`, optional non-null `interviewOptions`를 받는다. Version은 Card의 current confirmed non-stale version이어야 한다. 다른 Persona context를 전달하지 않고 Persona별 독립 TaskRun을 만든다. Synthesis body는 같은 Study의 exact `interviewRunIds` 두 개 이상과 optional synthesis options를 받으며 원본 Interview를 수정하지 않는다.

### 8.8 Marketing

MarketingWorkspace create는 최초 생성 시 201이다. 동일 `Idempotency-Key` replay는 최초 response를 반환한다. 이미 logical workspace가 있는데 다른 key로 다시 생성하면 `CONFLICT`/409 `WORKSPACE_ALREADY_EXISTS`; 암묵적 GET으로 바꾸지 않는다.

WorkspaceVersion body는 required `conceptSelectionId`, `conceptVersionId`, `personaStudyId`, `evidenceReferences`, `expectedWorkspaceRevision`을 받는다. Evidence reference는 exact interview/synthesis/card version을 가리킨다.

Marketing generation body는 required `marketingWorkspaceVersionId`, `assetType`, `targetPersonaCardIds`, `generationInput`을 받는다. Comparison body는 `marketingWorkspaceVersionId`, distinct `marketingAssetVersionIds` 두 개 이상과 PersonaCard 또는 synthesis `evidenceReferences`를 받는다. 결과는 Persona-based relative assessment이며 conversion probability, market share, winner probability와 실제 A/B claim을 금지한다.

User-edited asset version body는 required `baseAssetVersionId`, `content`, `expectedAssetRevision`과 optional Spring-owned `taskArtifactId`를 받는다. `content`는 text representation이고 둘 다 없을 수 없다. Storage key, URL, local/AI path는 받거나 반환하지 않는다.

### 8.9 Final report and export

Generation body는 required exact `upstreamReferences`, `reportDecision`, `userRationale`, `expectedProjectRevision`을 받는다. `reportDecision`은 `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP`이다. Upstream set은 Idea, accepted Legal, ConceptSelection, Persona evidence, Marketing evidence와 포함할 adopted Run results를 typed `ResourceReference`로 고정한다.

FinalReportVersion view는 facts, legalSources, aiProposals, assumptions, researchNeeds, userDecisions, reportDecision, upstreamReferences, validity와 createdAt을 분리한다. HTML view는 persisted structured snapshot의 `text/html; charset=UTF-8` 표현이며 browser runtime 조립은 source of truth가 아니다. 초기 Markdown export endpoint는 없다.

PDF export POST 결정:

- AVAILABLE export가 없으면 TaskRun을 생성해 202를 반환한다.
- 동일 key replay는 최초 status/body를 재사용한다.
- 다른 valid key로 요청했지만 같은 immutable ReportVersion의 AVAILABLE PDF가 이미 있으면 새 TaskRun 없이 200 `FinalReportExportView`를 반환한다.
- 303 redirect는 사용하지 않는다.
- GET download는 Spring 인증 endpoint가 `application/pdf` bytes를 반환한다. Storage key/URL, local path나 AI presigned URL을 노출하지 않는다.

### 8.10 TaskRun controls

Public TaskRun view는 id, taskType, subject, state, retryable, cancelable, timestamps, nullable errorSummary/resultResource와 correlationId를 제공한다. TaskAttempt, worker/lease, provider, prompt/raw body, credential과 object key는 금지한다.

TaskRun GET은 terminal `FAILED`, `TIMED_OUT`, `CANCELLED`에서도 200이다. `errorSummary`는 public-safe `code`, `message`, `retryable`을 제공하며 `TASK_TIMEOUT`, `AI_SERVICE_UNAVAILABLE`, `AI_RESULT_INVALID`을 포함할 수 있다. 이 resource representation을 HTTP 502/503/504로 바꾸지 않는다.

Retry POST는 `Idempotency-Key`가 required이고 같은 TaskRun에 새 TaskAttempt를 추가한 뒤 202를 반환한다. `retryable=false`이면 `CAPABILITY_NOT_AVAILABLE`/409 `RETRY_NOT_ALLOWED`다. Domain rerun은 원래 Domain command endpoint에 새 key로 요청해 새 Domain Run과 새 TaskRun을 만든다.

Cancel POST는 idempotent하며 200 current TaskRun view를 반환한다. 이미 terminal이면 상태를 변경하지 않고 같은 current terminal view를 반환한다. Invalid owner/resource는 404다.

## 9. Capability–command matrix

공통 principal errors인 `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `POLICY_BLOCKED`, `IDEMPOTENCY_CONFLICT`는 모든 관련 command에 적용된다. 아래 Error subset은 추가 gate/state 오류다.

| Capability | Command endpoints | Required exact current references | Mode/status | Principal additional errors |
|---|---|---|---|---|
| `CAN_EDIT_IDEA` | idea source와 user-authored/final IdeaVersion confirmation POST | active owner-scoped Project와 exact source/extraction context | sync 201 | `CONFLICT`, `STALE_RESOURCE`, `PAYLOAD_TOO_LARGE` |
| `CAN_INTERPRET_IDEA` | idea-interpretation-runs POST | 하나 이상의 current validated IdeaSourceExtraction | async 202 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `PAYLOAD_TOO_LARGE`, `AI_SERVICE_UNAVAILABLE` |
| `CAN_RUN_LEGAL_REVIEW` | legal-review-runs POST | confirmed current IdeaVersion | async 202 | `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `AI_SERVICE_UNAVAILABLE` |
| `CAN_GENERATE_CONCEPTS` | concept-generation-runs와 ConceptVersion user-edit POST | current IdeaVersion + passing current LegalReviewRun; edit은 exact candidate/base version | async 202 또는 sync 201 | `LEGAL_GATE_BLOCKED`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `CONFLICT` |
| `CAN_RUN_QUICK_ASSESSMENT` | quick-assessment-runs POST | exact current ConceptVersion | async 202 | `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `AI_RESULT_INVALID` |
| `CAN_SET_SHORTLIST` | shortlist-decisions POST | exact current ConceptVersion set + reviewed Quick runs | sync 201 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `CONFLICT` |
| `CAN_RUN_DETAILED_ANALYSIS` | detailed-analysis-runs POST | current ShortlistDecision + included ConceptVersion | async 202 | `CAPABILITY_NOT_AVAILABLE`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING` |
| `CAN_SELECT_CONCEPT` | concept-selections POST | current ShortlistDecision + reviewed Detailed runs | sync 201 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `CONFLICT` |
| `CAN_CREATE_PERSONA_STUDY` | persona-studies POST | current ConceptSelection + selected ConceptVersion | sync 201 | `STALE_RESOURCE`, `CONFLICT` |
| `CAN_GENERATE_PERSONA_CARDS` | persona-card-generation-runs와 PersonaCardVersion POST | current non-stale Study/Selection/selected ConceptVersion; edit은 exact Card/base Version | async 202 또는 sync 201 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `CONFLICT` |
| `CAN_RUN_PERSONA_INTERVIEW` | persona-interview-runs, synthesis-runs POST | current Study + exact current PersonaCardVersion; synthesis는 adopted Interview set | async 202 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING` |
| `CAN_USE_MARKETING_WORKSPACE` | workspace/version/asset/generation/comparison POST | current ConceptSelection, PersonaStudy와 requested evidence/asset versions | sync 201 또는 async 202 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING`, `CONFLICT` |
| `CAN_GENERATE_FINAL_REPORT` | final-report-generation-runs POST | exact current upstream set + user decisions | async 202 | `UPSTREAM_NOT_READY`, `STALE_RESOURCE`, `TASK_ALREADY_RUNNING` |
| `CAN_EXPORT_FINAL_REPORT` | PDF export POST | persisted available FinalReportVersion | async 202 또는 existing 200 | `UPSTREAM_NOT_READY`, `TASK_ALREADY_RUNNING`, `AI_SERVICE_UNAVAILABLE` |

Task retry/cancel capability는 TaskRun의 `retryable`/`cancelable`, lifecycle, policy와 owner scope로 별도 계산하며 Workflow Stage를 사용하지 않는다.

## 10. Resource schema registry

아래 field 목록은 public representation만 정의한다. Domain 내부 field 전체를 자동 노출하지 않는다. `R`은 required/non-null, `O`는 optional/non-null if present, `N`은 required nullable을 뜻한다. 명시되지 않은 field는 허용하지 않는 방향이며 forward-compatible extension은 P2.6 fixture/schema consistency verification에서 검증한다.

| Schema | Required / non-null | Optional / non-null if present | Required nullable |
|---|---|---|---|
| `WorkflowSummaryResponse` | `projectId`, `projectStatus`, `workflowStage`, `projectRevision`, `capabilities`, `currentReferences`, `blockers`, `staleResourceSummary`, `activeTaskRuns`, `updatedAt` | none | current reference 개별 값 |
| `CapabilityView` | `name`, `enabled`, `blockers` | none | none |
| `ResourceReference` | `type`, `id` | `version` | none |
| `TaskRunPublicView` | `id`, `taskType`, `subject`, `state`, `retryable`, `cancelable`, `correlationId`, `createdAt` | none | `startedAt`, `finishedAt`, `errorSummary`, `resultResource` |
| `IdeaSourceView` | `id`, `sourceType`, `lifecycle`, `createdAt` | FILE이면 sanitized `fileName`, `mediaType`, `size` | `sourceLabel` |
| `IdeaSourceExtractionView` | `id`, `ideaSourceId`, `state`, `extractionVersion`, `createdAt` | `warnings` | `finishedAt`, `failureSummary` |
| `IdeaInterpretationRunCreateRequest` | `ideaSourceExtractionIds`, `expectedProjectRevision` | `interpretationOptions` | none |
| `IdeaInterpretationResultView` | `originalSourceSummary`, `normalizedDescription`, `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness`, `warnings`, `evidenceNeeds`, `provenance` | none | none |
| `IdeaInterpretationRunView` | `id`, `sourceExtractions`, `execution`, `validity`, `facts`, `assumptions`, `constraints`, `openQuestions`, `warnings`, `evidenceNeeds`, `provenance`, `createdAt` | none | `readiness`, `normalizedDescription`, `adoptedResult` |
| `IdeaStatementItem` | `key`, `text`, `provenanceCategory`, `sourceReferences`, `verificationNeeded` | `confidence`, `uncertainty` | none |
| `IdeaVersionConfirmation` | `accepted` | `editRationale` | none |
| `IdeaVersionCreateRequest` | `creationMode`, `ideaSourceIds`, `ideaSourceExtractionIds`, `normalizedDescription`, `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness`, `expectedProjectRevision`, `confirmation` | mode-dependent `interpretationRunId`, `adoptedInterpretationResultId` | none |
| `IdeaVersionView` | `id`, `version`, `creationMode`, `sourceReferences`, `sourceExtractionReferences`, `normalizedDescription`, `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness`, `createdBy`, `confirmation`, `validity`, `createdAt` | `proposalChanges` | `interpretationRun`, `adoptedInterpretationResult` |
| `LegalReviewRunView` | `id`, `ideaVersion`, `execution`, `validity`, `findings`, `sourceReferences`, `sourceCoverage`, `warnings`, `createdAt` | none | `legalResult`, `adoptedResult` |
| `LegalFindingView` | `id`, `findingType`, `severity`, `claim`, `affectedIdeaElement`, `requiredAction`, `provenanceCategory` | `sourceReferenceIds` | none |
| `LegalSourceReferenceView` | `id`, `sourceChannel`, `lawIdentifier`, `lawName`, `article`, `observedAt`, `currentness`, `authoritative`, `degraded` | `officialSourceUrl` | none |
| `ConceptGenerationRunView` | `id`, `ideaVersion`, `legalReviewRun`, `execution`, `validity`, `candidateReferences`, `createdAt` | `warnings` | `adoptedResult` |
| `ConceptCandidateView` | `id`, `generationRun`, `lifecycle`, `validity`, `createdAt` | none | `currentVersion` |
| `ConceptVersionView` | `id`, `candidateId`, `version`, `title`, `targetProblem`, `targetUserContext`, `valueProposition`, `solutionOutline`, `differentiators`, `constraints`, `assumptions`, `evidenceNeeds`, `provenanceCategory`, `validity`, `createdAt` | none | none |
| `QuickAssessmentRunView` | `id`, `conceptVersion`, `execution`, `validity`, `dimensions`, `evidence`, `uncertainties`, `createdAt` | `warnings` | `adoptedResult` |
| `ShortlistDecisionView` | `id`, `selectedConceptVersions`, `consideredConceptVersions`, `reviewedQuickAssessmentRuns`, `rationale`, `decisionActor`, `validity`, `createdAt` | `rejectedConceptVersions` | none |
| `DetailedAnalysisRunView` | `id`, `analysisType`, `conceptVersion`, `shortlistDecision`, `execution`, `validity`, `inputSnapshot`, `createdAt` | `warnings` | `result`, `adoptedResult` |
| `ConceptSelectionView` | `id`, `selectedConceptVersion`, `shortlistDecision`, `reviewedDetailedAnalysisRuns`, `alternativesConsidered`, `rationale`, `decisionActor`, `validity`, `createdAt` | `aiRecommendationReferences` | none |
| `PersonaStudyView` | `id`, `conceptSelection`, `conceptVersion`, `lifecycle`, `validity`, `personaCardReferences`, `createdAt` | none | none |
| `PersonaCardGenerationRunCreateRequest` | `personaStudyId`, `conceptSelectionId`, `conceptVersionId`, `personaCount`, `expectedStudyRevision` | `generationOptions` | none |
| `PersonaCardGenerationRunView` | `id`, `personaStudy`, `conceptSelection`, `conceptVersion`, `execution`, `validity`, `personaCardReferences`, `createdAt` | `warnings` | `adoptedResult` |
| `PersonaCardView` | `id`, `studyId`, `lifecycle`, `validity`, `createdAt` | none | `currentVersion` |
| `PersonaCardVersionCreateRequest` | `basePersonaCardId`, `baseVersion`, `roleAndContext`, `problemAndNeeds`, `behaviorAndDecision`, `editRationale`, `expectedStudyRevision` | none | none |
| `PersonaCardVersionView` | `id`, `personaCardId`, `version`, `roleAndContext`, `problemAndNeeds`, `behaviorAndDecision`, `syntheticDisclosure`, `provenance`, `validity`, `createdAt` | none | none |
| `PersonaInterviewView` | `id`, `personaStudy`, `personaCard`, `execution`, `validity`, `questions`, `answers`, `interpretations`, `evidenceNeeds`, `syntheticDisclosure`, `createdAt` | `warnings` | `adoptedResult` |
| `InterviewSynthesisView` | `id`, `studyId`, `sourceInterviewReferences`, `commonResponses`, `conflictingResponses`, `unresolvedQuestions`, `researchRecommendations`, `validity`, `createdAt` | `execution` for AI-backed synthesis | `adoptedResult` for AI-backed synthesis |
| `MarketingWorkspaceView` | `id`, `lifecycle`, `revision`, `validity`, `createdAt`, `updatedAt` | none | `currentVersion` |
| `MarketingWorkspaceVersionView` | `id`, `workspaceId`, `version`, `conceptSelection`, `conceptVersion`, `personaStudy`, `evidenceReferences`, `validity`, `createdAt` | none | none |
| `MarketingAssetView` | `id`, `workspaceId`, `assetType`, `lifecycle`, `validity`, `createdAt`, `updatedAt` | none | `currentVersion` |
| `MarketingAssetVersionView` | `id`, `assetId`, `version`, `targetPersonaReferences`, `provenance`, `validity`, `createdAt` | public-safe `artifact` summary | `content` when artifact-only |
| `MarketingComparisonRunView` | `id`, `workspaceVersion`, `assetVersions`, `personaEvidence`, `execution`, `validity`, `relativeAssessments`, `caveats`, `createdAt` | `aiRecommendation` | `adoptedResult` |
| `FinalReportView` | `id`, `projectId`, `status`, `createdAt`, `updatedAt` | none | `currentVersion` |
| `FinalReportVersionView` | `id`, `reportId`, `version`, `facts`, `legalSources`, `aiProposals`, `assumptions`, `researchNeeds`, `userDecisions`, `reportDecision`, `upstreamReferences`, `validity`, `createdAt` | `exports` | none |
| `FinalReportExportView` | `reportVersion`, `format`, `state`, `createdAt` | `size`, `checksum` | `finishedAt` |
| `ErrorResponse` | `error.code`, `error.message`, `error.correlationId`, `error.details` | none | `error.taskRunId` |
| `CursorPage` | `hasMore` | none | `nextCursor` |

All list responses wrap the corresponding view array with `CursorPage`. `execution`은 `TaskRunPublicView`의 safe projection이고 TaskAttempt collection을 포함하지 않는다. `adoptedResult`는 id/schema version/validation summary만 포함하는 public-safe reference이며 raw provider body가 아니다.

## 11. Security and privacy

- 모든 path와 nested reference가 같은 owner-scoped Project에 속하는지 Spring이 검증한다. 다른 owner의 identifier는 존재 여부를 숨기고 404다.
- Filename은 sanitized display name만 필요한 범위에서 반환한다. 사용자 text와 file-derived content는 해당 resource view에 필요한 최소 범위만 반환한다.
- AI-generated content는 `AI_PROPOSAL`, `AI_ASSISTED` 또는 `syntheticDisclosure`로 표시한다. Persona/Interview는 실제 고객 조사나 전문가 판단으로 표현하지 않는다.
- Legal citation URL만 external provenance로 허용한다. Storage URL, object key, local path와 presigned URL은 request/response 양쪽에서 금지한다.
- Provider prompt/raw output, model/provider metadata, AI Server identity, credential, TaskAttempt와 worker/lease identity는 public contract 밖이다.
- Audit actor identity는 내부 audit contract에 남기고 public decision actor는 권한에 맞는 opaque id/display summary만 제공한다.

## 12. P2.5 and P2.6 handoff

P2.5는 `IDEA_INTERPRETATION` Task type을 포함해 이 public command를 Spring–AI bounded inline JSON request/result contract에 매핑한다. Spring은 exact extracted text/chunks, source-safe labels, readiness/normalization options, contract version, correlation/execution identifier, deadline과 canonical input hash만 전달한다.

AI Server에 RDB identifier lookup, Object Storage URL/object key/presigned URL/local path, FILE bytes, JWT, user credential 또는 Spring DB entity serialization을 전달하지 않는다. AI Server는 Spring RDB/Object Storage를 조회하지 않는다. Public identifier나 owner context를 AI Server가 resolve하게 하지 않는다.

P2.6은 schema examples/fixtures, canonical request hashing, `IdeaStatementItem`, enum/status/error drift, endpoint-capability matrix와 nullable/omitted behavior를 자동 검증한다. OD-008 provider/model/SDK/library 선택은 provider-dependent implementation slice 전 decision gate로 유지한다.
