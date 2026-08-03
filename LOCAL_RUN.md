# Local Docker 실행

## 1. 환경변수 준비

PowerShell에서 예제 파일을 복사합니다.

```powershell
Copy-Item .env.example .env
```

`.env`에 최소한 다음 값을 직접 설정합니다. 실제 Secret은 저장소에 커밋하지 않습니다.

- `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`
- `AI_INTERNAL_SERVICE_TOKEN`: 충분히 긴 임의 문자열이며 AI와 Backend가 함께 사용합니다.
- `JWT_SECRET`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`
- OpenAI 호환 Provider를 사용한다면 필요에 따라 `AI_BASE_URL`
- 최초 로컬 관리자 계정이 필요하면 `BOOTSTRAP_ADMIN_ENABLED=true`와 `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`를 설정합니다. 계정 생성 후에는 bootstrap을 다시 비활성화합니다.

AI Provider API Key는 `ai-server` 컨테이너에만 전달됩니다. Frontend와 Backend DB에는 저장되지 않습니다.

## 2. 실행 및 접속

```powershell
docker compose up --build
```

- 서비스: http://localhost:3000
- 회원가입: http://localhost:3000/auth/signup
- 로그인: http://localhost:3000/auth/login

회원가입 또는 로그인 후 프로젝트를 생성하고 다음 순서로 확인합니다.

1. 아이디어 입력 및 AI 해석
2. Idea Version 확정과 법률 사전 검토
3. Concept 생성, 평가, Shortlist, 심층 분석과 선택
4. 합성 Persona 생성, 독립 Interview와 Synthesis
5. Marketing Asset 생성, 선택과 Comparison
6. Final Report 생성, 사용자 Decision 저장, 인쇄/PDF

## 3. 상태와 로그 확인

```powershell
docker compose ps
docker compose logs -f ai-server backend
```

관리자 Role 계정은 `/admin`에서 사용자, 프로젝트, 최근 TaskRun, 서비스 설정 상태를 확인할 수 있습니다. Admin 화면은 Provider API Key나 내부 Token 원문을 표시하지 않습니다.

## 4. 로컬 데이터 초기화

다음 명령은 PostgreSQL과 MinIO의 로컬 Volume을 함께 삭제합니다. 필요한 데이터가 없는지 확인한 뒤 실행하세요.

```powershell
docker compose down -v
```

이후 `docker compose up --build`를 실행하면 빈 로컬 환경으로 시작합니다.
