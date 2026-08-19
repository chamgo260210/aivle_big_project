import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('가상 인터뷰를 canonical 시장 인터뷰 슬롯 하나로 노출한다', () => {
    expect(PROJECT_MODULES.map((item) => item.id)).toEqual([
      'overview', 'idea', 'concepts', 'market', 'businessModel', 'conceptRefinement',
      'techOps', 'finance', 'launchReadiness', 'marketInterview', 'marketing', 'finalReport', 'settings',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/legal-report').id).toBe('concepts');
    // 사업 검증은 세 걸음이다 — market → businessModel → conceptRefinement. 각각 제 주소를 갖는다.
    expect(getProjectModuleByPath('41', '/app/projects/41/market').id).toBe('market');
    expect(getProjectModuleByPath('41', '/app/projects/41/business-model').id).toBe('businessModel');
    expect(getProjectModuleByPath('41', '/app/projects/41/concept-refinement').id).toBe('conceptRefinement');
  });

  it('기술·운영·재무·출시 준비 상태를 서로 독립적으로 projection한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'TECH_OPS', status: 'COMPLETED', requiredInputs: [] },
      { module: 'FINANCE', status: 'RUNNING', activeTaskRunId: 'finance-run' },
      { module: 'LAUNCH_READINESS', status: 'READY' },
    ]);
    expect(statuses.techOps.status).toBe('COMPLETED');
    expect(statuses.finance.status).toBe('RUNNING');
    expect(statuses.launchReadiness.status).toBe('READY');
    expect(getProjectModuleByPath('41', '/app/projects/41/technology').id).toBe('launchReadiness');
    expect(getProjectModuleByPath('41', '/app/projects/41/tech-ops').id).toBe('techOps');
    expect(getProjectModuleByPath('41', '/app/projects/41/finance').id).toBe('finance');
  });

  it('CONCEPT_REFINEMENT 상태를 사업 검증의 세 번째 근거로 보존한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'MARKET_ANALYSIS', status: 'COMPLETED' },
      { module: 'BUSINESS_MODEL', status: 'COMPLETED' },
      { module: 'CONCEPT_REFINEMENT', status: 'NEEDS_INPUT', activeRunId: 'round-1' },
    ]);
    expect(statuses.conceptRefinement).toMatchObject({ status: 'NEEDS_INPUT', activeRunId: 'round-1' });
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
