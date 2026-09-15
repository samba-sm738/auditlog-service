package com.persistent.assessment.auditlog.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditExportBundle;

import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

	@Mock
	private AuditEventRepository eventRepository;

	@InjectMocks
	private AuditExportService service;

	private AuditEvent event(long sequence, String actorId, String resourceId,
			String previousHash, String contentHash) {
		AuditEvent event = new AuditEvent();
		event.setId(UUID.randomUUID());
		event.setSequenceNumber(sequence);
		event.setEventType("RECORD_UPDATED");
		event.setActorId(actorId);
		event.setResourceType("Customer");
		event.setResourceId(resourceId);
		event.setPayload(Map.of("field", "address"));
		event.setEventTimestamp(OffsetDateTime.parse("2026-09-14T10:00:0" + sequence + "Z"));
		event.setPreviousHash(previousHash);
		event.setContentHash(contentHash);
		event.setCreatedAt(OffsetDateTime.parse("2026-09-14T10:10:00Z"));
		return event;
	}

	@Test
	void exportsByResourceIdWithChainAnchors() {
		AuditEvent first = event(1, "user-1", "cust-1", "0".repeat(64), "hash-1");
		AuditEvent second = event(4, "user-2", "cust-1", "hash-3", "hash-4");
		when(eventRepository.findByResourceIdOrderBySequenceNumberAsc("cust-1"))
				.thenReturn(List.of(first, second));

		AuditExportBundle bundle = service.exportByResourceId("cust-1");

		verify(eventRepository).findByResourceIdOrderBySequenceNumberAsc("cust-1");

		assertThat(bundle.getFormat()).isEqualTo("audit-bundle-v1");
		assertThat(bundle.getExportedAt()).isNotNull();
		assertThat(bundle.getFilter().getType()).isEqualTo("resourceId");
		assertThat(bundle.getFilter().getValue()).isEqualTo("cust-1");

		assertThat(bundle.getChain().getAlgorithm()).isEqualTo("SHA-256");
		assertThat(bundle.getChain().getRecordCount()).isEqualTo(2);
		assertThat(bundle.getChain().getFirstRecordId()).isEqualTo(first.getId());
		assertThat(bundle.getChain().getFirstSequenceNumber()).isEqualTo(1L);
		// The anchor preserves the link to the excluded predecessor, not a fabricated one.
		assertThat(bundle.getChain().getFirstPreviousHash()).isEqualTo("0".repeat(64));
		assertThat(bundle.getChain().getLastRecordId()).isEqualTo(second.getId());
		assertThat(bundle.getChain().getLastSequenceNumber()).isEqualTo(4L);
		assertThat(bundle.getChain().getLastRecordHash()).isEqualTo("hash-4");

		assertThat(bundle.getRecords()).hasSize(2);
		AuditEventResponse exported = bundle.getRecords().get(1);
		assertThat(exported.getId()).isEqualTo(second.getId());
		assertThat(exported.getSequenceNumber()).isEqualTo(4L);
		assertThat(exported.getEventType()).isEqualTo("RECORD_UPDATED");
		assertThat(exported.getActorId()).isEqualTo("user-2");
		assertThat(exported.getResourceType()).isEqualTo("Customer");
		assertThat(exported.getResourceId()).isEqualTo("cust-1");
		assertThat(exported.getPayload()).containsEntry("field", "address");
		assertThat(exported.getTimestamp()).isEqualTo(second.getEventTimestamp());
		// Integrity fields are copied unchanged — export never rewrites hashes.
		assertThat(exported.getPreviousHash()).isEqualTo("hash-3");
		assertThat(exported.getContentHash()).isEqualTo("hash-4");
	}

	@Test
	void exportsByActorId() {
		AuditEvent event = event(7, "user-7", "cust-3", "hash-6", "hash-7");
		when(eventRepository.findByActorIdOrderBySequenceNumberAsc("user-7"))
				.thenReturn(List.of(event));

		AuditExportBundle bundle = service.exportByActorId("user-7");

		verify(eventRepository).findByActorIdOrderBySequenceNumberAsc("user-7");

		assertThat(bundle.getFilter().getType()).isEqualTo("actorId");
		assertThat(bundle.getFilter().getValue()).isEqualTo("user-7");
		assertThat(bundle.getChain().getRecordCount()).isEqualTo(1);
		assertThat(bundle.getChain().getFirstPreviousHash()).isEqualTo("hash-6");
		assertThat(bundle.getChain().getLastRecordHash()).isEqualTo("hash-7");
		assertThat(bundle.getRecords()).hasSize(1);
	}

	@Test
	void emptyMatchProducesValidEmptyBundle() {
		when(eventRepository.findByResourceIdOrderBySequenceNumberAsc("cust-9"))
				.thenReturn(List.of());

		AuditExportBundle bundle = service.exportByResourceId("cust-9");

		assertThat(bundle.getFormat()).isEqualTo("audit-bundle-v1");
		assertThat(bundle.getRecords()).isEmpty();
		assertThat(bundle.getChain().getRecordCount()).isZero();
		assertThat(bundle.getChain().getFirstRecordId()).isNull();
		assertThat(bundle.getChain().getFirstSequenceNumber()).isNull();
		assertThat(bundle.getChain().getFirstPreviousHash()).isNull();
		assertThat(bundle.getChain().getLastRecordId()).isNull();
		assertThat(bundle.getChain().getLastSequenceNumber()).isNull();
		assertThat(bundle.getChain().getLastRecordHash()).isNull();
	}

}
