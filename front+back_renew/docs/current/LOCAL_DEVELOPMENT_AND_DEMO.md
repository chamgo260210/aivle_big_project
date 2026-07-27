# Local development and demo

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`

## Prerequisites

Java 17, Node 22/npm 10, Docker Desktop for PostgreSQL tests.

```powershell
cd backend
$env:JWT_SECRET='replace-with-at-least-32-byte-local-secret'
.\gradlew.bat bootRun

cd ..\frontEnd
npm.cmd ci
$env:VITE_API_BASE_URL='http://127.0.0.1:8080/api/v1'
npm.cmd run dev
```

AI 기본값은 Mock/disabled입니다. Real mode는 reviewed base URL/model/key/limits/timeouts와 명시적 활성화가 필요합니다.

## Demo

Signup → project → dashboard → valid DOCX → job recovery → structured result → missing-field completion when present → confirm → legal → feasibility → persona → dashboard → report → Markdown → print/PDF → refresh → logout/login return.

기본 document Mock는 12개 항목을 모두 PRESENT로 반환하므로 FILLED/WAIVED 단계가 없을 수 있습니다. 존재하지 않는 보완 항목을 시연했다고 말하지 않습니다.

## Quality commands

```powershell
cd frontEnd
npm.cmd ci
npm.cmd audit --audit-level=high
npm.cmd run lint
npm.cmd run test:run
npm.cmd run build

cd ..\backend
.\gradlew.bat clean test build bootJar
.\gradlew.bat postgresTest

cd ..
npx.cmd --yes @redocly/cli@2.20.5 lint docs/api/openapi.yaml
```
