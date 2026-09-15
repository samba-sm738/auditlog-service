package com.persistent.assessment.auditlog.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.persistent.assessment.auditlog.service.ArchiveEventsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArchiveEventsSchedulerTest {

	@Mock
	private ArchiveEventsService archiveEventsService;

	@InjectMocks
	private ArchiveEventsScheduler scheduler;

	@Test
	void delegatesToTheArchiveService() {
		scheduler.archiveExpiredEvents();

		verify(archiveEventsService).archiveEventsOlderThanRetention();
	}

	@Test
	void isScheduledWithAConfigurableFixedDelay() throws Exception {
		Scheduled scheduled = ArchiveEventsScheduler.class
				.getMethod("archiveExpiredEvents")
				.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString())
				.isEqualTo("${audit.events.archive-interval-ms:86400000}");
	}

}
