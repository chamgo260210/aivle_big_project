import { describe, expect, it, vi } from 'vitest';

import { createReportApi } from './reportApi.js';

function clientWith(handler) {
  return {
    get: vi.fn(handler),
  };
}

describe('report api aggregation', () => {
  it('loads eight existing result and job resources in parallel', async () => {
    const pending = [];
    const client = clientWith((path) => new Promise((resolve) => pending.push({ path, resolve })));
    const promise = createReportApi(client).load(10);
    await Promise.resolve();
    expect(client.get).toHaveBeenCalledTimes(8);
    pending.forEach(({ path, resolve }) => resolve({ data: { path } }));
    const result = await promise;
    expect(result.plan.state).toBe('available');
    expect(result.personaJob.state).toBe('available');
  });

  it('classifies 404 as an unstarted section', async () => {
    const client = clientWith(async () => { throw { status: 404 }; });
    const result = await createReportApi(client).load(10);
    expect(Object.values(result).every((resource) => resource.state === 'missing')).toBe(true);
  });

  it('preserves individual non-404 errors', async () => {
    const client = clientWith(async (path) => {
      if (path.includes('legal-reviews/latest')) throw new Error('network');
      return { data: {} };
    });
    const result = await createReportApi(client).load(10);
    expect(result.legalReview.state).toBe('error');
    expect(result.plan.state).toBe('available');
  });

  it('forwards the same AbortSignal to every request', async () => {
    const client = clientWith(async () => ({ data: {} }));
    const controller = new AbortController();
    await createReportApi(client).load(10, { signal: controller.signal });
    expect(client.get.mock.calls.every(([, options]) => options.signal === controller.signal)).toBe(true);
  });

  it.each([
    ['/projects/10/structured-plans/latest'],
    ['/projects/10/jobs/latest?jobType=DOCUMENT_PARSE'],
    ['/projects/10/legal-reviews/latest'],
    ['/projects/10/jobs/latest?jobType=LEGAL_REVIEW'],
    ['/projects/10/feasibility-assessments/latest'],
    ['/projects/10/jobs/latest?jobType=FEASIBILITY_ANALYSIS'],
    ['/projects/10/persona-recommendations/latest'],
    ['/projects/10/jobs/latest?jobType=PERSONA_RECOMMENDATION'],
  ])('reuses existing endpoint %s', async (expectedPath) => {
    const client = clientWith(async () => ({ data: {} }));
    await createReportApi(client).load(10);
    expect(client.get.mock.calls.map(([path]) => path)).toContain(expectedPath);
  });
});
