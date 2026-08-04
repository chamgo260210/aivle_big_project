# Idea → Legal → Concept 로컬 환경 설정

현재 재설계 범위는 Idea 입력부터 적격 Concept 3개 표시까지다. 시장 이후 기존 MVP는 이 흐름과 연결하지 않는다.

## 1. 환경 파일

PowerShell에서 저장소 루트를 기준으로 실행한다.

```powershell
Copy-Item .env.example .env
```

`.env.demo.example`을 사용하는 별도 데모 환경이라면 해당 파일을 복사할 수 있다. 실제 Secret이 들어간 `.env`는 저장소에 포함하지 않는다.

## 2. AI와 내부 인증

실제 AI 실행에는 다음 값이 필요하다.

```dotenv
AI_PROVIDER=openai
AI_API_KEY=replace-with-provider-key
AI_MODEL=replace-with-model-id
AI_BASE_URL=
AI_INTERNAL_SERVICE_TOKEN=replace-with-long-random-token
AI_FIXTURE_MODE=false
```

- `AI_PROVIDER`는 `openai` 또는 `openai-compatible`이다.
- OpenAI 기본 endpoint를 사용하면 `AI_BASE_URL`은 비워 둔다.
- `AI_INTERNAL_SERVICE_TOKEN`은 ai-server의 내부 실행 API 인증값이다.
- compose는 같은 값을 Backend의 `AI_SERVER_INTERNAL_API_KEY`로 전달한다. 두 서비스의 내부 token을 서로 다르게 설정하지 않는다.
- `AI_FIXTURE_MODE=false`를 유지한다. 현재 Journey에는 하드코딩 성공 fallback이 없다.

## 3. 법제처 Source

```dotenv
MOLEG_API_KEY=replace-with-moleg-key
MOLEG_API_BASE_URL=https://www.law.go.kr/DRF
LEGAL_REGISTRY_VERSION=legal-registry-v1
LEGAL_PROVIDER_TIMEOUT_SECONDS=30
LEGAL_SOURCE_CACHE_SECONDS=3600
```

- 실제 공식 Source 검증 전 `MOLEG_API_KEY`를 입력한다.
- `LEGAL_REGISTRY_VERSION`은 ai-server에 포함된 Registry 버전과 일치해야 한다.
- `SOURCE_PARTIAL`과 `REGISTRY_GAP`은 성공 fallback이 아니다. 화면에서 전문가 또는 Registry 보완 필요 상태로 표시된다.

## 4. Concept Eligibility 한도

```dotenv
CONCEPT_TARGET_ELIGIBLE_COUNT=3
CONCEPT_MAX_REPLACEMENT_ROUNDS=2
CONCEPT_MAX_INSPECTED_CANDIDATES=9
```

기본 정책은 적격 3개, 대체 2라운드, 전체 검사 9개다.

## 5. 필수 Application 값

로컬 compose 실행 전 최소한 다음 값도 설정한다.

```dotenv
JWT_SECRET=replace-with-at-least-32-byte-secret
POSTGRES_PASSWORD=replace-with-local-password
MINIO_ROOT_PASSWORD=replace-with-local-password
```

운영 Domain, HTTPS, Secret Manager, 운영 DB, 배포 및 CI 설정은 현재 범위에 포함하지 않는다.
