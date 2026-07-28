import {
  getProjectArea,
  getProjectBasePath,
  getProjectNextAction,
  getProjectStatusView,
} from './projectWorkflowModel.js';

const STAGE_LABEL = {
  DOCUMENT: '사업계획 입력',
  STRUCTURING: '정보 구조화',
  LEGAL_REVIEW: '법률·규제 검토',
  FEASIBILITY: '사업 타당성 분석',
  FINANCIAL: '재무·수익성 분석',
  PERSONA_CONFIGURATION: 'AI 패널 검증',
  PANEL_SURVEY: 'AI 패널 조사',
  PANEL_DISCUSSION: 'AI 패널 토론',
  REPORT: '통합 보고서',
  COMPLETED: '통합 보고서',
};

export function toProjectViewModel(project) {
  const viewModel = {
    projectId: String(project.id),
    name: project.title,
    description: project.description ?? '',
    industryCategory: project.industryCategory ?? '',
    status: project.status,
    stage: project.stage,
    createdAt: project.createdAt,
    updatedAt: project.updatedAt ?? project.createdAt,
    version: project.version,
  };
  const statusView = getProjectStatusView(viewModel.status);
  const nextAction = getProjectNextAction(viewModel);
  return {
    ...viewModel,
    statusLabel: statusView.label,
    statusTone: statusView.tone,
    stageLabel: STAGE_LABEL[viewModel.stage] ?? '단계 확인 필요',
    area: getProjectArea(viewModel),
    nextAction,
    nextRoute: nextAction.route.replace(`${getProjectBasePath(viewModel.projectId)}/`, ''),
  };
}

export function formatProjectDate(value) {
  if (!value) return '수정 시각 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '수정 시각 확인 필요';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
