package com.aivle.backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorUserId;
    private Long projectId;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(length = 60)
    private String aggregateType;

    private Long aggregateId;

    @Column(length = 100)
    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private AuditEvent(
        Long actorUserId,
        Long projectId,
        AuditEventType eventType,
        String aggregateType,
        Long aggregateId,
        String requestId,
        String metadataJson,
        LocalDateTime occurredAt
    ) {
        this.actorUserId = actorUserId;
        this.projectId = projectId;
        this.eventType = eventType.name();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.requestId = requestId;
        this.metadataJson = metadataJson;
        this.occurredAt = occurredAt;
    }

    public static AuditEvent record(
        Long actorUserId,
        Long projectId,
        AuditEventType eventType,
        String aggregateType,
        Long aggregateId,
        String requestId,
        String metadataJson,
        LocalDateTime occurredAt
    ) {
        return new AuditEvent(
            actorUserId,
            projectId,
            eventType,
            aggregateType,
            aggregateId,
            requestId,
            metadataJson,
            occurredAt
        );
    }
}
