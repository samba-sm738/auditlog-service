package com.persistent.assessment.auditlog.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces a present {@code accountNumber} field of an audit event payload with a fixed
 * redaction marker.
 *
 * <p>Redaction is one-way and deterministic: the service has no reversible encryption to
 * reuse — SHA-256, which protects the hash chain, cannot decrypt — so the original value
 * is replaced by a constant marker before the payload is hashed and persisted, and the
 * marker is what responses return.
 */
final class PayloadRedactor {

	/** The payload field whose value must never be stored or returned in clear text. */
	static final String ACCOUNT_NUMBER_FIELD = "accountNumber";

	/** Deterministic marker written in place of the account number. */
	static final String REDACTED = "[REDACTED]";

	private PayloadRedactor() {
	}

	/**
	 * Returns a copy of {@code payload} with {@code accountNumber} replaced by
	 * {@link #REDACTED} when the field is present, or {@code payload} unchanged when it
	 * is absent.
	 *
	 * <p>The field is matched by exact name and presence only: {@code null} and empty
	 * values are redacted exactly like populated ones, while lookalike names such as
	 * {@code accountNumberLast4} are left untouched. The given map is never mutated.
	 *
	 * @param payload the payload to redact, or {@code null}
	 * @return the redacted copy, or {@code payload} (possibly {@code null}) when no
	 * {@code accountNumber} field is present
	 */
	static Map<String, Object> redactAccountNumbers(Map<String, Object> payload) {
		if (payload == null || !payload.containsKey(ACCOUNT_NUMBER_FIELD)) {
			return payload;
		}

		Map<String, Object> redacted = new LinkedHashMap<>(payload);
		redacted.put(ACCOUNT_NUMBER_FIELD, REDACTED);

		return redacted;
	}

}
