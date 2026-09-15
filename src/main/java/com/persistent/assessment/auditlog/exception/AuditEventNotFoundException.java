package com.persistent.assessment.auditlog.exception;

import java.util.UUID;

public class AuditEventNotFoundException extends RuntimeException {

	private final UUID id;

	public AuditEventNotFoundException(UUID id) {
		super("Audit event not found: " + id);
		this.id = id;
	}

	public UUID getId() {
		return this.id;
	}

}
