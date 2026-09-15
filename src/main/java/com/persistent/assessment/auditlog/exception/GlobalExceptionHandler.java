package com.persistent.assessment.auditlog.exception;

import com.persistent.assessment.auditlog.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AuditEventNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(AuditEventNotFoundException ex) {
		ErrorResponse body = new ErrorResponse(ex.getMessage());
		body.setCode("AUDIT_EVENT_NOT_FOUND");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	/** Raised when a query parameter cannot be bound, e.g. a non-numeric cursor. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return badRequest(ex.getName() + " has an invalid value");
	}

	/** Raised for cross-field query rules the generated contract cannot express. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		return badRequest(ex.getConstraintViolations().stream()
				.findFirst()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.orElse("Request validation failed"));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
		return badRequest("Invalid request parameter");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
		return badRequest(ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
				.orElse("Request validation failed"));
	}

	private ResponseEntity<ErrorResponse> badRequest(String message) {
		ErrorResponse body = new ErrorResponse(message);
		body.setCode("VALIDATION_FAILED");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

}
