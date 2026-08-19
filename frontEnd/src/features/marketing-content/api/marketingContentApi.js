const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/marketing-contents`;
const unwrap = (response) => response.data;

export function createMarketingContentApi(client) {
  return Object.freeze({
    list: async (projectId, options) => unwrap(await client.get(root(projectId), options)),
    current: async (projectId, options) => unwrap(await client.get(`${root(projectId)}/current`, options)),
    detail: async (projectId, contentId, options) => unwrap(await client.get(`${root(projectId)}/${encodeURIComponent(contentId)}`, options)),
    create: async (projectId, request, idempotencyKey, options = {}) => unwrap(await client.post(root(projectId), request, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey, 'X-Correlation-Id': idempotencyKey },
    })),
    update: async (projectId, contentId, request, options) => unwrap(await client.patch(`${root(projectId)}/${encodeURIComponent(contentId)}`, request, options)),
    regenerate: async (projectId, contentId, idempotencyKey, options = {}) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(contentId)}/regenerate`, {}, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey, 'X-Correlation-Id': idempotencyKey },
    })),
    retry: async (projectId, contentId, idempotencyKey, options = {}) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(contentId)}/retry`, {}, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey, 'X-Correlation-Id': idempotencyKey },
    })),
    finalize: async (projectId, contentId, options) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(contentId)}/finalize`, {}, options)),
    // 생성된 키비주얼은 Bearer 토큰이 필요해 <img src>로 직접 못 부른다.
    // blob 으로 받아 화면에서 object URL 로 바꿔 쓴다.
    image: async (projectId, contentId, options) =>
      (await client.download(`${root(projectId)}/${encodeURIComponent(contentId)}/image`, options)).blob,
    uploadReference: async (projectId, file, options) => {
      const form = new FormData(); form.append('file', file);
      return unwrap(await client.upload(`/api/v3/projects/${encodeURIComponent(projectId)}/evidence-artifacts`, form, options));
    },
  });
}
