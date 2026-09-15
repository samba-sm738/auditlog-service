package com.persistent.assessment.auditlog.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Computes SHA-256 hashes for audit log content and hash chaining.
 *
 * <p>Stateless and thread-safe: a new {@link MessageDigest} instance is created per call.
 */
@Component
public class HashService {

	private static final String ALGORITHM = "SHA-256";

	/**
	 * Computes the SHA-256 hash of the given value.
	 *
	 * @param value the value to hash, encoded as UTF-8; a {@code null} value is treated as empty
	 * @return the hash as a lowercase hexadecimal string, 64 characters long
	 * @throws IllegalStateException if the SHA-256 algorithm is not available on this JVM
	 */
	public String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			byte[] hashed = digest.digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hashed.length * 2);
			for (byte b : hashed) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(ALGORITHM + " algorithm not available", e);
		}
	}

}
