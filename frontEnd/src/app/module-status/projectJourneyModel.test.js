import { describe, expect, it } from 'vitest';
import { getProjectModules, MODULE_STATUS } from './projectModuleModel.js';
import { aggregateJourneyStatus, getJourneyByPath, getJourneyProgress, getProjectJourneys, JOURNEY_STATUS, PROJECT_JOURNEYS } from './projectJourneyModel.js';

describe('project journey model', () => {
  it.each([
    ['/idea', 'planning'], ['/concepts', 'planning'], ['/concepts/compare', 'planning'], ['/concepts/legal-report', 'planning'],
    ['/market', 'validation'], ['/business-model', 'validation'], ['/concept-refinement', 'validation'],
    ['/launch-readiness', 'launch'], ['/technology', 'launch'],
    ['/launch-readiness/reports/technology', 'launch'],
    ['/operations', 'launch'], ['/tech-ops', 'launch'], ['/finance', 'launch'],
    ['/market-interview', 'interview'], ['/marketing', 'marketingStrategy'], ['/final-report', 'finalReport'],
  ])('%s 경로를 %s Journey로 연결한다', (path, journey) => {
    expect(getJourneyByPath(`/app/projects/41${path}`).id).toBe(journey);
  });

  it('top-level Journey를 canonical 여섯 단계로 고정한다', () => {
    expect(PROJECT_JOURNEYS).toHaveLength(6);
    expect(PROJECT_JOURNEYS.map(({ id }) => id)).toEqual([
      'planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport',
    ]);
    expect(PROJECT_JOURNEYS.map(({ shortLabel }) => shortLabel)).toEqual([
      '사업 기획', '사업 검증', '출시 준비', '시장 인터뷰', '마케팅 전략', '최종 보고서',
    ]);
  });

  it('출시 준비는 canonical 한 단계이며 기술·운영·재무를 하위 Journey로 노출하지 않는다', () => {
    const modules = getProjectModules('41', {
      techOps: { status: MODULE_STATUS.READY },
      finance: { status: MODULE_STATUS.FAILED },
      launchReadiness: { status: MODULE_STATUS.READY },
    });
    const launch = getProjectJourneys('41', modules).find(({ id }) => id === 'launch');
    expect(launch.href).toBe('/app/projects/41/launch-readiness');
    expect(launch.children).toEqual([]);
    // 자식 카드는 안 펼치지만 상태는 세 분석이 정한다 — 손을 댄 뒤에는 OPTIONAL 로 덮지 않는다.
    expect(launch.status).toBe(JOURNEY_STATUS.ATTENTION);
  });

  it('선택 단계도 끝내면 완료로 보인다', () => {
    const modules = getProjectModules('41', {
      techOps: { status: MODULE_STATUS.COMPLETED },
      finance: { status: MODULE_STATUS.COMPLETED },
      launchReadiness: { status: MODULE_STATUS.COMPLETED },
      finalReport: { status: MODULE_STATUS.COMPLETED },
    });
    const journeys = getProjectJourneys('41', modules);
    expect(journeys.find(({ id }) => id === 'launch').status).toBe(JOURNEY_STATUS.COMPLETED);
    expect(journeys.find(({ id }) => id === 'finalReport').status).toBe(JOURNEY_STATUS.COMPLETED);
  });

  it('아직 시작하지 않은 선택 단계는 「선택 기능」으로 남는다', () => {
    const journeys = getProjectJourneys('41', getProjectModules('41', {}));
    expect(journeys.find(({ id }) => id === 'launch').status).toBe(JOURNEY_STATUS.OPTIONAL);
    expect(journeys.find(({ id }) => id === 'finalReport').status).toBe(JOURNEY_STATUS.OPTIONAL);
  });

  it('하위 모듈 상태를 결정적으로 집계한다', () => {
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.COMPLETED])).toBe(JOURNEY_STATUS.COMPLETED);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.RUNNING, MODULE_STATUS.NOT_READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.NEEDS_INPUT])).toBe(JOURNEY_STATUS.NEEDS_INPUT);
    expect(aggregateJourneyStatus([MODULE_STATUS.STALE])).toBe(JOURNEY_STATUS.STALE);
  });

  it('첫 미완료 substep을 상위 Journey 진입 경로로 사용한다', () => {
    const modules = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').href)
      .toBe('/app/projects/41/business-model');
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').children.map(({ id }) => id))
      .toEqual(['market', 'businessModel', 'conceptRefinement']);
  });

  it('사업 검증은 Market과 BM만 완료되어도 refinement 전에는 완료되지 않는다', () => {
    const waiting = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.COMPLETED },
      conceptRefinement: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', waiting).find(({ id }) => id === 'validation').status)
      .toBe(JOURNEY_STATUS.IN_PROGRESS);
    const completed = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.COMPLETED },
      conceptRefinement: { status: MODULE_STATUS.COMPLETED },
    });
    expect(getProjectJourneys('41', completed).find(({ id }) => id === 'validation').status)
      .toBe(JOURNEY_STATUS.COMPLETED);
  });

  it('시장 인터뷰 Journey를 canonical 슬롯 하나로 집계한다', () => {
    const modules = getProjectModules('41', {
      marketInterview: { status: MODULE_STATUS.COMPLETED },
    });
    const journeys = getProjectJourneys('41', modules);
    const interview = journeys.find(({ id }) => id === 'interview');
    expect(interview.href).toBe('/app/projects/41/market-interview');
    expect(interview.children.map(({ id }) => id)).toEqual(['marketInterview']);
    expect(interview.status).toBe(JOURNEY_STATUS.COMPLETED);
    expect(journeys.map(({ id }) => id)).toEqual([
      'planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport',
    ]);
  });

  it('출시 준비와 최종 보고서는 프로젝트 진행률에 포함하지 않는다', () => {
    // ⚠ 이 테스트가 지키는 것은 «진행률 분모» 다. 상태가 무엇이든 분모는 4로 고정이어야 한다 —
    //   선택 단계를 끝냈다고 필수 단계 수가 늘어나면 진행률이 뒤로 간다.
    const journeys = getProjectJourneys('41', getProjectModules('41', {
      techOps: { status: MODULE_STATUS.COMPLETED }, finance: { status: MODULE_STATUS.COMPLETED },
      launchReadiness: { status: MODULE_STATUS.COMPLETED }, finalReport: { status: MODULE_STATUS.COMPLETED },
    }));
    expect(journeys.find(({ id }) => id === 'launch').optional).toBe(true);
    expect(journeys.find(({ id }) => id === 'finalReport').optional).toBe(true);
    expect(getJourneyProgress(journeys).total).toBe(4);
  });
});
