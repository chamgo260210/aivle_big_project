import { describe, expect, it } from 'vitest';
import { MODULE_STATUS, PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('shows Concept Portfolio as one business-proposal journey step', () => {
    expect(PROJECT_MODULES.map((item) => item.shortLabel)).toEqual([
      '개요', '아이디어', '사업안', '사업 검증', '기술·운영', '재무', '시장 인터뷰', '마케팅 콘텐츠', '설정',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
  });

  it('provides all independent module statuses and canonical routes without project.stage', () => {
    expect(Object.keys(MODULE_STATUS)).toEqual([
      'NOT_READY', 'READY', 'QUEUED', 'RUNNING', 'NEEDS_INPUT',
      'COMPLETED', 'FAILED', 'STALE', 'NOT_CONNECTED',
    ]);
    expect(PROJECT_MODULES).toHaveLength(9);
    expect(getProjectModules('project / 1').map((module) => module.href)).toEqual([
      '/app/projects/project%20%2F%201/overview',
      '/app/projects/project%20%2F%201/idea',
      '/app/projects/project%20%2F%201/concepts',
      '/app/projects/project%20%2F%201/business-validation',
      '/app/projects/project%20%2F%201/tech-ops',
      '/app/projects/project%20%2F%201/finance',
      '/app/projects/project%20%2F%201/market-interview',
      '/app/projects/project%20%2F%201/marketing',
      '/app/projects/project%20%2F%201/settings',
    ]);
  });

  it('maps the V2 canonical module status and keeps legacy aliases transitional only', () => {
    const statuses = normalizeProjectModuleStatuses([{ module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' }]);
    expect(statuses.concepts.status).toBe('RUNNING');
    expect(statuses.concepts.activeTaskRunId).toBe('task');
  });

  it('folds market research and the BM canvas into one business-validation slot', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'MARKET_ANALYSIS', status: 'RUNNING' },
      // 백엔드 enum 에는 남아 있지만 findAll() 이 더는 돌려주지 않는다. 와도 칸이 없다.
      { module: 'BUSINESS_MODEL', status: 'COMPLETED' },
    ]);
    expect(statuses.market.status).toBe('RUNNING');
    expect(statuses.businessModel).toBeUndefined();
    expect(getProjectModules('41').filter((item) => item.id === 'businessModel')).toHaveLength(0);
  });

  it('keeps the market interview on its own slot', () => {
    const statuses = normalizeProjectModuleStatuses([{ module: 'PANEL_SURVEY', status: 'COMPLETED' }]);
    expect(statuses.panelSurvey.status).toBe('COMPLETED');
  });
});
