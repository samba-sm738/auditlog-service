package com.persistent.assessment.auditlog.scheduler;

import com.persistent.assessment.auditlog.service.ArchiveEventsService;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers archival of expired audit events.
 *
 * <p>Intentionally thin: all retention and archival logic lives in
 * {@link ArchiveEventsService}; this class only decides when to run.
 * {@link EnableScheduling} is declared here so the feature stays self-contained.
 */
@Component
@EnableScheduling
public class ArchiveEventsScheduler {

	private final ArchiveEventsService archiveEventsService;

	public ArchiveEventsScheduler(ArchiveEventsService archiveEventsService) {
		this.archiveEventsService = archiveEventsService;
	}

	@Scheduled(
			fixedDelayString = "${audit.events.archive-interval-ms:86400000}",
			initialDelayString = "${audit.events.archive-initial-delay-ms:60000}")
	public void archiveExpiredEvents() {
		archiveEventsService.archiveEventsOlderThanRetention();
	}

}
