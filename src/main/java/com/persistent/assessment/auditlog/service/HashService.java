package com.persistent.assessment.auditlog.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Computes SHA-256 hashes for audit log content and hash chaining.
 *
 * <p>Stateless and thread-safe: a new {@link MessageDigest} instance is created per call.
 */
@Component
public class HashService {

	private static final String ALGORITHM = "SHA-256";

	/**
	 * Dedicated mapper rather than the application-wide bean: canonicalization must never
	 * change, otherwise every previously stored hash becomes unverifiable.
	 *
	 * <p>Thread-safe, as {@link ObjectMapper} instances are immutable once built.
	 */
	private final ObjectMapper objectMapper = JsonMapper.builder().build();

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

	/**
	 * Creates the canonical representation of the event used to compute the content hash.
	 *
	 * <p>Fields are written in the exact order defined by the contract, and the hash fields
	 * themselves ({@code previousHash} and {@code contentHash}) are deliberately excluded.
	 *
	 * @param eventType the event type
	 * @param actorId the identifier of the actor that triggered the event
	 * @param resourceType the type of the affected resource
	 * @param resourceId the identifier of the affected resource
	 * @param payload the event payload, as a JSON document
	 * @param timestamp the event timestamp, normalized to UTC before hashing
	 * @return the canonical JSON representation of the event
	 * @throws IllegalArgumentException if the payload is not valid JSON or the event cannot
	 * be serialized
	 */
	public String canonicalize(String eventType, String actorId, String resourceType,
			String resourceId, String payload, OffsetDateTime timestamp) {
		try {
			ObjectNode event = objectMapper.createObjectNode();

			// Insert fields in the exact order defined by the contract.
			event.put("eventType", eventType);
			event.put("actorId", actorId);
			event.put("resourceType", resourceType);
			event.put("resourceId", resourceId);

			// Parse payload as JSON rather than treating it as a plain string.
			event.set("payload", canonicalizeJson(payload));

			// Always hash the timestamp in UTC.
			event.put("timestamp", timestamp.toInstant().toString());

			return objectMapper.writeValueAsString(event);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("Unable to canonicalize audit event", e);
		}
	}

	/**
	 * Parses and canonicalizes the payload JSON.
	 *
	 * <p>Object properties are recursively sorted so that logically equivalent JSON objects
	 * produce the same hash.
	 *
	 * @param payload the payload to canonicalize, as a JSON document
	 * @return the canonicalized payload
	 * @throws IllegalArgumentException if the payload is missing or not valid JSON
	 */
	public JsonNode canonicalizeJson(String payload) {
		try {
			JsonNode node = objectMapper.readTree(payload);
			if (node == null || node.isMissingNode()) {
				throw new IllegalArgumentException("Payload must be valid JSON");
			}

			return sortJsonNode(node);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("Payload must contain valid JSON", e);
		}
	}

	/**
	 * Recursively sorts JSON object keys, so that {@code {"b":2,"a":1}} becomes
	 * {@code {"a":1,"b":2}}.
	 *
	 * <p>Arrays retain their original order, because array ordering can be semantically
	 * significant. Strings, numbers, booleans and nulls are returned unchanged.
	 *
	 * @param node the node to sort
	 * @return the node with all nested object keys sorted
	 */
	private JsonNode sortJsonNode(JsonNode node) {
		if (node.isObject()) {
			Map<String, JsonNode> sortedFields = new TreeMap<>();
			node.properties().forEach(entry -> sortedFields.put(entry.getKey(), entry.getValue()));

			ObjectNode sortedObject = objectMapper.createObjectNode();
			sortedFields.forEach((key, value) -> sortedObject.set(key, sortJsonNode(value)));

			return sortedObject;
		}

		if (node.isArray()) {
			ArrayNode sortedArray = objectMapper.createArrayNode();
			for (JsonNode element : node) {
				sortedArray.add(sortJsonNode(element));
			}

			return sortedArray;
		}

		// Strings, numbers, booleans and null remain unchanged.
		return node;
	}

}
