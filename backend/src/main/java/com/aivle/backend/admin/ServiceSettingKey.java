package com.aivle.backend.admin;

public enum ServiceSettingKey {
    REGISTRATION_ENABLED("true", "registrationEnabled"),
    DOCUMENT_PROCESSING_ENABLED("true", "documentProcessingEnabled"),
    MAINTENANCE_MODE("false", "maintenanceMode");

    private final String defaultValue;
    private final String legacyKey;
    ServiceSettingKey(String defaultValue, String legacyKey) { this.defaultValue = defaultValue; this.legacyKey = legacyKey; }
    public String defaultValue() { return defaultValue; }
    public String legacyKey() { return legacyKey; }
}
