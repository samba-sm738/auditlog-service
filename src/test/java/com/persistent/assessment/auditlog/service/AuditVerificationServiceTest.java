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
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditVerificationServiceTest {

	private static final String GENESIS_HASH = "0".repeat(64);

	@Mock
	private AuditEventRepository auditEventRepository;

	@Mock
	private HashService hashService;

	@InjectMocks
	private AuditVerificationService service;

	private AuditEvent event(long sequence, String previousHash, String contentHash) {
		AuditEvent event = new AuditEvent();
		event.setId(UUID.randomUUID());
		event.setSequenceNumber(sequence);
		event.setEventType("USER_LOGIN");
		event.setActorId("user-1");
		event.setResourceType("Customer");
		event.setResourceId("cust-1");
		event.setPayload(Map.of());
		event.setEventTimestamp(OffsetDateTime.parse("2026-09-14T10:00:00Z"));
		event.setPreviousHash(previousHash);
		event.setContentHash(contentHash);
		return event;
	}

	/**
	 * Canonicalization is mocked to a constant; each test then stubs {@code hash} to return
	 * the stored content hash of every record that passes the earlier invariants, in order.
	 */
	private void stubCanonicalization() {
		lenient().when(hashService.canonicalize(anyString(), anyString(), anyString(), anyString(),
				any(), anyString(), any(OffsetDateTime.class))).thenReturn("canonical-event");
	}

	@Test
	void emptyLogIsValid() {
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of());

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isTrue();
		assertThat(response.getRecordsChecked()).isZero();
		assertThat(response.getFirstViolation().get()).isNull();
	}

	@Test
	void intactChainIsValid() {
		stubCanonicalization();
		AuditEvent first = event(1, GENESIS_HASH, "hash-1");
		AuditEvent second = event(2, "hash-1", "hash-2");
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of(first, second));
		when(hashService.hash("canonical-event")).thenReturn("hash-1", "hash-2");

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isTrue();
		assertThat(response.getRecordsChecked()).isEqualTo(2);
		assertThat(response.getFirstViolation().get()).isNull();
	}

	@Test
	void reportsSequenceGap() {
		stubCanonicalization();
		AuditEvent first = event(1, GENESIS_HASH, "hash-1");
		AuditEvent second = event(3, "hash-1", "hash-3");
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of(first, second));
		when(hashService.hash("canonical-event")).thenReturn("hash-1");

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isFalse();
		assertThat(response.getRecordsChecked()).isEqualTo(2);
		assertThat(response.getFirstViolation().get().getType()).isEqualTo("SEQUENCE_GAP");
		assertThat(response.getFirstViolation().get().getSequenceNumber()).isEqualTo(3L);
		assertThat(response.getFirstViolation().get().getRecordId())
				.isEqualTo(second.getId().toString());
		assertThat(response.getFirstViolation().get().getMessage())
				.contains("Expected sequence number 2 but found 3");
	}

	@Test
	void reportsNullSequenceAsGap() {
		AuditEvent first = event(1, GENESIS_HASH, "hash-1");
		first.setSequenceNumber(null);
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of(first));

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isFalse();
		assertThat(response.getFirstViolation().get().getType()).isEqualTo("SEQUENCE_GAP");
	}

	@Test
	void reportsPreviousHashMismatch() {
		AuditEvent first = event(1, "not-the-genesis-hash", "hash-1");
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of(first));

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isFalse();
		assertThat(response.getRecordsChecked()).isEqualTo(1);
		assertThat(response.getFirstViolation().get().getType())
				.isEqualTo("PREVIOUS_HASH_MISMATCH");
		assertThat(response.getFirstViolation().get().getSequenceNumber()).isEqualTo(1L);
	}

	@Test
	void reportsContentHashMismatch() {
		stubCanonicalization();
		AuditEvent first = event(1, GENESIS_HASH, "tampered-hash");
		when(auditEventRepository.findAllInSequence()).thenReturn(List.of(first));

		// Recomputed content no longer matches the stored content hash.
		when(hashService.hash("canonical-event")).thenReturn("recomputed-hash");

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isFalse();
		assertThat(response.getRecordsChecked()).isEqualTo(1);
		assertThat(response.getFirstViolation().get().getType())
				.isEqualTo("CONTENT_HASH_MISMATCH");
		assertThat(response.getFirstViolation().get().getRecordId())
				.isEqualTo(first.getId().toString());
	}

	@Test
	void stopsAtTheFirstViolation() {
		stubCanonicalization();
		AuditEvent first = event(1, GENESIS_HASH, "hash-1");
		AuditEvent second = event(2, "wrong-previous-hash", "hash-2");
		AuditEvent third = event(3, "hash-2", "hash-3");
		when(auditEventRepository.findAllInSequence())
				.thenReturn(List.of(first, second, third));
		when(hashService.hash("canonical-event")).thenReturn("hash-1");

		AuditVerificationResponse response = service.verify();

		assertThat(response.getValid()).isFalse();
		assertThat(response.getRecordsChecked()).isEqualTo(2);
	}

}
