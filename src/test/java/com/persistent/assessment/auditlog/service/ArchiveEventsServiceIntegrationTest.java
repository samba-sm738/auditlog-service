package com.persistent.assessment.auditlog.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that archival filters on {@code created_at} in the database and honours
 * the {@code audit.events.retention-days} configured in {@code application.yaml}.
 */
@SpringBootTest
class ArchiveEventsServiceIntegrationTest {

	@Autowired
	private ArchiveEventsService archiveEventsService;

	@Autowired
	private AuditEventRepository eventRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetAuditLog() {
		jdbcTemplate.execute("DELETE FROM audit_events");
	}

	@Test
	void archivesOnlyEventsOlderThanTheConfiguredRetentionPeriod() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.MICROS);

		// The default retention is 30 days: straddle that boundary.
		AuditEvent expired = event(1L, now.minusDays(31));
		AuditEvent recent = event(2L, now.minusDays(29));
		eventRepository.saveAll(List.of(expired, recent));

		int archived = archiveEventsService.archiveEventsOlderThanRetention();

		assertThat(archived).isEqualTo(1);
		assertThat(eventRepository.findAll())
				.extracting(AuditEvent::getId)
				.containsExactly(recent.getId());
	}

	@Test
	void leavesAYoungLogUntouched() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.MICROS);

		eventRepository.saveAll(List.of(
				event(1L, now.minusHours(1)),
				event(2L, now)));

		int archived = archiveEventsService.archiveEventsOlderThanRetention();

		assertThat(archived).isZero();
		assertThat(eventRepository.findAll()).hasSize(2);
	}

	private AuditEvent event(long sequenceNumber, OffsetDateTime createdAt) {
		AuditEvent event = new AuditEvent();
		event.setId(UUID.randomUUID());
		event.setSequenceNumber(sequenceNumber);
		event.setEventType("USER_LOGIN");
		event.setActorId("user-" + sequenceNumber);
		event.setResourceType("Customer");
		event.setResourceId("cust-" + sequenceNumber);
		event.setPayload(Map.of("field", "address"));
		event.setEventTimestamp(createdAt);
		event.setPreviousHash("0".repeat(64));
		event.setContentHash(String.format("%064d", sequenceNumber));
		event.setCreatedAt(createdAt);
		return event;
	}

}
