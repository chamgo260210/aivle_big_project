package com.aivle.backend.admin;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServicePolicyService {
    private final ServiceSettingRepository settings;
    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() { return enabled(ServiceSettingKey.REGISTRATION_ENABLED); }
    @Transactional(readOnly = true)
    public boolean isDocumentProcessingEnabled() { return enabled(ServiceSettingKey.DOCUMENT_PROCESSING_ENABLED); }
    @Transactional(readOnly = true)
    public boolean isMaintenanceMode() { return enabled(ServiceSettingKey.MAINTENANCE_MODE); }
    public void requireRegistrationEnabled() { if (!isRegistrationEnabled()) throw new BusinessException(ErrorCode.REGISTRATION_DISABLED); }
    public void requireDocumentProcessingEnabled() { if (!isDocumentProcessingEnabled()) throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING_DISABLED); }
    public void requireServiceAvailableForUser() { if (isMaintenanceMode()) throw new BusinessException(ErrorCode.MAINTENANCE_MODE_ENABLED); }
    private boolean enabled(ServiceSettingKey key) {
        String value = settings.findById(key.name()).map(ServiceSetting::getSettingValue)
            .or(() -> settings.findById(key.legacyKey()).map(ServiceSetting::getSettingValue))
            .orElse(key.defaultValue());
        return Boolean.parseBoolean(value);
    }
}
