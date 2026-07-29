package com.aivle.backend.admin;

import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final Clock jobClock;
    @Transactional(readOnly = true)
    public AdminController.OverviewResponse overview() {
        long active = users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.USER, UserStatus.ACTIVE)
            + users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE);
        long locked = users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.USER, UserStatus.LOCKED)
            + users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.LOCKED);
        long activeProjects = projects.countByStatusInAndDeletedAtIsNull(List.of(ProjectStatus.DRAFT, ProjectStatus.ACTIVE, ProjectStatus.PAUSED));
        return new AdminController.OverviewResponse(
            new AdminController.UserMetrics(users.count(), active, locked),
            new AdminController.ProjectMetrics(projects.count(), activeProjects, projects.countByStatusAndDeletedAtIsNull(ProjectStatus.COMPLETED)),
            new AdminController.JobMetrics(0, 0, 0, false), LocalDateTime.now(jobClock));
    }
}
