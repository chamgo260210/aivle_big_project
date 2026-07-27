package com.aivle.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    String password,

    @NotBlank
    @Size(max = 50)
    String displayName
) {
    public SignupRequest {
        email = email == null ? null : email.trim();
    }
}
