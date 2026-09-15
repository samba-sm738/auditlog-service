package com.persistent.assessment.auditlog.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadRedactorTest {

	@Test
	void redactsAPresentAccountNumberRegardlessOfItsValue() {
		assertThat(PayloadRedactor.redactAccountNumbers(
				Map.of("accountNumber", "1234567890123456", "status", "ACTIVE", "amount", 100)))
				.containsEntry("accountNumber", "[REDACTED]")
				.containsEntry("status", "ACTIVE")
				.containsEntry("amount", 100);

		assertThat(PayloadRedactor.redactAccountNumbers(Map.of("accountNumber", "1234")))
				.containsEntry("accountNumber", "[REDACTED]");

		// Empty and non-string values are still present fields.
		assertThat(PayloadRedactor.redactAccountNumbers(Map.of("accountNumber", "")))
				.containsEntry("accountNumber", "[REDACTED]");
		assertThat(PayloadRedactor.redactAccountNumbers(Map.of("accountNumber", 123456789)))
				.containsEntry("accountNumber", "[REDACTED]");
	}

	@Test
	void treatsANullAccountNumberAsPresent() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("accountNumber", null);
		payload.put("status", "ACTIVE");

		assertThat(PayloadRedactor.redactAccountNumbers(payload))
				.containsEntry("accountNumber", "[REDACTED]")
				.containsEntry("status", "ACTIVE");
	}

	@Test
	void leavesPayloadsWithoutAccountNumberUnchanged() {
		assertThat(PayloadRedactor.redactAccountNumbers(Map.of("status", "ACTIVE", "amount", 100)))
				.containsExactlyInAnyOrderEntriesOf(Map.of("status", "ACTIVE", "amount", 100));
	}

	@Test
	void doesNotRedactLookalikeFieldNames() {
		assertThat(PayloadRedactor.redactAccountNumbers(Map.of(
				"accountNumber", "123456",
				"accountNumberType", "SAVINGS",
				"accountNumberLast4", "3456")))
				.containsEntry("accountNumber", "[REDACTED]")
				.containsEntry("accountNumberType", "SAVINGS")
				.containsEntry("accountNumberLast4", "3456");
	}

	@Test
	void neverMutatesTheGivenPayload() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("accountNumber", "123456");

		PayloadRedactor.redactAccountNumbers(payload);

		assertThat(payload).containsEntry("accountNumber", "123456");
	}

	@Test
	void toleratesANullPayload() {
		assertThat(PayloadRedactor.redactAccountNumbers(null)).isNull();
	}

}
