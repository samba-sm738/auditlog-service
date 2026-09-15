package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.model.HashChainViolation;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditVerificationService {

	/** Previous hash of the very first record: the chain starts from 64 zeroes. */
	private static final String GENESIS_HASH = "0".repeat(64);

	private static final String SEQUENCE_GAP = "SEQUENCE_GAP";

	private static final String PREVIOUS_HASH_MISMATCH = "PREVIOUS_HASH_MISMATCH";

	private static final String CONTENT_HASH_MISMATCH = "CONTENT_HASH_MISMATCH";

	private final AuditEventRepository auditEventRepository;

	private final HashService hashService;

	public AuditVerificationService(AuditEventRepository auditEventRepository, HashService hashService) {
		this.auditEventRepository = auditEventRepository;
		this.hashService = hashService;
	}

	/**
	 * Walks the audit log in sequence order and verifies the tamper-evident hash chain.
	 *
	 * <p>For every record three invariants are checked: the sequence number increases by one
	 * with no gaps, the stored previous hash matches the content hash of the preceding record
	 * (or the genesis hash for the first one), and the stored content hash matches the hash
	 * recomputed from the canonical event content.
	 *
	 * <p>Verification stops at the first violation, which is reported together with the number
	 * of records inspected up to and including the offending one.
	 *
	 * @return the verification result
	 */
	@Transactional(readOnly = true)
	public AuditVerificationResponse verify() {
		String expectedPreviousHash = GENESIS_HASH;
		long expectedSequence = 1;
		long recordsChecked = 0;

		for (AuditEvent event : auditEventRepository.findAllInSequence()) {

			recordsChecked++;

			if (event.getSequenceNumber() == null || event.getSequenceNumber() != expectedSequence) {
				return broken(recordsChecked, event, SEQUENCE_GAP,
						"Expected sequence number " + expectedSequence + " but found "
								+ event.getSequenceNumber() + ".");
			}

			if (!expectedPreviousHash.equals(event.getPreviousHash())) {
				return broken(recordsChecked, event, PREVIOUS_HASH_MISMATCH,
						"Stored previous hash does not match the content hash of the preceding record.");
			}

			String canonicalEvent = hashService.canonicalize(
					event.getEventType(),
					event.getActorId(),
					event.getResourceType(),
					event.getResourceId(),
					event.getPayload(),
					event.getPreviousHash(),
					event.getEventTimestamp());

			String calculatedHash = hashService.hash(canonicalEvent);

			if (!calculatedHash.equals(event.getContentHash())) {
				return broken(recordsChecked, event, CONTENT_HASH_MISMATCH,
						"Stored content hash does not match canonical event content.");
			}

			expectedPreviousHash = event.getContentHash();
			expectedSequence++;
		}

		return new AuditVerificationResponse(true, recordsChecked)
				.firstViolation(null);
	}

	private AuditVerificationResponse broken(long recordsChecked, AuditEvent event, String type,
			String message) {
		HashChainViolation violation = new HashChainViolation()
				.sequenceNumber(event.getSequenceNumber())
				.recordId(String.valueOf(event.getId()))
				.type(type)
				.message(message);

		return new AuditVerificationResponse(false, recordsChecked)
				.firstViolation(violation);
	}

}
