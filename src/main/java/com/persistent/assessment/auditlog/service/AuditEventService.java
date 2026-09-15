package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.entity.AuditChainState;
import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.exception.AuditEventNotFoundException;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.repository.AuditChainStateRepository;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

	/** Precision of the timestamp columns, i.e. {@code TIMESTAMP(6) WITH TIME ZONE}. */
	private static final ChronoUnit TIMESTAMP_PRECISION = ChronoUnit.MICROS;

	private final AuditEventRepository eventRepository;

	private final AuditChainStateRepository chainStateRepository;

	private final HashService hashService;

	public AuditEventService(AuditEventRepository eventRepository,
			AuditChainStateRepository chainStateRepository, HashService hashService) {
		this.eventRepository = eventRepository;
		this.chainStateRepository = chainStateRepository;
		this.hashService = hashService;
	}

	@Transactional
	public AuditEvent append(AuditEventRequest request) {
		// Locks the singleton chain tip row, so that concurrent appends cannot claim the same
		// sequence number or chain onto the same previous hash.
		AuditChainState chainState = chainStateRepository.getChainTip();

		if (chainState == null) {
			throw new IllegalStateException(
					"Audit chain state is missing: the audit_chain_state genesis row must be seeded");
		}

		long sequenceNumber = chainState.getNextSequenceNumber();

		String previousHash = chainState.getLatestHash();

		// The caller may state when the event occurred; otherwise it is recorded as now.
		// Either way the timestamp is normalized to UTC and truncated to the precision the
		// column stores before it is hashed: hashing a value the database would then round
		// would make the record unverifiable the moment it is read back.
		OffsetDateTime timestamp = (request.getTimestamp() == null
				? OffsetDateTime.now(ZoneOffset.UTC)
				: request.getTimestamp().withOffsetSameInstant(ZoneOffset.UTC))
				.truncatedTo(TIMESTAMP_PRECISION);

		// The payload column is NOT NULL, and an absent payload hashes as an empty object.
		Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();

		String canonicalEvent = hashService.canonicalize(
				request.getEventType(),
				request.getActorId(),
				request.getResourceType(),
				request.getResourceId(),
				payload,
				previousHash,
				timestamp);

		String contentHash = hashService.hash(canonicalEvent);

		AuditEvent event = new AuditEvent();

		event.setId(UUID.randomUUID());
		event.setSequenceNumber(sequenceNumber);
		event.setEventType(request.getEventType());
		event.setActorId(request.getActorId());
		event.setResourceType(request.getResourceType());
		event.setResourceId(request.getResourceId());
		event.setPayload(payload);
		event.setEventTimestamp(timestamp);
		event.setPreviousHash(previousHash);
		event.setContentHash(contentHash);
		event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(TIMESTAMP_PRECISION));

		AuditEvent saved = eventRepository.save(event);

		chainState.advance(contentHash);

		chainStateRepository.save(chainState);

		return saved;
	}

	@Transactional(readOnly = true)
	public AuditEvent findById(UUID id) {
		return eventRepository.findById(id)
				.orElseThrow(() -> new AuditEventNotFoundException(id));
	}

	/**
	 * Reads one cursor page of the audit log.
	 *
	 * <p>One row more than the requested page size is read, so that the presence of a further
	 * page is known without counting the whole log. The extra row is dropped before returning.
	 *
	 * @param query the filters, cursor and page size to apply
	 * @return the page of events, together with the cursor to read the next one
	 */
	@Transactional(readOnly = true)
	public AuditEventSlice findSlice(AuditEventQuery query) {
		List<AuditEvent> rows = eventRepository.findSlice(
				query.cursor(),
				query.actorId(),
				query.resourceType(),
				query.resourceId(),
				query.eventType(),
				query.from(),
				query.to(),
				Limit.of(query.pageSize() + 1));

		boolean hasMore = rows.size() > query.pageSize();

		List<AuditEvent> events = hasMore ? List.copyOf(rows.subList(0, query.pageSize())) : rows;

		Long nextCursor = hasMore ? events.getLast().getSequenceNumber() : null;

		return new AuditEventSlice(events, nextCursor, hasMore);
	}

	@Transactional(readOnly = true)
	public AuditChainTip getChainTip() {
		AuditChainState state = chainStateRepository.findById(true)
				.orElseThrow(() -> new IllegalStateException("Audit chain state is missing"));

		return new AuditChainTip(state.getNextSequenceNumber(), state.getLatestHash());
	}

	/**
	 * A query against the audit log: optional filters combined with AND, plus the cursor and
	 * page size that bound the returned slice.
	 *
	 * @param actorId the actor to filter on, or {@code null} for any actor
	 * @param resourceType the resource type to filter on, or {@code null} for any type
	 * @param resourceId the resource id to filter on, or {@code null} for any resource
	 * @param eventType the event type to filter on, or {@code null} for any type
	 * @param from inclusive lower bound on the event timestamp, or {@code null} for none
	 * @param to exclusive upper bound on the event timestamp, or {@code null} for none
	 * @param cursor exclusive lower bound on the sequence number, or {@code null} to start
	 * from the beginning of the log
	 * @param pageSize maximum number of events to return
	 */
	public record AuditEventQuery(
			String actorId,
			String resourceType,
			String resourceId,
			String eventType,
			OffsetDateTime from,
			OffsetDateTime to,
			Long cursor,
			int pageSize
	) {
	}

	/**
	 * One page of the audit log.
	 *
	 * @param events the events on this page, in ascending sequence order
	 * @param nextCursor the cursor to pass back to read the next page, or {@code null} when
	 * this is the last page
	 * @param hasMore whether at least one further matching event exists after this page
	 */
	public record AuditEventSlice(
			List<AuditEvent> events,
			Long nextCursor,
			boolean hasMore
	) {
	}

	public record AuditChainTip(
			long nextSequenceNumber,
			String latestHash
	) {
	}

}
