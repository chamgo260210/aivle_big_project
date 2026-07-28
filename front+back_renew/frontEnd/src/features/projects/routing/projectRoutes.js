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
  overview: (projectId) => projectBase(projectId),
  getStarted: (projectId) => `${projectBase(projectId)}/get-started`,
  plan: (projectId) => `${projectBase(projectId)}/plan`,
  briefSettings: (projectId) => `${projectBase(projectId)}/settings`,
  documents: (projectId) => `${projectBase(projectId)}/plan/documents`,
  structure: (projectId) => `${projectBase(projectId)}/plan/structure`,
  review: (projectId) => `${projectBase(projectId)}/review`,
  legal: (projectId) => `${projectBase(projectId)}/review/legal`,
  feasibility: (projectId) => `${projectBase(projectId)}/review/market`,
  validate: (projectId) => `${projectBase(projectId)}/validate`,
  personas: (projectId) => `${projectBase(projectId)}/validate/personas`,
  report: (projectId) => `${projectBase(projectId)}/report`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
  danger: (projectId) => `${projectBase(projectId)}/settings`,
});
