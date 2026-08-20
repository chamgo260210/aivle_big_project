package com.aivle.backend.auth.dto;

import com.aivle.backend.common.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record ReviewQuickAccessRequest(@NotNull UserRole role) {
}
