import { projectRoutes } from '../routing/projectRoutes.js';

export const MODULE_STATUS = Object.freeze({
  NOT_READY: 'NOT_READY', READY: 'READY', QUEUED: 'QUEUED', RUNNING: 'RUNNING',
  NEEDS_INPUT: 'NEEDS_INPUT', COMPLETED: 'COMPLETED', FAILED: 'FAILED', STALE: 'STALE',
  NOT_CONNECTED: 'NOT_CONNECTED',
});

export const MODULE_STATUS_VIEW = Object.freeze({
  NOT_READY: { label: '준비 전', tone: 'neutral' }, READY: { label: '시작 가능', tone: 'info' },
  QUEUED: { label: '대기 중', tone: 'neutral' }, RUNNING: { label: '진행 중', tone: 'info' },
  NEEDS_INPUT: { label: '입력 필요', tone: 'warning' }, COMPLETED: { label: '완료', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' }, STALE: { label: '갱신 필요', tone: 'warning' },
  NOT_CONNECTED: { label: '연결 준비 중', tone: 'neutral' },
});

export const PROJECT_STATUS_VIEW = Object.freeze({
  DRAFT: { label: '작성 중', tone: 'neutral' }, ACTIVE: { label: '진행 중', tone: 'info' },
  PAUSED: { label: '확인 필요', tone: 'warning' }, COMPLETED: { label: '완료', tone: 'success' },
  ARCHIVED: { label: '보관됨', tone: 'neutral' },
});

export const PROJECT_MODULES = Object.freeze([
  { id: 'overview', label: '프로젝트 개요', shortLabel: '개요', routeKey: 'overview', defaultStatus: MODULE_STATUS.READY },
  { id: 'idea', label: '1. 아이디어', shortLabel: '아이디어', routeKey: 'idea', defaultStatus: MODULE_STATUS.NEEDS_INPUT },
  // 컨셉 생성과 컨셉 비교는 한 화면(BusinessProposalWorkspace)으로 합쳐졌다. 칸은 하나다.
  { id: 'concepts', label: '2. 사업안', shortLabel: '사업안', routeKey: 'concepts', defaultStatus: MODULE_STATUS.NOT_READY },
  // 시장 분석과 BM 분석은 「사업 검증」 한 칸으로 접혔다. id 는 `market` 그대로다 —
  // 백엔드 `PipelineModuleType.MARKET_ANALYSIS` 와 짝이라 이름을 바꾸면 상태 매핑이 조용히 끊긴다.
  { id: 'market', label: '3. 사업 검증', shortLabel: '사업 검증', routeKey: 'market', defaultStatus: MODULE_STATUS.NOT_CONNECTED },
  { id: 'techOps', label: '4. 기술·운영', shortLabel: '기술·운영', routeKey: 'techOps', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'finance', label: '5. 재무', shortLabel: '재무', routeKey: 'finance', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'panelSurvey', label: '6. 시장 인터뷰', shortLabel: '시장 인터뷰', routeKey: 'panelSurvey', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'marketing', label: '7. 마케팅 콘텐츠 제작', shortLabel: '마케팅 콘텐츠', routeKey: 'marketing', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'settings', label: '프로젝트 설정', shortLabel: '설정', routeKey: 'settings', defaultStatus: MODULE_STATUS.READY },
]);

const API_MODULE_IDS = Object.freeze({
  // ⚠ 컨셉 계열 셋이 모두 'concepts' 한 칸으로 접힌다. Object.fromEntries 는 뒤가 이기므로
  //    백엔드가 보내는 순서(PipelineModuleType 열거 순서)의 **마지막** 것이 화면에 남는다.
  IDEA: 'idea', CONCEPT_PORTFOLIO: 'concepts', CONCEPT_FACTORY: 'concepts',
  // ⚠ BUSINESS_MODEL 은 여기 없다. 백엔드 enum 에는 남아 있지만 `findAll()` 이 더는 돌려주지
  //    않는다 — 「사업 검증」 칸의 상태는 MARKET_ANALYSIS 하나가 대표한다.
  CONCEPT_SELECTION: 'concepts', MARKET_ANALYSIS: 'market',
  TECH_OPS: 'techOps', FINANCE: 'finance', PANEL_SURVEY: 'panelSurvey', MARKETING: 'marketing',
});

export function getModuleStatusView(status) { return MODULE_STATUS_VIEW[status] ?? MODULE_STATUS_VIEW.NOT_READY; }
export function getProjectStatusView(status) { return PROJECT_STATUS_VIEW[status] ?? { label: '상태 확인 필요', tone: 'neutral' }; }
export function normalizeProjectModuleStatuses(items) {
  if (!Array.isArray(items)) return {};
  return Object.fromEntries(items.flatMap((item) => {
    const id = API_MODULE_IDS[item?.module];
    if (!id || !MODULE_STATUS[item?.status]) return [];
    return [[id, { ...item, requiredInputs: Array.isArray(item.requiredInputs) ? item.requiredInputs : [] }]];
  }));
}
export function getProjectModules(projectId, statuses = {}) {
  return PROJECT_MODULES.map((module) => {
    const state = statuses[module.id];
    return { ...module, ...(state && typeof state === 'object' ? state : {}), href: projectRoutes[module.routeKey](projectId), status: typeof state === 'string' ? state : state?.status ?? module.defaultStatus };
  });
}
export function getProjectModuleByPath(projectId, pathname, statuses = {}) {
  const modules = getProjectModules(projectId, statuses);
  const normalized = pathname.replace(/\/+$/, '');
  if (normalized === projectRoutes.conceptCompare(projectId)) return modules.find((item) => item.id === 'concepts');
  return modules.find((module) => module.href === normalized) ?? modules[0];
}
