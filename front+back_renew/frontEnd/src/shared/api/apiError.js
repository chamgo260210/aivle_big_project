export class ApiError extends Error {
  constructor({
    status = 0,
    code = 'NETWORK_ERROR',
    message = '요청을 처리하지 못했습니다.',
    fieldErrors = [],
    retryable = false,
    requestId = null,
    cause,
  } = {}) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryable = retryable;
    this.requestId = requestId;
  }
}

export function normalizeApiError(error, fallback = {}) {
  if (error instanceof ApiError) return error;
  if (error?.name === 'AbortError') {
    return new ApiError({
      status: 0,
      code: 'REQUEST_ABORTED',
      message: '요청이 취소되었거나 제한 시간을 초과했습니다.',
      retryable: true,
      cause: error,
      ...fallback,
    });
  }
  return new ApiError({
    message: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
    retryable: true,
    cause: error,
    ...fallback,
  });
}

const USER_MESSAGE_BY_CODE = {
  VALIDATION_FAILED: '입력한 내용을 다시 확인해 주세요.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  EMAIL_ALREADY_EXISTS: '이미 사용 중인 이메일입니다.',
  USER_EMAIL_DUPLICATED: '이미 사용 중인 이메일입니다.',
  PASSWORD_POLICY_VIOLATION: '비밀번호 정책을 확인해 주세요.',
  ACCESS_TOKEN_EXPIRED: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  ACCESS_TOKEN_INVALID: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  REFRESH_TOKEN_INVALID: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  USER_INACTIVE: '현재 로그인할 수 없는 계정입니다.',
  UNAUTHORIZED: '로그인이 필요합니다.',
  FORBIDDEN: '이 작업을 수행할 권한이 없습니다.',
  NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  CONFLICT: '다른 변경사항이 반영되었습니다. 최신 내용을 확인해 주세요.',
  RESOURCE_VERSION_CONFLICT: '다른 변경사항이 먼저 저장되었습니다. 최신 내용을 확인해 주세요.',
  PLAN_NOT_EDITABLE: '확정된 사업계획은 더 이상 수정할 수 없습니다.',
  PLAN_ALREADY_CONFIRMED: '이미 확정된 사업계획입니다.',
  PLAN_INCOMPLETE: '필수 보완 항목을 모두 해결한 뒤 확정해 주세요.',
  MISSING_FIELD_NOT_FOUND: '보완 항목을 찾을 수 없습니다. 최신 내용을 다시 불러와 주세요.',
  STRUCTURED_PLAN_NOT_FOUND: '최신 구조화 결과를 찾을 수 없습니다.',
  PROJECT_STAGE_INVALID: '현재 프로젝트 단계에서는 이 작업을 진행할 수 없습니다.',
  NETWORK_ERROR: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
  REQUEST_ABORTED: '요청이 취소되었습니다. 다시 시도해 주세요.',
};

export function getUserErrorMessage(error) {
  return USER_MESSAGE_BY_CODE[error?.code] ?? '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}
