const projectBase = (projectId) => `/app/projects/${encodeURIComponent(projectId)}`;

export const appRoutes = Object.freeze({
  home: '/app',
  projects: '/app/projects',
  newProject: '/app/projects/new',
  profileSettings: '/app/settings/profile',
  securitySettings: '/app/settings/security',
});

export const projectRoutes = Object.freeze({
  base: projectBase,
  overview: (projectId) => `${projectBase(projectId)}/overview`,
  idea: (projectId) => `${projectBase(projectId)}/idea`,
  concepts: (projectId) => `${projectBase(projectId)}/concepts`,
  conceptCompare: (projectId) => `${projectBase(projectId)}/concepts/compare`,
  // 여정 3번. 시장분석과 BM 캔버스가 「사업 검증」 한 칸으로 접혔다. id 는 `market` 그대로다 —
  // 백엔드 `PipelineModuleType.MARKET_ANALYSIS` 와 짝이라 이름을 바꾸면 상태 매핑이 조용히 끊긴다.
  market: (projectId) => `${projectBase(projectId)}/business-validation`,
  techOps: (projectId) => `${projectBase(projectId)}/tech-ops`,
  finance: (projectId) => `${projectBase(projectId)}/finance`,
  // 여정 7번. id 는 `panelSurvey` 그대로다 — 백엔드 `PipelineModuleType.PANEL_SURVEY` 와
  // 짝이라 이름을 바꾸면 상태 매핑이 조용히 끊긴다. 바뀐 것은 화면과 경로다.
  panelSurvey: (projectId) => `${projectBase(projectId)}/market-interview`,
  marketing: (projectId) => `${projectBase(projectId)}/marketing`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
});

export function getProjectRoute(projectId, moduleId) {
  const route = projectRoutes[moduleId];
  return typeof route === 'function' ? route(projectId) : projectRoutes.overview(projectId);
}
