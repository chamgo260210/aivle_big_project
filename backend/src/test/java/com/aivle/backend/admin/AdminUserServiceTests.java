package com.aivle.backend.admin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class AdminUserServiceTests {
    @Test
    void blankKeywordUsesTypedEmptyStringForPostgresSearch() {
        UserRepository users = mock(UserRepository.class);
        var pageable = PageRequest.of(0, 20);
        when(users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
            .thenReturn(1L);
        when(users.searchAdminUsers("", null, null, pageable)).thenReturn(Page.empty(pageable));
        AdminUserService service = new AdminUserService(
            users,
            mock(ProjectRepository.class),
            mock(com.aivle.backend.auth.RefreshTokenRepository.class),
            mock(AdminAuditService.class),
            mock(AdminReauthenticationService.class),
            Clock.systemUTC()
        );

        service.list(null, null, null, pageable);

        verify(users).searchAdminUsers(eq(""), isNull(), isNull(), eq(pageable));
    }
}
