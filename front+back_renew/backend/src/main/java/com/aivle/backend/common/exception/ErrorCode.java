package com.aivle.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", false),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요.", false),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "인증 토큰이 올바르지 않습니다.", false),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "갱신 토큰이 올바르지 않습니다.", false),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.", false),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.", false),
    USERNAME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "사용할 수 없는 아이디입니다. 다른 아이디를 입력해 주세요.", false),
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 여러 번 실패했습니다. 잠시 후 다시 시도해 주세요.", false),
    USER_INACTIVE(HttpStatus.UNAUTHORIZED, "현재 로그인할 수 없는 계정입니다.", false),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "비밀번호 정책을 확인해 주세요.", false),
    MISSING_FIELD_NOT_FOUND(HttpStatus.NOT_FOUND, "보완 항목을 찾을 수 없습니다.", false),
    PLAN_NOT_EDITABLE(HttpStatus.CONFLICT, "확정된 구조화 계획은 수정할 수 없습니다.", false),
    PLAN_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "필수 보완 항목을 완료해 주세요.", false),
    PLAN_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 구조화 계획입니다.", false),
    RESOURCE_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 변경이 먼저 저장되었습니다.", true),
    JOB_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 작업은 수동 재시도할 수 없습니다.", false),
    STRUCTURED_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "구조화된 사업계획 결과를 찾을 수 없습니다.", false),
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일이 필요합니다.", false),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.", false),
    FILE_NAME_INVALID(HttpStatus.BAD_REQUEST, "파일 이름이 올바르지 않습니다.", false),
    FILE_STORAGE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "파일을 안전하게 저장하지 못했습니다.", true),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다.", false),
    DOCUMENT_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다.", false),
    DOCUMENT_UPLOAD_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "현재 프로젝트 상태에서는 문서를 업로드할 수 없습니다.", false),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key 형식이 올바르지 않습니다.", false),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "동일한 Idempotency-Key가 다른 요청에 사용되었습니다.", false),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다.", false),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.", false),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요.", false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다.", false),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.", false),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 변경과 충돌했습니다.", true),
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.", false),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.", false),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 프로젝트에 접근할 권한이 없습니다.", false),
    PROJECT_STAGE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "현재 프로젝트 단계에서 수행할 수 없습니다.", false),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 제한을 초과했습니다.", false),
    FILE_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다.", false),
    FILE_SIGNATURE_INVALID(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "파일 내용과 형식이 일치하지 않습니다.", false),
    DOCUMENT_PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "문서 구조화에 실패했습니다.", true),
    STRUCTURED_PLAN_MISSING_REQUIRED_FIELDS(HttpStatus.UNPROCESSABLE_ENTITY, "필수 정보 보완이 필요합니다.", false),
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "동일한 분석이 이미 실행 중입니다.", false),
    ANALYSIS_INPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "분석 입력이 올바르지 않습니다.", false),
    EXTERNAL_AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 일시적으로 연결할 수 없습니다.", true),
    LEGAL_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "법률·규제 사전검토 결과를 찾을 수 없습니다.", false),
    LEGAL_REVIEW_INPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "확정된 사업계획과 프로젝트 단계를 확인해 주세요.", false),
    FEASIBILITY_ASSESSMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "사업 타당성 사전분석 결과를 찾을 수 없습니다.", false),
    FEASIBILITY_INPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "확정된 사업계획과 최신 법률 사전검토 결과를 확인해 주세요.", false),
    PERSONA_RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "페르소나 추천 결과를 찾을 수 없습니다.", false),
    PERSONA_INPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "확정 계획과 최신 타당성 분석 입력을 확인해 주세요.", false),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", true);

    private final HttpStatus httpStatus;
    private final String message;
    private final boolean retryable;
}
