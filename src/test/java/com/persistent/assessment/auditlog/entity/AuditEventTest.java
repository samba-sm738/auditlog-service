package com.persistent.assessment.auditlog.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuditEventTest {

	@Test
	void gettersAndSettersRoundTripAllFields() {
		UUID id = UUID.randomUUID();
		OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-14T10:00:00Z");
		OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-14T10:00:01Z");
		Map<String, Object> payload = Map.of("field", "address");

		AuditEvent event = new AuditEvent();
		event.setId(id);
		event.setSequenceNumber(7L);
		event.setEventType("USER_LOGIN");
		event.setActorId("user-1");
		event.setResourceType("Customer");
		event.setResourceId("cust-1");
		event.setPayload(payload);
		event.setEventTimestamp(timestamp);
		event.setPreviousHash("0".repeat(64));
		event.setContentHash("a".repeat(64));
		event.setCreatedAt(createdAt);

		assertThat(event.getId()).isEqualTo(id);
		assertThat(event.getSequenceNumber()).isEqualTo(7L);
		assertThat(event.getEventType()).isEqualTo("USER_LOGIN");
		assertThat(event.getActorId()).isEqualTo("user-1");
		assertThat(event.getResourceType()).isEqualTo("Customer");
		assertThat(event.getResourceId()).isEqualTo("cust-1");
		assertThat(event.getPayload()).isEqualTo(payload);
		assertThat(event.getEventTimestamp()).isEqualTo(timestamp);
		assertThat(event.getPreviousHash()).isEqualTo("0".repeat(64));
		assertThat(event.getContentHash()).isEqualTo("a".repeat(64));
		assertThat(event.getCreatedAt()).isEqualTo(createdAt);
	}

}
