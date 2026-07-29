export function createAdminApi(client) {
  const base = '/admin';
  return {
    overview: () => client.get(`${base}/overview`).then((r) => r.data),
    users: (params = {}) => client.get(`${base}/users?${new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))}`).then((r) => r.data),
    user: (id) => client.get(`${base}/users/${encodeURIComponent(id)}`).then((r) => r.data),
    updateStatus: (id, input) => client.patch(`${base}/users/${encodeURIComponent(id)}/status`, input).then((r) => r.data),
    updateRole: (id, input) => client.patch(`${base}/users/${encodeURIComponent(id)}/role`, input).then((r) => r.data),
    revokeSessions: (id, input) => client.post(`${base}/users/${encodeURIComponent(id)}/sessions/revoke`, input),
    projects: (params = {}) => client.get(`${base}/projects?${new URLSearchParams(params)}`).then((r) => r.data),
    audit: (params = {}) => client.get(`${base}/audit?${new URLSearchParams(params)}`).then((r) => r.data),
    settings: () => client.get(`${base}/settings`).then((r) => r.data),
    updateSetting: (key, input) => client.patch(`${base}/settings/${encodeURIComponent(key)}`, input).then((r) => r.data),
    services: () => client.get(`${base}/ai/services`).then((r) => r.data),
    jobs: () => client.get(`${base}/jobs`).then((r) => r.data),
  };
}
