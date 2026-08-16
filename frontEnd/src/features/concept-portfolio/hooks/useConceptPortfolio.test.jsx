import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import { startNewConceptPortfolioRun, useConceptPortfolio } from './useConceptPortfolio.js';

describe('useConceptPortfolio live invalidation', () => {
  it('re-reads canonical REST state after project event revision changes', async () => {
    const client = { get: vi.fn((path) => {
      if (path.endsWith('/current') && path.includes('concept-portfolio-runs')) return Promise.resolve({ data: { runId: 'run-1' } });
      if (path.endsWith('/concepts')) return Promise.resolve({ data: [{ conceptId: 'c1', candidateId: 'candidate', selectable: true }] });
      if (path.endsWith('/input-requests')) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: null });
    }), post: vi.fn() };
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result, rerender } = renderHook(({ revision }) => useConceptPortfolio('41', revision), { wrapper, initialProps: { revision: 0 } });
    await waitFor(() => expect(result.current.loading).toBe(false));
    const firstReads = client.get.mock.calls.filter(([path]) => path.includes('concept-portfolio-runs/current')).length;
    rerender({ revision: 1 });
    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.includes('concept-portfolio-runs/current')).length).toBeGreaterThan(firstReads));
  });

  // ⚠ `plan` 은 정수가 아니면 400 이다. 확정을 먼저 보내면 가설만 굳고 계획은 빈 채로
  //    남아, 캔버스의 그 칸이 영영 빈다.
  it('⭐ 가설을 굳히기 전에 실행 계획을 먼저 저장한다', async () => {
    const order = [];
    const client = {
      get: vi.fn((path) => Promise.resolve({
        data: path.endsWith('concept-portfolio-selections/current') ? { selectionId: 17 } : null,
      })),
      post: vi.fn(() => { order.push('confirm'); return Promise.resolve({ data: null }); }),
    };
    const savePlan = vi.fn(() => { order.push('savePlan'); return Promise.resolve(); });
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useConceptPortfolio('41'), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await result.current.confirm([], savePlan);

    expect(order).toEqual(['savePlan', 'confirm']);
  });

  it('⭐ 계획 저장이 실패하면 가설을 굳히지 않는다', async () => {
    const client = {
      get: vi.fn((path) => Promise.resolve({
        data: path.endsWith('concept-portfolio-selections/current') ? { selectionId: 17 } : null,
      })),
      post: vi.fn(),
    };
    const savePlan = vi.fn().mockRejectedValue(new Error('계획을 저장하지 못했다.'));
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useConceptPortfolio('41'), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await result.current.confirm([], savePlan);

    expect(client.post).not.toHaveBeenCalled();
    await waitFor(() => expect(result.current.error).toBeTruthy());
  });

  it('uses a fresh idempotency key for each terminal Portfolio retry', async () => {
    const api = { ideaBrief: vi.fn().mockResolvedValue({ data: { confirmedSnapshotId: 'brief-1' } }),
      createRun: vi.fn().mockResolvedValue({ data: { runId: 'run' } }) };
    await startNewConceptPortfolioRun(api, '41');
    await startNewConceptPortfolioRun(api, '41');
    const first = api.createRun.mock.calls[0][1];
    const second = api.createRun.mock.calls[1][1];
    expect(first).toMatchObject({ ideaBriefSnapshotId: 'brief-1', maxConcepts: 5 });
    expect(second.idempotencyKey).not.toBe(first.idempotencyKey);
  });
});
