package com.persistent.assessment.auditlog.exception;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuditEventNotFoundExceptionTest {

	@Test
	void carriesTheMissingIdInMessageAndAccessor() {
		UUID id = UUID.randomUUID();

		AuditEventNotFoundException exception = new AuditEventNotFoundException(id);

		assertThat(exception.getId()).isEqualTo(id);
		assertThat(exception.getMessage()).isEqualTo("Audit event not found: " + id);
		assertThat(exception).isInstanceOf(RuntimeException.class);
	}

}
