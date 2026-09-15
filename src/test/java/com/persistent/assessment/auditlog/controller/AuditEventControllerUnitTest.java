package com.persistent.assessment.auditlog.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.model.AuditEventPage;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.model.AuditEventResponse;
import com.persistent.assessment.auditlog.model.AuditVerificationResponse;
import com.persistent.assessment.auditlog.service.AuditEventService;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventQuery;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventSlice;
import com.persistent.assessment.auditlog.service.AuditVerificationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventControllerUnitTest {

	@Mock
	private AuditEventService eventService;

	@Mock
	private AuditVerificationService verificationService;

	@InjectMocks
	private AuditEventController controller;

	private AuditEvent event() {
		AuditEvent event = new AuditEvent();
		event.setId(UUID.randomUUID());
		event.setSequenceNumber(1L);
		event.setEventType("USER_LOGIN");
		event.setActorId("user-1");
		event.setResourceType("Customer");
		event.setResourceId("cust-1");
		event.setPayload(Map.of("field", "address"));
		event.setEventTimestamp(OffsetDateTime.parse("2026-09-14T10:00:00Z"));
		event.setPreviousHash("0".repeat(64));
		event.setContentHash("a".repeat(64));
		event.setCreatedAt(OffsetDateTime.parse("2026-09-14T10:00:01Z"));
		return event;
	}

	@Test
	void createAuditEventReturnsCreatedWithMappedBody() {
		AuditEvent event = event();
		AuditEventRequest request = new AuditEventRequest()
				.eventType("USER_LOGIN")
				.actorId("user-1")
				.resourceType("Customer")
				.resourceId("cust-1");
		when(eventService.append(request)).thenReturn(event);

		ResponseEntity<AuditEventResponse> response = controller.createAuditEvent(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		AuditEventResponse body = response.getBody();
		assertThat(body.getId()).isEqualTo(event.getId());
		assertThat(body.getSequenceNumber()).isEqualTo(1L);
		assertThat(body.getEventType()).isEqualTo("USER_LOGIN");
		assertThat(body.getActorId()).isEqualTo("user-1");
		assertThat(body.getResourceType()).isEqualTo("Customer");
		assertThat(body.getResourceId()).isEqualTo("cust-1");
		assertThat(body.getPayload()).containsEntry("field", "address");
		assertThat(body.getTimestamp()).isEqualTo(event.getEventTimestamp());
		assertThat(body.getPreviousHash()).isEqualTo("0".repeat(64));
		assertThat(body.getContentHash()).isEqualTo("a".repeat(64));
	}

	@Test
	void listAuditEventsPassesQueryAndMapsPage() {
		AuditEvent event = event();
		when(eventService.findSlice(any(AuditEventQuery.class)))
				.thenReturn(new AuditEventSlice(List.of(event), 1L, true));

		OffsetDateTime from = OffsetDateTime.parse("2026-09-14T09:00:00Z");
		OffsetDateTime to = OffsetDateTime.parse("2026-09-14T11:00:00Z");

		ResponseEntity<AuditEventPage> response = controller.listAuditEvents(
				"user-1", "Customer", "cust-1", "USER_LOGIN", from, to, 0L, 5);

		ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
		verify(eventService).findSlice(captor.capture());
		AuditEventQuery query = captor.getValue();
		assertThat(query.actorId()).isEqualTo("user-1");
		assertThat(query.resourceType()).isEqualTo("Customer");
		assertThat(query.resourceId()).isEqualTo("cust-1");
		assertThat(query.eventType()).isEqualTo("USER_LOGIN");
		assertThat(query.from()).isEqualTo(from);
		assertThat(query.to()).isEqualTo(to);
		assertThat(query.cursor()).isEqualTo(0L);
		assertThat(query.pageSize()).isEqualTo(5);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		AuditEventPage page = response.getBody();
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().getFirst().getSequenceNumber()).isEqualTo(1L);
		assertThat(page.getPageSize()).isEqualTo(5);
		assertThat(page.getNextCursor()).isEqualTo(1L);
		assertThat(page.getHasMore()).isTrue();
	}

	@Test
	void listAuditEventsDefaultsPageSize() {
		when(eventService.findSlice(any(AuditEventQuery.class)))
				.thenReturn(new AuditEventSlice(List.of(), null, false));

		ResponseEntity<AuditEventPage> response = controller.listAuditEvents(
				null, null, null, null, null, null, null, null);

		ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
		verify(eventService).findSlice(captor.capture());
		assertThat(captor.getValue().pageSize()).isEqualTo(20);

		assertThat(response.getBody().getPageSize()).isEqualTo(20);
		assertThat(response.getBody().getContent()).isEmpty();
		assertThat(response.getBody().getHasMore()).isFalse();
	}

	@Test
	void listAuditEventsRejectsFromNotBeforeTo() {
		OffsetDateTime from = OffsetDateTime.parse("2026-09-14T10:00:00Z");
		OffsetDateTime to = OffsetDateTime.parse("2026-09-14T09:00:00Z");

		assertThatThrownBy(() -> controller.listAuditEvents(
				null, null, null, null, from, to, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("from must be strictly before to");

		assertThatThrownBy(() -> controller.listAuditEvents(
				null, null, null, null, from, from, null, null))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(eventService);
	}

	@Test
	void verifyAuditLogReturnsServiceResult() {
		AuditVerificationResponse verification = new AuditVerificationResponse(true, 3L);
		when(verificationService.verify()).thenReturn(verification);

		ResponseEntity<AuditVerificationResponse> response = controller.verifyAuditLog();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(verification);
	}

}
