package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventResponse;

/**
 * Maps persisted audit events to their API representation. Shared by the list and export
 * paths so that a record looks identical no matter which endpoint produced it.
 */
public final class AuditEventMapper {

	private AuditEventMapper() {
	}

	public static AuditEventResponse toResponse(AuditEvent event) {
		return new AuditEventResponse()
				.id(event.getId())
				.sequenceNumber(event.getSequenceNumber())
				.eventType(event.getEventType())
				.actorId(event.getActorId())
				.resourceType(event.getResourceType())
				.resourceId(event.getResourceId())
				.payload(PayloadRedactor.redactAccountNumbers(event.getPayload()))
				.timestamp(event.getEventTimestamp())
				.previousHash(event.getPreviousHash())
				.contentHash(event.getContentHash());
	}

}
