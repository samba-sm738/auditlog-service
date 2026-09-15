package com.persistent.assessment.auditlog.controller;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code /v1/audit/events}, focused on the cursor pagination contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditEventControllerTests {

	private static final String GENESIS_HASH = "0".repeat(64);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	/**
	 * Resets the log between tests, so that sequence numbers and therefore cursors are
	 * deterministic.
	 */
	@BeforeEach
	void resetAuditLog() {
		jdbcTemplate.execute("DELETE FROM audit_events");
		jdbcTemplate.update(
				"UPDATE audit_chain_state SET next_sequence_number = 1, latest_hash = ?",
				GENESIS_HASH);
	}

	@Test
	void walksTheWholeLogWithoutSkippingOrRepeatingEvents() throws Exception {
		for (int i = 1; i <= 5; i++) {
			append("user-" + i, "Customer", "cust-" + i, "RECORD_UPDATED",
					"2026-09-14T10:0" + i + ":00Z");
		}

		List<Long> visited = new ArrayList<>();
		Long cursor = null;
		int pages = 0;

		do {
			JsonNode page = list(cursor == null
					? "/v1/audit/events?pageSize=2"
					: "/v1/audit/events?pageSize=2&cursor=" + cursor);

			page.get("content").forEach(event -> visited.add(event.get("sequenceNumber").asLong()));

			assertThat(page.get("pageSize").asInt()).isEqualTo(2);

			cursor = page.get("hasMore").asBoolean() ? page.get("nextCursor").asLong() : null;
			pages++;
		}
		while (cursor != null);

		// Three pages of 2, 2 and 1, covering the log exactly once in append order.
		assertThat(pages).isEqualTo(3);
		assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 5L);
	}

	@Test
	void reportsNoFurtherPageOnTheLastPage() throws Exception {
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");
		append("user-2", "Customer", "cust-2", "USER_LOGIN", "2026-09-14T10:01:00Z");

		JsonNode page = list("/v1/audit/events?pageSize=20");

		assertThat(page.get("content")).hasSize(2);
		assertThat(page.get("hasMore").asBoolean()).isFalse();
		assertThat(page.get("nextCursor").isNull()).isTrue();
	}

	@Test
	void returnsAnEmptyPageWhenTheCursorIsPastTheEndOfTheLog() throws Exception {
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");

		JsonNode page = list("/v1/audit/events?cursor=99");

		assertThat(page.get("content")).isEmpty();
		assertThat(page.get("hasMore").asBoolean()).isFalse();
		assertThat(page.get("nextCursor").isNull()).isTrue();
	}

	@Test
	void doesNotShiftPagesWhenEventsAreAppendedMidWalk() throws Exception {
		for (int i = 1; i <= 4; i++) {
			append("user-a", "Customer", "cust-1", "RECORD_UPDATED", "2026-09-14T10:0" + i + ":00Z");
		}

		JsonNode first = list("/v1/audit/events?pageSize=2");
		assertThat(first.get("nextCursor").asLong()).isEqualTo(2L);

		// An offset-based reader would now re-see or miss rows; a cursor reader cannot.
		append("user-b", "Customer", "cust-9", "USER_LOGIN", "2026-09-14T09:00:00Z");

		JsonNode second = list("/v1/audit/events?pageSize=2&cursor=2");

		assertThat(sequenceNumbers(second)).containsExactly(3L, 4L);
	}

	@Test
	void appliesFiltersAndCursorTogether() throws Exception {
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");
		append("user-2", "Customer", "cust-2", "RECORD_UPDATED", "2026-09-14T10:01:00Z");
		append("user-1", "Order", "ord-7", "RECORD_UPDATED", "2026-09-14T10:02:00Z");
		append("user-1", "Customer", "cust-1", "RECORD_UPDATED", "2026-09-14T10:03:00Z");

		assertThat(sequenceNumbers(list("/v1/audit/events?actorId=user-1")))
				.containsExactly(1L, 3L, 4L);

		assertThat(sequenceNumbers(list("/v1/audit/events?resourceType=Customer&resourceId=cust-1")))
				.containsExactly(1L, 4L);

		assertThat(sequenceNumbers(list("/v1/audit/events?eventType=RECORD_UPDATED")))
				.containsExactly(2L, 3L, 4L);

		// from is inclusive, to is exclusive.
		assertThat(sequenceNumbers(list(
				"/v1/audit/events?from=2026-09-14T10:01:00Z&to=2026-09-14T10:03:00Z")))
				.containsExactly(2L, 3L);

		// Filters are ANDed with each other and with the cursor.
		assertThat(sequenceNumbers(list("/v1/audit/events?actorId=user-1&cursor=1")))
				.containsExactly(3L, 4L);
	}

	@Test
	void rejectsInvalidPaginationParameters() throws Exception {
		mockMvc.perform(get("/v1/audit/events?cursor=not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/v1/audit/events?cursor=-1"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/v1/audit/events?pageSize=0"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/v1/audit/events?pageSize=101"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/v1/audit/events?from=2026-09-14T10:00:00Z&to=2026-09-14T09:00:00Z"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void chainsAppendedEventsAndKeepsTheLogVerifiable() throws Exception {
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");

		// The same event content twice: previousHash differs, so the content hashes differ and
		// the unique constraint on content_hash is not violated.
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");

		JsonNode page = list("/v1/audit/events");
		JsonNode first = page.get("content").get(0);
		JsonNode second = page.get("content").get(1);

		assertThat(first.get("previousHash").asString()).isEqualTo(GENESIS_HASH);
		assertThat(second.get("previousHash").asString())
				.isEqualTo(first.get("contentHash").asString());
		assertThat(second.get("contentHash").asString())
				.isNotEqualTo(first.get("contentHash").asString());

		mockMvc.perform(get("/v1/audit/verify"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.recordsChecked").value(2));
	}

	/**
	 * Regression test: server-assigned timestamps have nanosecond precision in Java but are
	 * stored with microsecond precision, so the hashed value has to be truncated to what the
	 * column can hold. Several records are appended because the rounding only bites for some
	 * fractional values.
	 */
	@Test
	void keepsEventsVerifiableWhenTheServerAssignsTheTimestamp() throws Exception {
		for (int i = 0; i < 10; i++) {
			appendWithoutTimestamp("user-1", "Customer", "cust-1", "USER_LOGIN");
		}

		mockMvc.perform(get("/v1/audit/verify"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.recordsChecked").value(10));
	}

	@Test
	void detectsTamperingWithARecordedEvent() throws Exception {
		append("user-1", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:00:00Z");
		append("user-2", "Customer", "cust-2", "USER_LOGIN", "2026-09-14T10:01:00Z");

		jdbcTemplate.update("UPDATE audit_events SET actor_id = 'attacker' WHERE sequence_number = 1");

		mockMvc.perform(get("/v1/audit/verify"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(false))
				.andExpect(jsonPath("$.firstViolation.sequenceNumber").value(1))
				.andExpect(jsonPath("$.firstViolation.type").value("CONTENT_HASH_MISMATCH"));
	}

	private void append(String actorId, String resourceType, String resourceId, String eventType,
			String timestamp) throws Exception {
		String body = """
				{
				  "eventType": "%s",
				  "actorId": "%s",
				  "resourceType": "%s",
				  "resourceId": "%s",
				  "payload": { "field": "address", "newValue": "456 Oak Ave" },
				  "timestamp": "%s"
				}
				""".formatted(eventType, actorId, resourceType, resourceId, timestamp);

		mockMvc.perform(post("/v1/audit/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());
	}

	private void appendWithoutTimestamp(String actorId, String resourceType, String resourceId,
			String eventType) throws Exception {
		String body = """
				{
				  "eventType": "%s",
				  "actorId": "%s",
				  "resourceType": "%s",
				  "resourceId": "%s",
				  "payload": { "field": "address" }
				}
				""".formatted(eventType, actorId, resourceType, resourceId);

		mockMvc.perform(post("/v1/audit/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());
	}

	private JsonNode list(String uri) throws Exception {
		String body = mockMvc.perform(get(uri))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return objectMapper.readTree(body);
	}

	private List<Long> sequenceNumbers(JsonNode page) {
		List<Long> sequenceNumbers = new ArrayList<>();
		page.get("content").forEach(event -> sequenceNumbers.add(event.get("sequenceNumber").asLong()));
		return sequenceNumbers;
	}

}
