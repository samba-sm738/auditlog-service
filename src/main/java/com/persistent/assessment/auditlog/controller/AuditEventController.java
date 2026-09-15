package com.persistent.assessment.auditlog.controller;

import com.persistent.assessment.auditlog.api.V1Api;
import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventPage;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.service.AuditEventService;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventQuery;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventSlice;
import com.persistent.assessment.auditlog.service.AuditVerificationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditEventController implements V1Api {

	/** Mirrors the default declared for pageSize in the API contract. */
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final AuditEventService eventService;

	private final AuditVerificationService verificationService;

	public AuditEventController(AuditEventService eventService,
			AuditVerificationService verificationService) {
		this.eventService = eventService;
		this.verificationService = verificationService;
	}

	@Override
	public ResponseEntity<AuditEventResponse> createAuditEvent(AuditEventRequest auditEventRequest) {
		AuditEvent event = eventService.append(auditEventRequest);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(event));
	}

	/**
	 * Returns one cursor page of the audit log.
	 *
	 * <p>Paging is keyset based on the sequence number: {@code cursor} is the last sequence
	 * number the caller has already seen, and the response reports the {@code nextCursor} to
	 * pass back. Bounds on {@code cursor} and {@code pageSize} are enforced by the generated
	 * contract; only the cross-field {@code from}/{@code to} rule is checked here.
	 */
	@Override
	public ResponseEntity<AuditEventPage> listAuditEvents(String actorId, String resourceType,
			String resourceId, String eventType, OffsetDateTime from, OffsetDateTime to,
			Long cursor, Integer pageSize) {
		if (from != null && to != null && !from.isBefore(to)) {
			throw new IllegalArgumentException("from must be strictly before to");
		}

		int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;

		AuditEventSlice slice = eventService.findSlice(new AuditEventQuery(
				actorId, resourceType, resourceId, eventType, from, to, cursor, size));

		List<AuditEventResponse> content = slice.events().stream()
				.map(this::toResponse)
				.toList();

		AuditEventPage auditEventPage = new AuditEventPage()
				.content(content)
				.pageSize(size)
				.nextCursor(slice.nextCursor())
				.hasMore(slice.hasMore());

		return ResponseEntity.ok(auditEventPage);
	}

	@Override
	public ResponseEntity<AuditVerificationResponse> verifyAuditLog() {
		return ResponseEntity.ok(verificationService.verify());
	}
	
	private AuditEventResponse toResponse(AuditEvent event) {
		return new AuditEventResponse()
				.id(event.getId())
				.sequenceNumber(event.getSequenceNumber())
				.eventType(event.getEventType())
				.actorId(event.getActorId())
				.resourceType(event.getResourceType())
				.resourceId(event.getResourceId())
				.payload(event.getPayload())
				.timestamp(event.getEventTimestamp())
				.previousHash(event.getPreviousHash())
				.contentHash(event.getContentHash());
	}
}
