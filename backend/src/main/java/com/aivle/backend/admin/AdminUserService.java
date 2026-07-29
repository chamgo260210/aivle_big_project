package com.aivle.backend.admin;

import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.auth.RefreshTokenRepository;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final RefreshTokenRepository refreshTokens;
    private final DomainAuditService audits;
    private final Clock jobClock;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(String keyword, UserRole role, UserStatus status, Pageable pageable) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return users.searchAdminUsers(normalized, role, status, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse detail(Long userId) { return response(find(userId)); }

    @Transactional
    public AdminUserResponse changeStatus(User actor, Long userId, UserStatus status, String reason, String requestId) {
        User target = find(userId);
        if (actor.getId().equals(target.getId()) && status != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.SELF_ADMIN_ACCOUNT_CHANGE_NOT_ALLOWED);
        if (target.getStatus() == status) {
            if (status == UserStatus.LOCKED) throw new BusinessException(ErrorCode.USER_ALREADY_LOCKED);
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (target.getRole() == UserRole.ADMIN && target.getStatus() == UserStatus.ACTIVE
            && status != UserStatus.ACTIVE && activeAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_ADMIN_REQUIRED);
        }
        UserStatus before = target.getStatus();
        target.updateStatus(status, reason.trim(), LocalDateTime.now(jobClock));
        target.advanceSecurityVersion();
        revoke(target.getId());
        audits.record(actor.getId(), null, AuditEventType.ADMIN_USER_STATUS_CHANGED, "USER", target.getId(), requestId,
            Map.of("before", before.name(), "after", status.name(), "reason", reason.trim(), "targetUserId", target.getId().toString()));
        return response(target);
    }

    @Transactional
    public AdminUserResponse changeRole(User actor, Long userId, UserRole role, String reason, String requestId) {
        User target = find(userId);
        if (actor.getId().equals(target.getId()) && target.getRole() == UserRole.ADMIN && role != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.SELF_ADMIN_ROLE_CHANGE_NOT_ALLOWED);
        }
        if (target.getRole() == role) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (target.getRole() == UserRole.ADMIN && target.getStatus() == UserStatus.ACTIVE && role != UserRole.ADMIN && activeAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_ADMIN_REQUIRED);
        }
        UserRole before = target.getRole();
        target.updateRole(role, actor.getId(), LocalDateTime.now(jobClock));
        target.advanceSecurityVersion();
        revoke(target.getId());
        audits.record(actor.getId(), null, AuditEventType.ADMIN_USER_ROLE_CHANGED, "USER", target.getId(), requestId,
            Map.of("before", before.name(), "after", role.name(), "reason", reason.trim(), "targetUserId", target.getId().toString()));
        return response(target);
    }

    @Transactional
    public void revokeSessions(User actor, Long userId, String reason, String requestId) {
        if (actor.getId().equals(userId)) throw new BusinessException(ErrorCode.SELF_SESSION_REVOKE_NOT_ALLOWED);
        find(userId);
        users.findById(userId).ifPresent(User::advanceSecurityVersion);
        revoke(userId);
        audits.record(actor.getId(), null, AuditEventType.ADMIN_USER_SESSIONS_REVOKED, "USER", userId, requestId,
            Map.of("reason", reason.trim(), "targetUserId", userId.toString()));
    }

    private void revoke(Long userId) {
        LocalDateTime now = LocalDateTime.now(jobClock);
        refreshTokens.findAllByUserIdAndDeletedAtIsNull(userId).forEach(token -> token.revoke(now));
    }
    private java.util.List<User> activeAdminsForUpdate() { return users.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE); }
    private User find(Long userId) { return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
    private AdminUserResponse response(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getName(), user.getRole().name(),
            user.getStatus().name(), projects.countByOwnerIdAndDeletedAtIsNull(user.getId()), user.getLastLoginAt(), user.getCreatedAt(),
            user.getLockedAt(), user.getLockedReason(), user.getDisabledAt());
    }

    public record AdminUserResponse(Long id, String username, String email, String displayName, String role, String accountStatus,
                                    long projectCount, LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime lockedAt,
                                    String lockedReason, LocalDateTime disabledAt) { }
}
