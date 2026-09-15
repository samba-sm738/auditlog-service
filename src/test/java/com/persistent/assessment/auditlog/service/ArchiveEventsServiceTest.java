package com.persistent.assessment.auditlog.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveEventsServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

	@Mock
	private AuditEventRepository eventRepository;

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private ArchiveEventsService service(long retentionDays) {
		return new ArchiveEventsService(eventRepository, retentionDays, clock);
	}

	@Test
	void archivesEventsCreatedBeforeNowMinusRetentionDays() {
		when(eventRepository.deleteEventsCreatedBefore(any(OffsetDateTime.class)))
				.thenReturn(3);

		int archived = service(30).archiveEventsOlderThanRetention();

		assertThat(archived).isEqualTo(3);
		verify(eventRepository).deleteEventsCreatedBefore(
				OffsetDateTime.parse("2026-08-16T12:00:00Z"));
	}

	@Test
	void theConfiguredRetentionPeriodDrivesTheCutoff() {
		when(eventRepository.deleteEventsCreatedBefore(any(OffsetDateTime.class)))
				.thenReturn(0);

		service(7).archiveEventsOlderThanRetention();

		ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(eventRepository).deleteEventsCreatedBefore(cutoff.capture());
		assertThat(cutoff.getValue()).isEqualTo(OffsetDateTime.parse("2026-09-08T12:00:00Z"));
	}

	@Test
	void truncatesTheCutoffToTheTimestampColumnPrecision() {
		Clock nanos = Clock.fixed(Instant.parse("2026-09-15T12:00:00.123456789Z"),
				ZoneOffset.UTC);

		new ArchiveEventsService(eventRepository, 30, nanos).archiveEventsOlderThanRetention();

		ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(eventRepository).deleteEventsCreatedBefore(cutoff.capture());
		assertThat(cutoff.getValue().getNano() % 1_000).isZero();
		assertThat(cutoff.getValue()).isEqualTo(OffsetDateTime.parse("2026-08-16T12:00:00.123456Z"));
	}

	@Test
	void reportsZeroWhenNothingIsOldEnough() {
		when(eventRepository.deleteEventsCreatedBefore(any(OffsetDateTime.class)))
				.thenReturn(0);

		assertThat(service(30).archiveEventsOlderThanRetention()).isZero();
	}

}
