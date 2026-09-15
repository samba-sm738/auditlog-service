package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditExportBundle;
import com.persistent.assessment.auditlog.model.AuditExportChain;
import com.persistent.assessment.auditlog.model.AuditExportFilter;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds self-contained, independently verifiable export bundles.
 *
 * <p>The export is strictly read-only: persisted audit records are authoritative and are
 * copied into the bundle unchanged — no hash is ever recalculated or rewritten on the way
 * out.
 *
 * <p>A filtered export is generally not a contiguous segment of the global audit chain,
 * so each record keeps its original {@code previousHash}. The chain metadata anchors the
 * exported segment instead of fabricating links: {@code firstPreviousHash} names the hash
 * that precedes the first exported record (which may belong to a record the filter
 * excluded), and {@code lastRecordHash} names the hash of the last exported record.
 */
@Service
public class AuditExportService {

	/** Versioned identifier of the bundle format this service writes. */
	public static final String BUNDLE_FORMAT = "audit-bundle-v1";

	public static final String FILTER_RESOURCE_ID = "resourceId";

	public static final String FILTER_ACTOR_ID = "actorId";

	private final AuditEventRepository eventRepository;

	public AuditExportService(AuditEventRepository eventRepository) {
		this.eventRepository = eventRepository;
	}

	/**
	 * Exports the complete history of one resource.
	 *
	 * @param resourceId the resource to export
	 * @return the bundle; when nothing matches, a valid empty bundle
	 */
	@Transactional(readOnly = true)
	public AuditExportBundle exportByResourceId(String resourceId) {
		return buildBundle(FILTER_RESOURCE_ID, resourceId,
				eventRepository.findByResourceIdOrderBySequenceNumberAsc(resourceId));
	}

	/**
	 * Exports every event caused by one actor.
	 *
	 * @param actorId the actor to export
	 * @return the bundle; when nothing matches, a valid empty bundle
	 */
	@Transactional(readOnly = true)
	public AuditExportBundle exportByActorId(String actorId) {
		return buildBundle(FILTER_ACTOR_ID, actorId,
				eventRepository.findByActorIdOrderBySequenceNumberAsc(actorId));
	}

	private AuditExportBundle buildBundle(String filterType, String filterValue,
			List<AuditEvent> events) {
		List<AuditEventResponse> records = events.stream()
				.map(AuditEventMapper::toResponse)
				.toList();

		AuditExportChain chain = new AuditExportChain()
				.algorithm(HashService.ALGORITHM)
				.recordCount((long) records.size());

		if (!events.isEmpty()) {
			AuditEvent first = events.getFirst();
			AuditEvent last = events.getLast();

			chain.firstRecordId(first.getId())
					.firstSequenceNumber(first.getSequenceNumber())
					.firstPreviousHash(first.getPreviousHash())
					.lastRecordId(last.getId())
					.lastSequenceNumber(last.getSequenceNumber())
					.lastRecordHash(last.getContentHash());
		}

		return new AuditExportBundle()
				.format(BUNDLE_FORMAT)
				.exportedAt(OffsetDateTime.now(ZoneOffset.UTC))
				.filter(new AuditExportFilter().type(filterType).value(filterValue))
				.chain(chain)
				.records(records);
	}

}
