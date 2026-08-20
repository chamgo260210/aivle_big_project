export const ADMIN_STATUS_LABELS = {
  ACTIVE: '활성', LOCKED: '잠김', DISABLED: '비활성', SUSPENDED: '정지', PENDING: '대기', WITHDRAWN: '탈퇴',
  USER: '일반 사용자', ADMIN: '관리자', SUCCESS: '성공', FAILED: '실패',
  DRAFT: '작성 중', PAUSED: '일시 정지', COMPLETED: '완료', ARCHIVED: '보관됨',
  QUEUED: '대기', READY: '실행 준비', RUNNING: '실행 중', SUCCEEDED: '성공', CANCELLED: '취소',
  TIMED_OUT: '시간 초과', NEEDS_INPUT: '입력 필요', NOT_READY: '준비 전', NOT_CONNECTED: '연결 안 됨',
  STALE: '업데이트 필요', AVAILABLE: '사용 가능', UNAVAILABLE: '사용 불가', CONFIGURED: '설정 완료',
  NOT_CONFIGURED: '설정 필요', UNKNOWN: '확인 필요',
};

export const PROJECT_STATUS_LABELS = {
  DRAFT: '작성 중', ACTIVE: '진행 중', PAUSED: '일시 정지', COMPLETED: '완료', ARCHIVED: '보관됨',
};

export const ADMIN_MODULE_LABELS = {
  IDEA: '사업 아이디어', CONCEPT_PORTFOLIO: '사업안 구성', MARKET_ANALYSIS: '시장 분석',
  BUSINESS_MODEL: '사업 모델', CONCEPT_REFINEMENT: '컨셉 다듬기', TECH_OPS: '기술·운영 분석',
  FINANCE: '재무 분석', LAUNCH_READINESS: '출시 준비', MARKET_INTERVIEW: '시장 인터뷰',
  MARKETING: '마케팅 전략', FINAL_REPORT: '최종 사업기획서',
};

export const ADMIN_TASK_TYPE_LABELS = {
  IDEA_ATTACHMENT_PARSE: '첨부 자료 분석', IDEA_BRIEF_DERIVATION: '아이디어 브리프 구성',
  CONCEPT_PORTFOLIO_V2_RUN: '사업안 후보 생성', CONCEPT_PORTFOLIO_V2_CONTINUE: '사업안 후보 추가 생성',
  CONCEPT_PORTFOLIO_V2_SELECTION_ACTION: '사업안 선택 반영', CONCEPT_FACTORY_RUN: '사업안 생성',
  CONCEPT_CANDIDATE: '사업안 후보 생성', CONCEPT_DISTINCTNESS_JUDGE: '사업안 차별성 검토',
  CONCEPT_LEGAL_REVIEW: '사업안 법률 검토', CONCEPT_REDESIGN: '사업안 재설계',
  CONCEPT_HYPOTHESIS_ALTERNATIVE: '가설 대안 생성', CONCEPT_DELTA_LEGAL_REVIEW: '변경안 법률 검토',
  TECH_OPS_PROPOSAL: '기술·운영 제안', TECH_OPS_ADVISORY: '기술·운영 자문',
  FINANCE_ESTIMATE: '재무 추정', FINANCE_ANALYSIS_REPORT: '재무 분석 보고서',
  LAUNCH_TECHNOLOGY_READINESS: '기술 출시 준비 분석', LAUNCH_OPERATIONS_READINESS: '운영 출시 준비 분석',
  LAUNCH_READINESS: '출시 준비 종합 분석', MARKETING_CONTENT_GENERATION: '마케팅 콘텐츠 생성',
  MARKETING_STRATEGY_GENERATION: '마케팅 전략 생성', FINAL_BUSINESS_PROPOSAL_GENERATION: '최종 사업기획서 생성',
  FINAL_BUSINESS_PROPOSAL_REVIEW: '최종 사업기획서 검토', MARKETING_VISUAL_GENERATION: '마케팅 이미지 생성',
  MARKET_RESEARCH: '시장 조사', BUSINESS_VALIDATION: '사업 검증', MARKET_INTERVIEW: '시장 인터뷰',
  TWIN_SURVEY: '가상 고객 조사', TWIN_STIMULUS_DRAFT: '가상 고객 조사 자료 생성',
};

export const ADMIN_TARGET_TYPE_LABELS = {
  USER: '사용자', PROJECT: '프로젝트', SERVICE_SETTING: '서비스 설정', ADMIN_AUTH: '관리자 인증',
  REFRESH_TOKEN: '로그인 세션', OTHER: '기타',
};

export const ADMIN_ERROR_LABELS = {
  AI_SERVICE_UNAVAILABLE: 'AI 서비스 연결 실패', AI_CONFIGURATION_INVALID: 'AI 설정 오류',
  AI_RESULT_INVALID: 'AI 결과 형식 오류', INVALID_REQUEST: '요청 형식 오류',
  FIELD_CONSTRAINT_VIOLATION: '입력 조건 불일치', EXTERNAL_AI_SERVICE_UNAVAILABLE: '외부 AI 서비스 연결 실패',
};

export function adminStatusLabel(value) {
  const normalized = String(value || 'UNKNOWN').toUpperCase();
  return ADMIN_STATUS_LABELS[normalized] || normalized;
}

export function projectStatusLabel(value) {
  return PROJECT_STATUS_LABELS[value] || adminStatusLabel(value);
}

export function adminTaskTypeLabel(value) {
  return ADMIN_TASK_TYPE_LABELS[value] || value || '알 수 없는 작업';
}

export function adminModuleLabel(value) {
  return ADMIN_MODULE_LABELS[value] || value || '알 수 없는 단계';
}

export function adminTargetTypeLabel(value) {
  return ADMIN_TARGET_TYPE_LABELS[value] || value || '기타';
}

export function adminErrorLabel(value) {
  return ADMIN_ERROR_LABELS[value] || value || '오류 없음';
}
