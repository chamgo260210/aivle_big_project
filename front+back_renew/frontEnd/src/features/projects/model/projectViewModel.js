const STATUS_LABEL = {
  DRAFT: '작성 중',
  ACTIVE: '진행 중',
  PAUSED: '일시 중지',
  COMPLETED: '완료',
  ARCHIVED: '보관됨',
};

const STAGE_LABEL = {
  DOCUMENT: '문서 등록',
  STRUCTURING: '사업계획 구조화',
  LEGAL_REVIEW: '법률 검토',
  FEASIBILITY: '사업성 분석',
  FINANCIAL: '재무 분석',
  PERSONA_CONFIGURATION: 'AI 패널 구성',
  PANEL_SURVEY: 'AI 패널 조사',
  PANEL_DISCUSSION: 'AI 패널 토론',
  REPORT: '보고서',
  MARKETING: '마케팅',
  COMPLETED: '완료',
};

const NEXT_ROUTE = {
  DOCUMENT: 'documents',
  STRUCTURING: 'structured-plan',
  LEGAL_REVIEW: 'legal-review',
  FEASIBILITY: 'feasibility',
  FINANCIAL: 'analyses/financial',
  PERSONA_CONFIGURATION: 'personas',
  PANEL_SURVEY: 'panel-survey',
  PANEL_DISCUSSION: 'panel-discussion',
  REPORT: 'reports',
  MARKETING: 'marketing',
  COMPLETED: 'reports',
};

export function toProjectViewModel(project) {
  return {
    projectId: String(project.id),
    name: project.title,
    description: project.description ?? '',
    industryCategory: project.industryCategory ?? '',
    status: project.status,
    statusLabel: STATUS_LABEL[project.status] ?? '상태 확인 필요',
    stage: project.stage,
    stageLabel: STAGE_LABEL[project.stage] ?? '단계 확인 필요',
    createdAt: project.createdAt,
    updatedAt: project.updatedAt ?? project.createdAt,
    nextRoute: NEXT_ROUTE[project.stage] ?? 'overview',
    version: project.version,
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
