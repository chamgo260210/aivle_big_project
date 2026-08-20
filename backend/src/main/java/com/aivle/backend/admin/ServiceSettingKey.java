package com.aivle.backend.admin;

public enum ServiceSettingKey {
    REGISTRATION_ENABLED(
        "true",
        "신규 회원가입",
        "새로운 사용자의 회원가입 허용 여부를 설정합니다."
    ),
    MAINTENANCE_MODE(
        "false",
        "서비스 점검 모드",
        "조회와 관리자 기능은 유지하면서 일반 사용자의 변경 작업을 일시 중지합니다."
    );

    private final String defaultValue;
    private final String displayName;
    private final String description;

    ServiceSettingKey(String defaultValue, String displayName, String description) {
        this.defaultValue = defaultValue;
        this.displayName = displayName;
        this.description = description;
    }

    public String defaultValue() { return defaultValue; }
    public String displayName() { return displayName; }
    public String description() { return description; }
}
