package com.aivle.backend.auth.dto;

import com.aivle.backend.user.entity.User;

public record UserResponse(
    Long id,
    String email,
    String displayName,
    String role,
    String status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole().name(),
            user.getStatus().name()
        );
    }
}
