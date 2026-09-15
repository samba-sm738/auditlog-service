package com.persistent.assessment.auditlog.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HashServiceTest {

	private final HashService hashService = new HashService();

	@Test
	void hashReturnsLowercaseSha256Hex() {
		// Well-known SHA-256 of "abc".
		assertThat(hashService.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void hashOfNullEqualsHashOfEmptyInput() {
		// SHA-256 of the empty byte sequence.
		assertThat(hashService.hash(null))
				.isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
	}

	@Test
	void hashIsDeterministicAndDistinctForDistinctInputs() {
		assertThat(hashService.hash("same")).isEqualTo(hashService.hash("same"));
		assertThat(hashService.hash("a")).isNotEqualTo(hashService.hash("b"));
		assertThat(hashService.hash("anything")).hasSize(64);
	}

	@Test
	void canonicalizeWritesFieldsInContractOrder() {
		String canonical = hashService.canonicalize(
				"USER_LOGIN", "user-1", "Customer", "cust-1",
				Map.of("b", 2, "a", 1),
				"0".repeat(64),
				OffsetDateTime.parse("2026-09-14T10:00:00Z"));

		assertThat(canonical).isEqualTo(
				"{\"eventType\":\"USER_LOGIN\",\"actorId\":\"user-1\","
						+ "\"resourceType\":\"Customer\",\"resourceId\":\"cust-1\","
						+ "\"payload\":{\"a\":1,\"b\":2},"
						+ "\"timestamp\":\"2026-09-14T10:00:00Z\","
						+ "\"previousHash\":\"" + "0".repeat(64) + "\"}");
	}

	@Test
	void canonicalizeNormalizesTimestampToUtc() {
		String canonical = hashService.canonicalize(
				"USER_LOGIN", "user-1", "Customer", "cust-1",
				Map.of(),
				"0".repeat(64),
				OffsetDateTime.parse("2026-09-14T15:30:00+05:30"));

		assertThat(canonical).contains("\"timestamp\":\"2026-09-14T10:00:00Z\"");
	}

	@Test
	void canonicalizeKeepsTimestampSubSecondPrecision() {
		String canonical = hashService.canonicalize(
				"USER_LOGIN", "user-1", "Customer", "cust-1",
				Map.of(),
				"0".repeat(64),
				OffsetDateTime.parse("2026-09-14T10:00:00.123456Z"));

		assertThat(canonical).contains("\"timestamp\":\"2026-09-14T10:00:00.123456Z\"");
	}

	@Test
	void canonicalizePayloadSortsObjectKeysRecursively() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("z", Map.of("y", 1, "a", 2));
		payload.put("b", "value");
		payload.put("a", List.of(Map.of("d", 4, "c", 3)));

		JsonNode node = hashService.canonicalizePayload(payload);

		assertThat(node.toString())
				.isEqualTo("{\"a\":[{\"c\":3,\"d\":4}],\"b\":\"value\",\"z\":{\"a\":2,\"y\":1}}");
	}

	@Test
	void canonicalizePayloadOfNullIsEmptyObject() {
		JsonNode node = hashService.canonicalizePayload(null);

		assertThat(node.isObject()).isTrue();
		assertThat(node.toString()).isEqualTo("{}");
	}

	@Test
	void canonicalizePayloadPreservesArrayOrderAndScalars() {
		Map<String, Object> payload = Map.of(
				"nums", List.of(3, 1, 2),
				"flag", true,
				"missing", java.util.Collections.singletonMap("k", null));

		JsonNode node = hashService.canonicalizePayload(payload);

		assertThat(node.get("nums").toString()).isEqualTo("[3,1,2]");
		assertThat(node.get("flag").asBoolean()).isTrue();
		assertThat(node.get("missing").get("k").isNull()).isTrue();
	}

}
