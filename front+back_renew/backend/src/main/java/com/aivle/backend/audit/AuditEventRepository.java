package com.aivle.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findAllByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    List<AuditEvent> findAllByProjectIdOrderByOccurredAtDesc(Long projectId);
}
