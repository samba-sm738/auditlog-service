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

	/** The hash algorithm used for contentHash and previousHash. */
	public static final String ALGORITHM = "SHA-256";

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
	 * <p>Fields are written in the exact order defined by the contract. The preceding record's
	 * hash is part of the canonical content, which is what makes the log tamper-evident: an
	 * event cannot be moved, removed or spliced into a different position without invalidating
	 * its own content hash and every hash after it. The event's own {@code contentHash} is
	 * necessarily excluded.
	 *
	 * @param eventType the event type
	 * @param actorId the identifier of the actor that triggered the event
	 * @param resourceType the type of the affected resource
	 * @param resourceId the identifier of the affected resource
	 * @param payload the event payload
	 * @param previousHash the content hash of the preceding record, or the genesis hash for
	 * the first record
	 * @param timestamp the event timestamp, normalized to UTC before hashing
	 * @return the canonical JSON representation of the event
	 * @throws IllegalArgumentException if the event cannot be serialized
	 */
	public String canonicalize(String eventType, String actorId, String resourceType,
			String resourceId, Map<String, Object> payload, String previousHash,
			OffsetDateTime timestamp) {
		try {
			ObjectNode event = objectMapper.createObjectNode();

			// Insert fields in the exact order defined by the contract.
			event.put("eventType", eventType);
			event.put("actorId", actorId);
			event.put("resourceType", resourceType);
			event.put("resourceId", resourceId);

			// Hash the payload as JSON, so that its structure and not its Java rendering is
			// what is committed to.
			event.set("payload", canonicalizePayload(payload));

			// Always hash the timestamp in UTC.
			event.put("timestamp", timestamp.toInstant().toString());

			// Chain link: binds this event to its position in the log.
			event.put("previousHash", previousHash);

			return objectMapper.writeValueAsString(event);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("Unable to canonicalize audit event", e);
		}
	}

	/**
	 * Canonicalizes the event payload.
	 *
	 * <p>Object properties are recursively sorted so that logically equivalent JSON objects
	 * produce the same hash. A missing payload canonicalizes to an empty object.
	 *
	 * @param payload the payload to canonicalize
	 * @return the canonicalized payload
	 * @throws IllegalArgumentException if the payload cannot be represented as JSON
	 */
	public JsonNode canonicalizePayload(Map<String, Object> payload) {
		try {
			JsonNode node = objectMapper.valueToTree(payload == null ? Map.of() : payload);
			if (node == null || node.isMissingNode()) {
				throw new IllegalArgumentException("Payload must be representable as JSON");
			}

			return sortJsonNode(node);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("Payload must be representable as JSON", e);
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
