package com.aivle.backend.auth;

import com.aivle.backend.auth.dto.AuthResponse;
import com.aivle.backend.auth.dto.SignupResponse;
import com.aivle.backend.auth.dto.LoginRequest;
import com.aivle.backend.auth.dto.LogoutRequest;
import com.aivle.backend.auth.dto.RefreshRequest;
import com.aivle.backend.auth.dto.SignupRequest;
import com.aivle.backend.auth.dto.TokenPairResponse;
import com.aivle.backend.auth.dto.UserResponse;
import com.aivle.backend.auth.dto.UpdateProfileRequest;
import com.aivle.backend.auth.dto.ChangePasswordRequest;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
        @Valid @RequestBody SignupRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        SignupResponse response = authService.signup(
            request.username(),
            request.password(),
            request.displayName(),
            request.email(), request.organizationName(), request.departmentName(), request.jobTitle(),
            requestId
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, requestId));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        return ApiResponse.success(authService.login(
            request.username(),
            request.password(),
            servletRequest.getRemoteAddr(),
            requestId
        ), requestId);
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<TokenPairResponse> refresh(
        @Valid @RequestBody RefreshRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        return ApiResponse.success(
            authService.refresh(request.refreshToken(), requestId),
            requestId
        );
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(
        @Valid @RequestBody LogoutRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        authService.logout(
            currentUserProvider.currentUserId(),
            request.refreshToken(),
            requestId
        );
        return ApiResponse.success(null, requestId);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> me(HttpServletRequest servletRequest) {
        String requestId = requestId(servletRequest);
        return ApiResponse.success(
            authService.me(currentUserProvider.currentUserId()),
            requestId
        );
    }

    @PatchMapping("/users/me")
    public ApiResponse<UserResponse> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        return ApiResponse.success(
            authService.updateProfile(currentUserProvider.currentUserId(), request, requestId),
            requestId
        );
    }

    @PostMapping("/users/me/password")
    public ApiResponse<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestId = requestId(servletRequest);
        authService.changePassword(currentUserProvider.currentUserId(), request, requestId);
        return ApiResponse.success(null, requestId);
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }
}
