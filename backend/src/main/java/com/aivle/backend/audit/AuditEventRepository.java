package com.aivle.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findAllByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    List<AuditEvent> findAllByProjectIdOrderByOccurredAtDesc(Long projectId);
    Page<AuditEvent> findAllByEventTypeContainingIgnoreCaseOrderByOccurredAtDesc(String eventType, Pageable pageable);
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
