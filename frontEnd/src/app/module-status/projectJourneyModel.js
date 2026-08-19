import { projectRoutes } from '../routing/projectRoutes.js';
import { MODULE_STATUS } from './projectModuleModel.js';

export const JOURNEY_STATUS = Object.freeze({
  NOT_STARTED: 'NOT_STARTED',
  READY: 'READY',
  IN_PROGRESS: 'IN_PROGRESS',
  NEEDS_INPUT: 'NEEDS_INPUT',
  ATTENTION: 'ATTENTION',
  STALE: 'STALE',
  COMPLETED: 'COMPLETED',
  OPTIONAL: 'OPTIONAL',
});

export const JOURNEY_STATUS_VIEW = Object.freeze({
  NOT_STARTED: { label: '시작 전', tone: 'neutral' },
  READY: { label: '시작 가능', tone: 'info' },
  IN_PROGRESS: { label: '진행 중', tone: 'info' },
  NEEDS_INPUT: { label: '입력 필요', tone: 'warning' },
  ATTENTION: { label: '확인 필요', tone: 'danger' },
  STALE: { label: '업데이트 필요', tone: 'warning' },
  COMPLETED: { label: '완료', tone: 'success' },
  OPTIONAL: { label: '선택 기능', tone: 'neutral' },
});

export const PROJECT_JOURNEYS = Object.freeze([
  { id: 'planning', label: '1. 사업 기획', shortLabel: '사업 기획', moduleIds: ['idea', 'concepts'] },
  { id: 'validation', label: '2. 사업 검증', shortLabel: '사업 검증',
    // 세 걸음이다: 무엇이 관측됐나 → 그 사업이 서나 → 그래서 사업안을 어떻게 고칠까.
    moduleIds: ['market', 'businessModel', 'conceptRefinement'] },
  // moduleIds 는 «화면에 자식 카드로 보일 것», statusModuleIds 는 «상태를 정하는 것».
  // 출시 준비는 자식 카드를 펼치지 않지만(전용 페이지 하나로 간다) 상태는 세 분석이 정한다.
  { id: 'launch', label: '3. 출시 준비', shortLabel: '출시 준비', moduleIds: [], optional: true,
    statusModuleIds: ['techOps', 'finance', 'launchReadiness'] },
  { id: 'interview', label: '4. 시장 인터뷰', shortLabel: '시장 인터뷰', moduleIds: ['marketInterview'] },
  { id: 'marketingStrategy', label: '5. 마케팅 전략', shortLabel: '마케팅 전략', moduleIds: ['marketing'] },
  { id: 'finalReport', label: '6. 최종 보고서', shortLabel: '최종 보고서', moduleIds: [], optional: true,
    statusModuleIds: ['finalReport'] },
]);

const PATH_TO_JOURNEY = Object.freeze({
  overview: 'overview', idea: 'planning', concepts: 'planning', market: 'validation',
  'business-model': 'validation', 'concept-refinement': 'validation', 'launch-readiness': 'launch', technology: 'launch', operations: 'launch', 'tech-ops': 'launch', finance: 'launch',
  'market-interview': 'interview', marketing: 'marketingStrategy', 'final-report': 'finalReport',
});

export function getJourneyStatusView(status) {
  return JOURNEY_STATUS_VIEW[status] ?? JOURNEY_STATUS_VIEW.NOT_STARTED;
}

export function getJourneyActionView(status) {
  return ({
    [JOURNEY_STATUS.NOT_STARTED]: '시작하기',
    [JOURNEY_STATUS.READY]: '시작하기',
    [JOURNEY_STATUS.IN_PROGRESS]: '계속하기',
    [JOURNEY_STATUS.NEEDS_INPUT]: '입력하기',
    [JOURNEY_STATUS.ATTENTION]: '확인하기',
    [JOURNEY_STATUS.STALE]: '업데이트하기',
    [JOURNEY_STATUS.COMPLETED]: '결과 보기',
    [JOURNEY_STATUS.OPTIONAL]: '열기',
  })[status] ?? '확인하기';
}

export function aggregateJourneyStatus(moduleStatuses = []) {
  const statuses = moduleStatuses.map((item) => typeof item === 'string' ? item : item?.status).filter(Boolean);
  if (statuses.length === 0) return JOURNEY_STATUS.NOT_STARTED;
  if (statuses.includes(MODULE_STATUS.NEEDS_INPUT)) return JOURNEY_STATUS.NEEDS_INPUT;
  if (statuses.includes(MODULE_STATUS.FAILED)) return JOURNEY_STATUS.ATTENTION;
  if (statuses.includes(MODULE_STATUS.STALE)) return JOURNEY_STATUS.STALE;
  if (statuses.some((status) => [MODULE_STATUS.RUNNING, MODULE_STATUS.QUEUED].includes(status))) return JOURNEY_STATUS.IN_PROGRESS;
  if (statuses.every((status) => status === MODULE_STATUS.COMPLETED)) return JOURNEY_STATUS.COMPLETED;
  if (statuses.some((status) => status === MODULE_STATUS.COMPLETED)) return JOURNEY_STATUS.IN_PROGRESS;
  if (statuses.every((status) => status === MODULE_STATUS.READY)) return JOURNEY_STATUS.READY;
  if (statuses.some((status) => status === MODULE_STATUS.READY)) return JOURNEY_STATUS.READY;
  return JOURNEY_STATUS.NOT_STARTED;
}

export function getJourneyByPath(pathname) {
  const segments = pathname.replace(/\/+$/, '').split('/');
  const routeSegment = segments.includes('launch-readiness') ? 'launch-readiness'
    : ['compare', 'legal-report'].includes(segments.at(-1)) ? 'concepts' : segments.at(-1);
  const id = PATH_TO_JOURNEY[routeSegment] ?? 'overview';
  return id === 'overview' ? { id: 'overview', label: '프로젝트 개요', shortLabel: '프로젝트 개요', moduleIds: [] }
    : PROJECT_JOURNEYS.find((journey) => journey.id === id);
}

export function getProjectJourneys(projectId, modules = []) {
  return PROJECT_JOURNEYS.map((journey) => {
    const sourceChildren = (journey.statusModuleIds ?? journey.moduleIds)
      .map((id) => modules.find((module) => module.id === id)).filter(Boolean);
    // 선택 단계라고 상태를 OPTIONAL 로 «고정» 하면, 사용자가 그 단계를 끝내도 완료가 영영 안 뜬다.
    // 아직 손대지 않았을 때만 「선택 기능」이고, 한 번이라도 움직였으면 실제 상태를 보여 준다.
    const aggregated = aggregateJourneyStatus(sourceChildren);
    const status = journey.optional && untouched(sourceChildren) ? JOURNEY_STATUS.OPTIONAL : aggregated;
    const children = journey.id === 'launch' ? [] : sourceChildren;
    return {
      ...journey,
      children,
      status,
      href: journey.id === 'finalReport' ? projectRoutes.finalReport(projectId) : getJourneyEntryRoute(projectId, journey, children),
    };
  });
}

/**
 * 「아직 손대지 않았다」의 정의.
 *
 * <p>기본 상태(NOT_READY·READY·NOT_CONNECTED)만 있으면 사용자가 아무것도 하지 않은 것이다.
 * 하나라도 대기·진행·완료·실패·낡음으로 움직였다면 그 단계는 시작된 것이므로
 * 「선택 기능」으로 덮지 않는다.
 */
function untouched(moduleStatuses = []) {
  const idle = [MODULE_STATUS.NOT_READY, MODULE_STATUS.READY, MODULE_STATUS.NOT_CONNECTED];
  return moduleStatuses.every((item) => idle.includes(typeof item === 'string' ? item : item?.status));
}

export function getJourneyEntryRoute(projectId, journey, children = []) {
  if (journey.id === 'finalReport') return projectRoutes.finalReport(projectId);
  if (journey.id === 'launch') return projectRoutes.launchReadiness(projectId);
  const next = children.find((module) => module.status !== MODULE_STATUS.COMPLETED) ?? children.at(-1);
  return next?.href ?? projectRoutes.overview(projectId);
}

export function getJourneyProgress(journeys = []) {
  // ⚠ 분모는 `optional` «플래그» 다. 상태로 세면 선택 단계를 끝낸 순간 필수 단계 수가 늘어난다.
  const required = journeys.filter((journey) => !journey.optional);
  return { completed: required.filter((journey) => journey.status === JOURNEY_STATUS.COMPLETED).length, total: required.length };
}
