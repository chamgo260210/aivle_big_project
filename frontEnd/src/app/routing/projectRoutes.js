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
  legalReport: (projectId) => `${projectBase(projectId)}/concepts/legal-report`,
  market: (projectId) => `${projectBase(projectId)}/market`,
  businessModel: (projectId) => `${projectBase(projectId)}/business-model`,
  conceptRefinement: (projectId) => `${projectBase(projectId)}/concept-refinement`,
  marketInterview: (projectId) => `${projectBase(projectId)}/market-interview`,
  // 출시 준비는 팀원 판(#49)을 그대로 받는다. techOps·finance 는 그 화면의 다른 초점으로
  // 들어가므로 «옛 경로 이름은 남기되 목적지는 하나»다 — 부르는 곳을 안 고쳐도 된다.
  launchReadiness: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  launchReadinessReport: (projectId, reportType, modules = []) => {
    const route = `${projectBase(projectId)}/launch-readiness/reports/${encodeURIComponent(reportType)}`;
    if (reportType !== 'integrated' || modules.length === 0) return route;
    return `${route}?${modules.map((module) => `modules=${encodeURIComponent(module)}`).join('&')}`;
  },
  technology: (projectId) => `${projectBase(projectId)}/technology`,
  operations: (projectId) => `${projectBase(projectId)}/operations`,
  techOps: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  finance: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  marketing: (projectId) => `${projectBase(projectId)}/marketing`,
  finalReport: (projectId) => `${projectBase(projectId)}/final-report`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
});

export function getProjectRoute(projectId, moduleId) {
  const route = projectRoutes[moduleId];
  return typeof route === 'function' ? route(projectId) : projectRoutes.overview(projectId);
}
