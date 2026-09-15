package com.persistent.assessment.auditlog.controller;

import com.persistent.assessment.auditlog.api.V1Api;
import com.persistent.assessment.auditlog.model.AuditEventPage;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.service.EventService;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController implements V1Api {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@Override
	public ResponseEntity<AuditEventResponse> createAuditEvent(AuditEventRequest auditEventRequest) {
		return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createAuditEvent(auditEventRequest));
	}

	@Override
	public ResponseEntity<AuditEventPage> listAuditEvents(String actorId, String resourceType,
			String resourceId, String eventType, OffsetDateTime from, OffsetDateTime to,
			Integer page, Integer pageSize) {
		return ResponseEntity.ok(eventService.listAuditEvents(actorId, resourceType, resourceId,
				eventType, from, to, page, pageSize));
	}

	@Override
	public ResponseEntity<AuditVerificationResponse> verifyAuditLog() {
		return ResponseEntity.ok(eventService.verifyAuditLog());
	}

}
