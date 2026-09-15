package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveEventsService {

	private static final Logger log = LoggerFactory.getLogger(ArchiveEventsService.class);

	/** Precision of the timestamp columns, i.e. {@code TIMESTAMP(6) WITH TIME ZONE}. */
	private static final ChronoUnit TIMESTAMP_PRECISION = ChronoUnit.MICROS;

	private final AuditEventRepository eventRepository;

	private final long retentionDays;

	private final Clock clock;

	@Autowired
	public ArchiveEventsService(AuditEventRepository eventRepository,
			@Value("${audit.events.retention-days:30}") long retentionDays) {
		this(eventRepository, retentionDays, Clock.systemUTC());
	}

	ArchiveEventsService(AuditEventRepository eventRepository, long retentionDays, Clock clock) {
		this.eventRepository = eventRepository;
		this.retentionDays = retentionDays;
		this.clock = clock;
	}

	/**
	 * Archives every audit event whose {@code created_at} is older than the configured
	 * retention period, i.e. {@code created_at < now - retentionDays}.
	 *
	 * <p>This service defines no separate archive store: retention is enforced by removing
	 * expired rows with one bulk {@code DELETE}, so the {@code created_at} comparison runs in
	 * the database and no records are loaded into memory. The whole archival runs in a single
	 * transaction.
	 *
	 * @return the number of archived records
	 */
	@Transactional
	public int archiveEventsOlderThanRetention() {
		OffsetDateTime cutoff = OffsetDateTime.now(clock)
				.minusDays(retentionDays)
				.truncatedTo(TIMESTAMP_PRECISION);

		int archived = eventRepository.deleteEventsCreatedBefore(cutoff);

		log.info("Archived {} audit event(s) created before {}", archived, cutoff);

		return archived;
	}

}
