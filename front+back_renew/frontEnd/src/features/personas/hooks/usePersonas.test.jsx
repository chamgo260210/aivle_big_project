import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { usePersonas } from './usePersonas.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({
  useApiClient: vi.fn(),
}));

describe('usePersonas', () => {
  beforeEach(() => vi.clearAllMocks());

  it('accepts a NEEDS_VALIDATION feasibility result as a valid persona input', async () => {
    const notFound = Object.assign(new Error('not found'), { status: 404 });
    const client = {
      get: vi.fn()
        .mockResolvedValueOnce({ data: [] })
        .mockRejectedValueOnce(notFound)
        .mockRejectedValueOnce(notFound)
        .mockResolvedValueOnce({
          data: {
            assessmentId: 8,
            structuredPlanId: 3,
            status: 'NEEDS_VALIDATION',
          },
        }),
    };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => usePersonas('10'));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.feasibility.assessmentId).toBe(8);
  });
});
