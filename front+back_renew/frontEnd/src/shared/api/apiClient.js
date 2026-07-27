import { ApiError, normalizeApiError } from './apiError.js';
import { buildApiUrl, resolveApiBaseUrl } from './config.js';

function hasJsonContentType(response) {
  return response.headers.get('content-type')?.includes('application/json');
}

async function readResponseBody(response) {
  if (response.status === 204) return null;
  if (hasJsonContentType(response)) return response.json();
  const text = await response.text();
  return text || null;
}

function createRequestSignal(externalSignal, timeoutMs) {
  const controller = new AbortController();
  const abort = () => controller.abort(externalSignal?.reason);
  if (externalSignal?.aborted) {
    abort();
  } else {
    externalSignal?.addEventListener('abort', abort, { once: true });
  }
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  return {
    signal: controller.signal,
    cleanup() {
      clearTimeout(timeout);
      externalSignal?.removeEventListener('abort', abort);
    },
  };
}

export function createApiClient({
  baseUrl = resolveApiBaseUrl(),
  fetchImpl = globalThis.fetch,
  tokenProvider,
  onUnauthorized,
  refreshSession,
  timeoutMs = 15000,
} = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('A fetch implementation is required.');
  let refreshPromise = null;

  async function refreshOnce() {
    if (!refreshSession) return false;
    if (!refreshPromise) {
      refreshPromise = Promise.resolve(refreshSession()).finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  async function request(path, options = {}, hasRetried = false) {
    const {
      method = 'GET',
      body,
      headers = {},
      signal: externalSignal,
      requestId,
      authenticate = true,
      refreshOnUnauthorized = true,
    } = options;
    const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;
    const requestHeaders = new Headers(headers);
    let requestBody = body;
    const accessToken = authenticate
      ? await tokenProvider?.getAccessToken?.()
      : null;

    if (accessToken) requestHeaders.set('Authorization', `Bearer ${accessToken}`);
    if (requestId) requestHeaders.set('X-Request-Id', requestId);
    if (body !== undefined && body !== null && !isMultipart) {
      requestHeaders.set('Content-Type', 'application/json');
      requestBody = JSON.stringify(body);
    }

    const requestSignal = createRequestSignal(externalSignal, timeoutMs);
    try {
      if (requestSignal.signal.aborted) {
        throw new DOMException('Request aborted', 'AbortError');
      }
      const response = await fetchImpl(buildApiUrl(baseUrl, path), {
        method,
        body: requestBody,
        headers: requestHeaders,
        signal: requestSignal.signal,
        credentials: 'same-origin',
      });

      if (
        response.status === 401 &&
        refreshOnUnauthorized &&
        !hasRetried
      ) {
        onUnauthorized?.();
        const refreshed = await refreshOnce();
        if (refreshed) {
          requestSignal.cleanup();
          return request(path, options, true);
        }
      }

      const payload = await readResponseBody(response);
      if (!response.ok) {
        throw new ApiError({
          status: response.status,
          code: payload?.error?.code ?? `HTTP_${response.status}`,
          message: payload?.error?.message ?? '요청을 처리하지 못했습니다.',
          fieldErrors: payload?.error?.fieldErrors ?? [],
          retryable: payload?.error?.retryable ?? false,
          requestId: payload?.meta?.requestId ?? response.headers.get('x-request-id'),
        });
      }
      return payload;
    } catch (error) {
      throw normalizeApiError(error);
    } finally {
      requestSignal.cleanup();
    }
  }

  return {
    request,
    get: (path, options) => request(path, options),
    post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
    put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
    patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
    delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
    upload: (path, formData, options) => request(path, { ...options, method: 'POST', body: formData }),
  };
}

export const apiClient = createApiClient();
