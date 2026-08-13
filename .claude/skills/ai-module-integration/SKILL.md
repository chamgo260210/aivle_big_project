---
name: ai-module-integration
description: 이 저장소에 새 AI 기능/TaskType 을 붙이거나 기존 AI 실행 경로(TaskRun·워커·프롬프트·validator)를 고칠 때 쓴다. 두 군데씩 고쳐야 하는 자리가 세 쌍 있고 하나만 고치면 컴파일과 테스트는 통과한 뒤 런타임에만 깨지거나, AI 호출은 성공하고 결과만 조용히 버려진다.
---

# 새 AI 모듈을 붙이는 절차

정본 체크리스트: `docs/architecture/AI_MODULE_INTEGRATION_GUIDE.md`
실제 동작 구조: `docs/architecture/AS_BUILT_ARCHITECTURE.md` §3·§4

---

## 0. 먼저 결정할 것 — 실행 방식

**패턴은 사실상 하나다.** `@Scheduled` 폴러를 가진 **모듈 전용 워커**가
`claim → execute → validate → adopt` 를 하고 화면은 폴링한다.

⚠ **공용 `TaskRunWorker` 클래스는 없다.** 옛 문서가 "패턴 A/B/C 셋"이라고 적고
`JourneyAiService`·`ConceptJourneyService`·`conceptEligibilityExecutor` 를 예로 들지만
**그 클래스들은 전부 존재하지 않는다.**

가장 가까운 기존 워커를 베낀다 — `MarketResearchWorker` 가 가장 완전하다
(트랜잭션 가드 + 금칙 필드 검사 + 결과 검증을 다 갖고 있다).

---

## 1. 둘 다 고쳐야 하는 자리 — 세 쌍

| # | 어디 | 하나만 고치면 |
|---|---|---|
| 1 | `ai/app/api/executions.py` 의 **TaskType 목록 두 군데** | `UNSUPPORTED_TASK_TYPE` |
| 2 | `journey_provider` 의 **`_load_prompts.folders` / `model_types`** (별개 dict) | `AI_CONFIGURATION_INVALID` 또는 `RESULT_SCHEMA_INVALID` |
| 3 | **프롬프트와 validator** | 결과 필드 집합 불일치로 **전체 거부** |

3번이 특히 조용하다 — 프롬프트가 필드를 하나 더 만들기만 해도
`Set.copyOf(result.propertyNames()).equals(expected)` 가 깨져 결과 전체가 버려진다.

---

## 2. 체크리스트

**백엔드**
- [ ] `TaskType` enum 에 추가 (현재 18종)
- [ ] 도메인 서비스 — taskInput 구성 · 해시 · `taskRuns.create`
- [ ] **모듈 전용 워커** + `@Scheduled` 폴러 + **그 안에 결과 검증**
- [ ] 트랜잭션 가드 (`isActualTransactionActive`) — 현재 3개 워커에만 있다. 새 것에도 넣을 것
- [ ] 금칙 필드 검사 (`FORBIDDEN_FIELDS`) — 현재 2개 워커에만 있다
- [ ] 컨트롤러 (`/api/v2`)
- [ ] 마이그레이션이 필요하면 **V22 이상** (V1–V21 immutable)

**AI**
- [ ] `executions.py` TaskType 목록 **두 군데**
- [ ] `_load_prompts.folders` **와** `model_types`
- [ ] `prompts/<folder>/{system.md,user.md}`
- [ ] Pydantic 결과 모델

**계약**
- [ ] `docs/contracts/fixtures/internal-ai-v1/` 에 valid/negative 픽스처
      (CI 가 테스트보다 **먼저** 이걸 돌린다)

---

## 3. 지뢰

1. **AI 호출은 DB 트랜잭션 밖에서.** 서비스 메서드에 `@Transactional` 통째로 → 런타임 예외
2. **taskInput 에는 정수를 쓴다.** ⚠ 막는 것은 canonical hash 가 **아니다**(해셔는 유한 소수를 허용한다).
   실제로 막는 것은 **모듈별 입력 계약**이므로, **새 모듈은 자기 계약에 정수 검사를 직접 넣어야 한다.**
   안 넣으면 소수가 그냥 지나간다
3. **AI 가 ID 를 돌려주면 보낸 ID 와 대조한다** (환각 방지)
4. **응답 봉투는 정확히 12필드** — 초과도 부족도 `RESULT_UNKNOWN_FIELD`
5. `X-Correlation-Id` 헤더 ≠ 본문 `correlationId` → 400
6. `deadlineAt` 은 **미래여야 한다** — 테스트에서 고정 시각을 쓰면 `DEADLINE_EXCEEDED`
7. **Mock 이 없다.** 키가 없으면 실패한다. 가짜 결과를 만드는 경로를 새로 넣지 말 것

---

## 4. 끝났으면

```powershell
cd ai       ; python -m pytest -q
cd backend  ; .\gradlew.bat test --console=plain -q
cd frontEnd ; npm.cmd run test:baseline
```

실스택 스모크를 빼지 말 것 — 백엔드 통합 테스트도 프론트 컴포넌트 테스트도
구조적으로 못 보는 이음새가 있고, 과거에 실제로 거기서만 버그가 잡혔다.
