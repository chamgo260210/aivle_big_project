# Frontend architecture as built

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24
- Related Source: `frontEnd/src/main.jsx`, `app`, `features`, `shared`

유일한 runtime entry는 `src/main.jsx` → `app/App.jsx`입니다.

- `app`: router, providers, AppShell, project/public layout.
- `features`: auth, projects, documents, structured plan, legal, feasibility, personas, report.
- `shared`: Fetch API client, UI primitives, token/global styles, polling.
- `page`, `layout`, root legacy UI: runtime에 연결되지 않은 reference/prototype.

`features/report`는 기존 latest-result/job API를 병렬 호출하고, pure view model로 상태·다음 행동·검증 과제·provenance를 정규화합니다. 대시보드와 보고서는 같은 모델을 재사용합니다. Markdown export는 allowlist 기반이며 print CSS는 browser PDF save를 지원합니다.

`location.state`는 안전한 post-login return target에만 사용합니다. 보고서와 결과 복구는 URL과 API에 의존합니다. Panel, marketing, finance, settings, password reset은 명시적 placeholder입니다.
