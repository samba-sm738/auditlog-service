package com.persistent.assessment.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events", uniqueConstraints = {
		@UniqueConstraint(name = "uq_audit_events_sequence", columnNames = "sequence_number"),
		@UniqueConstraint(name = "uq_audit_events_content_hash", columnNames = "content_hash")
})
@Getter
@Setter
public class AuditEvent {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "sequence_number", nullable = false)
	private Long sequenceNumber;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "actor_id", nullable = false)
	private String actorId;

	@Column(name = "resource_type", nullable = false, length = 100)
	private String resourceType;

	@Column(name = "resource_id", nullable = false)
	private String resourceId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "JSON")
	private Map<String, Object> payload;

	@Column(name = "event_timestamp", nullable = false)
	private OffsetDateTime eventTimestamp;

	@Column(name = "previous_hash", nullable = false, length = 64)
	private String previousHash;

	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

}
