package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.repository.EventRepo;
import org.springframework.stereotype.Service;

@Service
public class EventService {

	private final EventRepo eventRepo;

	public EventService(EventRepo eventRepo) {
		this.eventRepo = eventRepo;
	}

	public AuditEventResponse createAuditEvent(AuditEventRequest auditEventRequest) {
		// TODO: implement audit event recording (sequence assignment, hash chaining, persistence via eventRepo)
		return null;
	}

}
