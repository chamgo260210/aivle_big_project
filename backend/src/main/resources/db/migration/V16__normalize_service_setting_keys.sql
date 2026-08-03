INSERT INTO service_settings (setting_key, setting_value, updated_by, updated_at)
SELECT 'REGISTRATION_ENABLED', setting_value, updated_by, updated_at
FROM service_settings legacy
WHERE legacy.setting_key = 'registrationEnabled'
  AND NOT EXISTS (
      SELECT 1 FROM service_settings canonical
      WHERE canonical.setting_key = 'REGISTRATION_ENABLED'
  );

INSERT INTO service_settings (setting_key, setting_value, updated_by, updated_at)
SELECT 'DOCUMENT_PROCESSING_ENABLED', setting_value, updated_by, updated_at
FROM service_settings legacy
WHERE legacy.setting_key = 'documentProcessingEnabled'
  AND NOT EXISTS (
      SELECT 1 FROM service_settings canonical
      WHERE canonical.setting_key = 'DOCUMENT_PROCESSING_ENABLED'
  );

INSERT INTO service_settings (setting_key, setting_value, updated_by, updated_at)
SELECT 'MAINTENANCE_MODE', setting_value, updated_by, updated_at
FROM service_settings legacy
WHERE legacy.setting_key = 'maintenanceMode'
  AND NOT EXISTS (
      SELECT 1 FROM service_settings canonical
      WHERE canonical.setting_key = 'MAINTENANCE_MODE'
  );

DELETE FROM service_settings
WHERE setting_key IN (
    'registrationEnabled',
    'documentProcessingEnabled',
    'maintenanceMode'
);
