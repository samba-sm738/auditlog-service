package com.persistent.assessment.auditlog.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code /v1/audit/export}, exercising real persistence and real
 * hash chaining through the HTTP API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditExportControllerTests {

	private static final String GENESIS_HASH = "0".repeat(64);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@BeforeEach
	void resetAuditLog() {
		jdbcTemplate.execute("DELETE FROM audit_events");
		jdbcTemplate.update(
				"UPDATE audit_chain_state SET next_sequence_number = 1, latest_hash = ?",
				GENESIS_HASH);
	}

	/**
	 * Seeds an interleaved log: user-a writes cust-1 (seq 1, 3) and cust-3 (seq 5),
	 * user-b writes cust-2 (seq 2) and cust-1 (seq 4). A resource export of cust-1 is
	 * therefore the non-contiguous sequence 1, 3, 4.
	 */
	private void seedInterleavedLog() throws Exception {
		append("user-a", "Customer", "cust-1", "USER_LOGIN", "2026-09-14T10:01:00Z");
		append("user-b", "Customer", "cust-2", "RECORD_UPDATED", "2026-09-14T10:02:00Z");
		append("user-a", "Customer", "cust-1", "RECORD_UPDATED", "2026-09-14T10:03:00Z");
		append("user-b", "Customer", "cust-1", "RECORD_UPDATED", "2026-09-14T10:04:00Z");
		append("user-a", "Customer", "cust-3", "USER_LOGOUT", "2026-09-14T10:05:00Z");
	}

	private void append(String actorId, String resourceType, String resourceId,
			String eventType, String timestamp) throws Exception {
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

	private JsonNode exportBundle(String uri) throws Exception {
		MvcResult result = mockMvc.perform(get(uri))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	@Test
	void exportsByResourceIdAsVerifiableBundle() throws Exception {
		seedInterleavedLog();

		JsonNode bundle = exportBundle("/v1/audit/export?resourceId=cust-1");

		assertThat(bundle.get("format").asString()).isEqualTo("audit-bundle-v1");
		assertThat(bundle.get("exportedAt").asString()).isNotBlank();
		assertThat(bundle.get("filter").get("type").asString()).isEqualTo("resourceId");
		assertThat(bundle.get("filter").get("value").asString()).isEqualTo("cust-1");

		JsonNode chain = bundle.get("chain");
		assertThat(chain.get("algorithm").asString()).isEqualTo("SHA-256");
		assertThat(chain.get("recordCount").asLong()).isEqualTo(3);

		JsonNode records = bundle.get("records");
		assertThat(records).hasSize(3);
		assertThat(records.get(0).get("sequenceNumber").asLong()).isEqualTo(1L);
		assertThat(records.get(1).get("sequenceNumber").asLong()).isEqualTo(3L);
		assertThat(records.get(2).get("sequenceNumber").asLong()).isEqualTo(4L);

		// The segment anchors pin the export to the global chain: the first record's
		// predecessor is the genesis hash (excluded records keep their real link, e.g.
		// seq 3 points at excluded seq 2), and the last record's hash is the anchor.
		assertThat(chain.get("firstRecordId").asString())
				.isEqualTo(records.get(0).get("id").asString());
		assertThat(chain.get("firstPreviousHash").asString()).isEqualTo(GENESIS_HASH);
		assertThat(records.get(1).get("previousHash").asString())
				.isNotEqualTo(records.get(0).get("contentHash").asString());
		assertThat(chain.get("lastRecordHash").asString())
				.isEqualTo(records.get(2).get("contentHash").asString());
	}

	@Test
	void exportsByActorIdAsVerifiableBundle() throws Exception {
		seedInterleavedLog();

		JsonNode bundle = exportBundle("/v1/audit/export?actorId=user-a");

		assertThat(bundle.get("filter").get("type").asString()).isEqualTo("actorId");
		assertThat(bundle.get("filter").get("value").asString()).isEqualTo("user-a");
		assertThat(bundle.get("chain").get("recordCount").asLong()).isEqualTo(3);

		JsonNode records = bundle.get("records");
		assertThat(records).hasSize(3);
		assertThat(records.get(0).get("sequenceNumber").asLong()).isEqualTo(1L);
		assertThat(records.get(1).get("sequenceNumber").asLong()).isEqualTo(3L);
		assertThat(records.get(2).get("sequenceNumber").asLong()).isEqualTo(5L);
		records.forEach(record ->
				assertThat(record.get("actorId").asString()).isEqualTo("user-a"));
	}

	@Test
	void rejectsMissingOrAmbiguousFilter() throws Exception {
		mockMvc.perform(get("/v1/audit/export"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/v1/audit/export?resourceId=cust-1&actorId=user-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/v1/audit/export?resourceId= "))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsValidEmptyBundleWhenNothingMatches() throws Exception {
		seedInterleavedLog();

		JsonNode bundle = exportBundle("/v1/audit/export?resourceId=cust-999");

		assertThat(bundle.get("chain").get("recordCount").asLong()).isZero();
		assertThat(bundle.get("records")).isEmpty();
		assertThat(bundle.get("chain").get("firstPreviousHash").isNull()).isTrue();
		assertThat(bundle.get("chain").get("lastRecordHash").isNull()).isTrue();
	}

}
