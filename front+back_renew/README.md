# AI 사업검증 플랫폼

사업계획서 DOCX를 구조화하고 법률 사전검토, 사업 타당성 평가, 데이터 기반 페르소나·고객 검증 계획, 통합 보고서까지 연결하는 교육·데모용 플랫폼입니다.

Phase 11 구현 기준은 `fd30d55856dd3f266abadea79232c834358abc91`입니다. 상세 As-built 문서는 [`docs/current/README.md`](docs/current/README.md), 발표·인계용 요약은 [`FINAL_PRODUCT_BRIEFING.md`](docs/current/FINAL_PRODUCT_BRIEFING.md)에서 확인합니다.

## 빠른 시작

- Backend: Java 17, Spring Boot 4.1.0, `cd backend && gradlew.bat bootRun`
- Frontend: Node 22, `cd frontEnd && npm.cmd ci && npm.cmd run dev`
- 로컬 API: `VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1`
- 통합 보고서: `/projects/:projectId/report`
- API 계약: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)
- 실행·데모: [`LOCAL_DEVELOPMENT_AND_DEMO.md`](docs/current/LOCAL_DEVELOPMENT_AND_DEMO.md)
- 품질 게이트: [`TESTING_AND_QUALITY_GATE.md`](docs/current/TESTING_AND_QUALITY_GATE.md)

## 현재 범위

인증, 프로젝트, DOCX 업로드·비동기 파싱, 12개 섹션 구조화·보완·확정, 법률 사전검토, 사업 타당성, 페르소나 추천, 프로젝트 대시보드, 현재 결과 통합 보고서, Markdown 다운로드와 브라우저 PDF 저장을 제공합니다.

기본 AI는 Mock입니다. 결과를 실제 시장·법률·고객 사실로 오인하면 안 됩니다. 비밀값은 환경변수로만 제공하고 커밋하지 마십시오.

## 알려진 한계

실제 AI 운영 인증, 최신 외부 법률·시장 데이터, 고객 응답 수집, 보고서 snapshot/version/share, 서버 PDF, password reset/MFA, HttpOnly refresh cookie는 아직 없습니다.
