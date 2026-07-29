# Testing and quality gate

- Status: Current
- Verified HEAD: `fd30d55856dd3f266abadea79232c834358abc91`
- Verified Date: 2026-07-24

| Gate | Result |
|---|---|
| `npm ci` | pass, 222 packages |
| `npm audit --audit-level=high` | pass, 0 vulnerabilities |
| frontend lint | pass |
| frontend Vitest | 30 files, 192 tests |
| frontend build | pass, 91 modules, JS 368.01 kB, CSS 34.61 kB |
| backend `clean test build bootJar` | pass |
| backend H2 | 33 suites, 168 tests |
| backend PostgreSQL | 6 suites, 19 tests |
| Flyway/Hibernate | V1–V9 fresh/upgrade/validate through gates |
| OpenAPI Redocly 2.20.5 | 0 errors, 0 warnings |
| Chrome E2E | full Mock current flow pass |
| responsive | 360/390/768/1024/1280/1440, no overflow |
| touch targets | report/dashboard measured at least 44 px |
| external AI calls | 0 |

Browser evidence used the real backend, Vite, isolated H2/storage, Mock AI, and Playwright. The repository still has no committed E2E framework. Live Mock did not create missing fields; FILLED/WAIVED remains integration/component/fixture coverage.

Security scan found environment-backed secrets, owner scope, local allowlisted CORS, no token/raw provider logging/export, and no legacy demo-password strings.
