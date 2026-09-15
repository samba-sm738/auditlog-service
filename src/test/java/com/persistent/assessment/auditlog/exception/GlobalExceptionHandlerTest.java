package com.persistent.assessment.auditlog.exception;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.persistent.assessment.auditlog.model.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void handleNotFoundReturns404WithCodeAndMessage() {
		UUID id = UUID.randomUUID();

		ResponseEntity<ErrorResponse> response =
				handler.handleNotFound(new AuditEventNotFoundException(id));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().getCode()).isEqualTo("AUDIT_EVENT_NOT_FOUND");
		assertThat(response.getBody().getMessage()).isEqualTo("Audit event not found: " + id);
	}

	@Test
	void handleTypeMismatchReturns400NamingTheParameter() {
		MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
		when(ex.getName()).thenReturn("cursor");

		ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().getMessage()).isEqualTo("cursor has an invalid value");
	}

	@Test
	void handleIllegalArgumentReturns400WithMessage() {
		ResponseEntity<ErrorResponse> response = handler
				.handleIllegalArgument(new IllegalArgumentException("from must be strictly before to"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().getMessage()).isEqualTo("from must be strictly before to");
	}

	@Test
	void handleConstraintViolationReportsFirstViolation() {
		ConstraintViolation<?> violation = mock(ConstraintViolation.class);
		Path path = mock(Path.class);
		when(path.toString()).thenReturn("pageSize");
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn("must be less than or equal to 100");

		ResponseEntity<ErrorResponse> response = handler
				.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getMessage())
				.isEqualTo("pageSize must be less than or equal to 100");
	}

	@Test
	void handleConstraintViolationFallsBackWhenNoViolations() {
		ResponseEntity<ErrorResponse> response = handler
				.handleConstraintViolation(new ConstraintViolationException(Set.of()));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
	}

	@Test
	void handleMethodValidationReturns400() {
		HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);

		ResponseEntity<ErrorResponse> response = handler.handleMethodValidation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().getMessage()).isEqualTo("Invalid request parameter");
	}

	@Test
	void handleBodyValidationReportsFirstFieldError() {
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors())
				.thenReturn(List.of(new FieldError("auditEventRequest", "eventType", "must not be blank")));

		ResponseEntity<ErrorResponse> response = handler.handleBodyValidation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getMessage()).isEqualTo("eventType must not be blank");
	}

	@Test
	void handleBodyValidationFallsBackWhenNoFieldErrors() {
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of());

		ResponseEntity<ErrorResponse> response = handler.handleBodyValidation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
	}

}
