package com.persistent.assessment.auditlog.controller;

import com.persistent.assessment.auditlog.api.V1Api;
import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventPage;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.service.AuditEventService;
import com.persistent.assessment.auditlog.service.AuditVerificationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditEventController implements V1Api {

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

	

	@Override
	public ResponseEntity<AuditEventPage> listAuditEvents(String actorId, String resourceType,
			String resourceId, String eventType, OffsetDateTime from, OffsetDateTime to,
			Integer page, Integer pageSize) {
		List<AuditEvent> events = eventService.findAllInSequence();

		List<AuditEventResponse> content = events.stream()
				.map(this::toResponse)
				.toList();

		AuditEventPage auditEventPage = new AuditEventPage()
				.content(content)
				.page(page)
				.pageSize(pageSize)
				.totalElements((long) content.size())
				.totalPages(content.isEmpty() ? 0
						: (int) Math.ceil((double) content.size() / pageSize));

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
