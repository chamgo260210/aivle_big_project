import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('keeps the complete cutover journey including Market Interview', () => {
    expect(PROJECT_MODULES.map((item) => item.id)).toEqual([
      'overview', 'idea', 'concepts', 'market', 'businessModel', 'techOps',
      'finance', 'marketInterview', 'marketing', 'settings',
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
  });
});
