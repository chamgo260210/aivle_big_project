import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('keeps the complete cutover journey including Market Interview', () => {
    // 사업 검증은 세 걸음이다 — market → businessModel → conceptRefinement.
    expect(PROJECT_MODULES.map((item) => item.id)).toEqual([
      'overview', 'idea', 'concepts', 'market', 'businessModel', 'conceptRefinement',
      'techOps', 'finance', 'marketInterview', 'marketing', 'settings',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
  });

  it('maps canonical module status identifiers', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' },
      { module: 'TWIN_SURVEY', status: 'COMPLETED' },
    ]);
    expect(statuses.concepts.activeTaskRunId).toBe('task');
    expect(statuses.marketInterview.status).toBe('COMPLETED');
    // 백엔드가 옮겨가는 중이라 새 키도 같은 칸으로 받는다.
    expect(normalizeProjectModuleStatuses([{ module: 'MARKET_INTERVIEW', status: 'RUNNING' }]).marketInterview.status)
      .toBe('RUNNING');
    // 다듬기 칸도 서버 상태를 받는다 — 안 받으면 defaultStatus 로 굳어 여정 2번이 영영 완료가 안 된다.
    expect(normalizeProjectModuleStatuses([{ module: 'CONCEPT_REFINEMENT', status: 'NEEDS_INPUT' }])
      .conceptRefinement.status).toBe('NEEDS_INPUT');
  });
});
