# Internal AI v1 Contract Fixtures

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2.6
- Introduced In Commit: P2.6 commit pending
- Scope: Executable fixtures and consistency checks for the internal Spring–AI v1 contract
- Supersedes: None
- Implementation Status: NOT_STARTED

이 디렉터리는 [Internal Spring–AI API v1 Contract](../../INTERNAL_AI_API_V1_CONTRACT.md)와 [Public API v2 Contract](../../PUBLIC_API_V2_CONTRACT.md)의 계약 검증 자료다. Production DTO, Pydantic model, route 또는 JSON Schema 구현이 아니다.

## Structure

- `manifest.json`: 모든 JSON fixture의 category, expected result, `expectedValidatorRule`, `coveredSchemas`, 단일 `primaryInvariant`와 검증 근거
- `validate_fixtures.py`: Python 표준 라이브러리만 사용하는 exact field-table/envelope/domain validator
- `common/`: canonical hash, TextContent와 33개 error reason fixture
- `tasks/`: 11개 task request/response, legal degraded와 detailed discriminator fixture
- `negative/`: 하나의 primary invariant만 위반하는 negative fixture

## Run

Windows와 Linux/CI 모두 같은 Python 명령을 사용한다.

```text
python docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
python -m py_compile docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
```

Validator는 network, DB, Object Storage, Spring/FastAPI runtime 또는 외부 package를 사용하지 않는다. 오류 출력은 fixture path, stable rule, expected/actual만 포함하며 fixture payload, 사용자 text 또는 secret을 출력하지 않는다.

## Fixture rules

모든 JSON은 UTF-8 without BOM, LF, comment 없는 단일 contract object다. Test metadata는 manifest에만 둔다. Positive fixture에는 credential/JWT, provider/model/SDK identity, Storage URL/key, presigned URL, local path와 FILE bytes가 없다. Manifest path는 fixture root 내부의 normalized relative path여야 하며 absolute path, `..`와 symlink escape를 금지한다.

Positive는 exact schema와 domain validator를 통과해야 한다. Negative도 동일한 `validate_fixture` 경로를 실행하고 정확히 `expectedValidatorRule`로 실패해야 하며, 다른 rule이 먼저 발생하거나 validation이 성공하면 전체 검증이 실패한다. Validator는 실제 object의 known field와 존재하는 named nested instance만 순회해 observed schema coverage를 수집하며, 각 `coveredSchemas` 선언은 observed set과 정확히 같아야 한다. Positive와 negative instance coverage는 각각 65개 named field-table schema 전체를 충족한다. Negative는 instance 선언과 별개로 실제 validation 실행 중 방문한 schema 집합도 65/65여야 하며, 누락 시 `SCHEMA_NEGATIVE_EXECUTION_COVERAGE`로 실패한다.

모든 Bounds/enum cell은 `EXECUTABLE`, `ENUM_REFERENCE`, `SEMANTIC_ONLY`, `STRUCTURAL_ONLY` 중 하나로 분류되며 알 수 없는 표현이나 실행 handler가 없는 표현은 `UNSUPPORTED_BOUND_SPEC`으로 시작 단계에서 실패한다. 일반 string의 단일 backtick literal Bounds는 exact equality로 실행되며 불일치는 `STRING_LITERAL_MISMATCH`다. String enum은 기존 exact enum 검증 경로를 유지한다. Loader는 duplicate key, NFC-normalized key collision, float JSON number, BOM과 CRLF를 거부한다. Request deadline은 실제 시각 대신 `FIXTURE_NOW` 고정 clock으로 검증한다. Request, success response와 internal error response의 2 MiB hard limit은 재직렬화 길이가 아닌 원본 UTF-8 transport bytes 길이로 검사한다.

P2.6 validator 성공은 이 worktree의 fixture consistency 증거이며 P2.6 또는 P2 전체 완료를 의미하지 않는다. Commit·push와 외부 검토 후 phase completion을 결정한다.
