import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import CompetitorSeedForm from './CompetitorSeedForm.jsx';

describe('CompetitorSeedForm', () => {
  it('저장된 씨앗을 불러오고 수정값을 서버에 보낸다', async () => {
    const api = {
      currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [{ name: '공비서', reason: '노쇼 방지', operatorName: '' }] }),
      saveCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [{ name: '새 경쟁사', reason: '노쇼 방지', operatorName: '' }] }),
    };
    render(<CompetitorSeedForm api={api} />);
    const name = await screen.findByDisplayValue('공비서');
    fireEvent.change(name, { target: { value: '새 경쟁사' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(api.saveCompetitorSeeds).toHaveBeenCalledWith([
      { name: '새 경쟁사', reason: '노쇼 방지', operatorName: '' },
    ]));
    expect(await screen.findByText('저장했다.')).toBeInTheDocument();
  });

  // ⚠ **서버 경고는 그리지 않는다**(2026-08-16 사용자 지시). 씨앗은 선택 입력인데
  //   안 적었다고 경고를 세우면 «틀린 것을 한 것»처럼 읽힌다. 계약은 그대로라 응답에는
  //   계속 실려 오므로, 「온다」가 아니라 **「와도 안 그린다」**를 못 박는다 —
  //   안 박으면 다음 사람이 「빠뜨렸나?」 하고 되살린다.
  it('서버 경고가 와도 화면에 세우지 않는다', async () => {
    const api = {
      currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [], warning: '씨앗 없이 업종 기준으로 조사한다.' }),
      saveCompetitorSeeds: vi.fn().mockRejectedValue(new Error('중복 이름')),
    };
    render(<CompetitorSeedForm api={api} />);
    await screen.findByRole('button', { name: '저장' });
    expect(screen.queryByText('씨앗 없이 업종 기준으로 조사한다.')).toBeNull();
  });

  // 경고와 달리 **저장 오류는 계속 낸다** — 사용자가 누른 일이 실패한 것이라
  // 안 알리면 저장된 줄 안다.
  it('저장 오류는 사용자에게 표시한다', async () => {
    const api = {
      currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [] }),
      saveCompetitorSeeds: vi.fn().mockRejectedValue(new Error('중복 이름')),
    };
    render(<CompetitorSeedForm api={api} />);
    fireEvent.click(await screen.findByRole('button', { name: '저장' }));
    expect(await screen.findByText('중복 이름')).toBeInTheDocument();
  });
});
