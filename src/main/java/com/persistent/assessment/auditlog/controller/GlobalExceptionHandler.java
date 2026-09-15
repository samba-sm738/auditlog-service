package com.persistent.assessment.auditlog.controller;

import com.persistent.assessment.auditlog.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	// TODO: extend with handlers for domain-specific failures (e.g. chain verification errors)
	// TODO: once EventService is implemented

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
