import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import BusinessProposalWorkspace from './BusinessProposalWorkspace.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';

/**
 * BM 실행 계획 — <b>컨셉 단계에서</b> 받는다. 여기서 재는 것은 <b>제출 게이트</b>다.
 *
 * <p>「빈 칸이 있으면 확인받는다」와 「계획을 저장한 뒤에 가설을 굳힌다」는 화면에서만
 * 성립한다. 서버에도 AI 에도 그 개념이 없으므로 여기서 안 재면 아무도 안 잰다.
 *
 * <p>2026-08-13 에 `market/BmPlanPhase.test.jsx` 에서 옮겨 왔다 — 폼이 BM 화면 앞
 * 국면에서 가설 확인 섹션으로 갔다.
 */
const marketApi = { currentBmPlan: vi.fn(), saveBmPlan: vi.fn() };

vi.mock('../../market/marketApi.js', () => ({ createMarketApi: () => marketApi }));
vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => ({}) }));
vi.mock('../hooks/useConceptPortfolio.js', () => ({ useConceptPortfolio: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({
  jobEventMessage: (event) => event.message,
  useJobEvents: () => ({ events: [], transport: 'idle' }),
}));

// 계약: `confirm(changes, savePlan)` 은 **savePlan 을 먼저 부른다.** 그 순서 자체는
// `useConceptPortfolio.test.jsx` 가 재고, 여기서는 화면이 savePlan 을 실어 보내는지 본다.
const confirm = vi.fn(async (changes, savePlan) => { await savePlan(); });

const portfolio = (overrides = {}) => ({
  loading: false, error: null, busy: false,
  run: { runId: 'run', productStatus: 'RESULTS_AVAILABLE', producedConceptCount: 1, openInputCount: 0 },
  concepts: [{ conceptId: 'c1', candidateId: 'candidate', conceptName: '지역 서비스', summary: '요약' }],
  inputRequests: [], hypotheses: [], report: null, marketSeed: null,
  selection: { selectionId: 17, conceptId: 'c1', hypothesisConfirmedCount: 0 },
  select: vi.fn(), refresh: vi.fn(), start: vi.fn(), respond: vi.fn(), retryContinuation: vi.fn(),
  confirm, alternative: vi.fn(), retryDelta: vi.fn(), finalizeReport: vi.fn(), finalizeMarketSeed: vi.fn(),
  ...overrides,
});

const renderWorkspace = () => render(
  <MemoryRouter initialEntries={['/app/projects/41/concepts']}>
    <Routes>
      <Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} />
    </Routes>
  </MemoryRouter>,
);

const submit = () => fireEvent.click(screen.getByRole('button', { name: '7개 검증 가정 확인' }));

beforeEach(() => {
  vi.clearAllMocks();
  marketApi.currentBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 0 });
  marketApi.saveBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 1 });
  useConceptPortfolio.mockReturnValue(portfolio());
});

describe('BM 실행 계획 — 가설 확인 섹션의 제출 게이트', () => {
  it('가설 확인 자리에서 같이 묻는다', async () => {
    renderWorkspace();
    expect(await screen.findByText('BM 분석에 이것만 더 필요합니다')).toBeInTheDocument();
    expect(screen.getByLabelText(/고객과 계속 이어지는 방식/)).toBeInTheDocument();
    expect(screen.getByLabelText(/쓸 수 있는 예산/)).toBeInTheDocument();
  });

  it('계획 폼은 앞에서 확정하는 것을 다시 묻지 않는다', async () => {
    const { container } = renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');
    // 수익모델·차별점은 **가설 카드**가 받는다. 계획 폼이 또 물으면 두 번 치게 된다.
    const form = container.querySelector('.bm-plan');
    expect(form.textContent).not.toMatch(/수익\s*모델/);
    expect(form.textContent).not.toMatch(/차별점/);
  });

  it('⭐ 빈 칸이 있으면 확인 없이 확정하지 않는다', async () => {
    renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');

    submit();

    expect(await screen.findByText('비어 있는 칸이 있습니다')).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
    expect(marketApi.saveBmPlan).not.toHaveBeenCalled();
  });

  it('⭐ 「돌아가서 채우기」를 누르면 확정이 안 간다', async () => {
    renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');
    submit();
    await screen.findByText('비어 있는 칸이 있습니다');

    fireEvent.click(screen.getByRole('button', { name: '돌아가서 채우기' }));

    await waitFor(() =>
      expect(screen.queryByText('비어 있는 칸이 있습니다')).not.toBeInTheDocument());
    expect(confirm).not.toHaveBeenCalled();
  });

  it('확인 문구가 어느 칸이 빌지 이름으로 말한다', async () => {
    renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');
    fireEvent.change(screen.getByLabelText(/고객과 계속 이어지는 방식/),
      { target: { value: '자동 알림' } });
    submit();

    const dialog = await screen.findByRole('dialog');
    expect(dialog.textContent).toContain('핵심 활동');
    expect(dialog.textContent).not.toContain('고객 관계,');
  });

  it('⭐ 「이대로 진행」이면 계획을 저장한 뒤 가설을 굳힌다', async () => {
    renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');
    submit();
    await screen.findByText('비어 있는 칸이 있습니다');

    fireEvent.click(screen.getByRole('button', { name: '이대로 진행' }));

    await waitFor(() => expect(marketApi.saveBmPlan).toHaveBeenCalled());
    expect(confirm).toHaveBeenCalled();
  });

  it('전부 채우면 확인 없이 바로 간다', async () => {
    marketApi.currentBmPlan.mockResolvedValue({
      plan: {
        customer_relationship: '자동 알림',
        key_activities: ['예약 통합'],
        key_resources: ['결제 연동'],
        key_partners: ['PG'],
      },
      constraints: { budget_krw: 5000000 },
      revision: 3,
    });
    renderWorkspace();
    await waitFor(() =>
      expect(screen.getByLabelText(/고객과 계속 이어지는 방식/).value).toBe('자동 알림'));

    submit();

    await waitFor(() => expect(confirm).toHaveBeenCalled());
    expect(screen.queryByText('비어 있는 칸이 있습니다')).not.toBeInTheDocument();
  });

  it('⭐ 친 값이 실제로 실려 간다 — 빈 칸은 빠진 채로', async () => {
    renderWorkspace();
    await screen.findByText('BM 분석에 이것만 더 필요합니다');
    fireEvent.change(screen.getByLabelText(/혼자 못 하는 부분/),
      { target: { value: 'PG\n예약 플랫폼' } });
    fireEvent.change(screen.getByLabelText(/쓸 수 있는 예산/), { target: { value: '5000000' } });

    submit();
    fireEvent.click(await screen.findByRole('button', { name: '이대로 진행' }));

    await waitFor(() => expect(marketApi.saveBmPlan).toHaveBeenCalled());
    const [plan, constraints] = marketApi.saveBmPlan.mock.calls[0];
    expect(plan).toEqual({ key_partners: ['PG', '예약 플랫폼'] });
    expect(constraints).toEqual({ budget_krw: 5000000 });
  });

  it('저장분이 폼으로 돌아온다 — 다시 오면 친 것이 남아 있어야 한다', async () => {
    marketApi.currentBmPlan.mockResolvedValue({
      plan: { key_activities: ['예약 통합', '보증금 청구'] },
      constraints: { months: 10 },
      revision: 2,
    });
    renderWorkspace();

    await waitFor(() => expect(screen.getByLabelText(/반복해서 해야 하는 일/).value)
      .toBe('예약 통합\n보증금 청구'));
    // 「기간」만으로는 가설 카드의 기간 칸과 겹친다 — 단위까지 적어야 계획 칸이다.
    expect(screen.getByLabelText('기간 (개월)').value).toBe('10');
  });
});
