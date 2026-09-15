package com.persistent.assessment.auditlog.repository;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventJpaRepository extends JpaRepository<AuditEvent, UUID> {

	Optional<AuditEvent> findById(UUID id);

    Page<AuditEvent> findAll(Pageable pageable);

    Page<AuditEvent> findBySequenceNumberGreaterThan(
            long sequenceNumber,
            Pageable pageable
    );

    Page<AuditEvent> findByActorId(
            String actorId,
            Pageable pageable
    );

    Page<AuditEvent> findByEventType(
            String eventType,
            Pageable pageable
    );

    Page<AuditEvent> findByResourceTypeAndResourceId(
            String resourceType,
            String resourceId,
            Pageable pageable
    );

    Page<AuditEvent> findByEventTimestampBetween(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

}
