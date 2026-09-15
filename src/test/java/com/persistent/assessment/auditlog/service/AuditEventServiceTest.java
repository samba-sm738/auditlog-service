package com.persistent.assessment.auditlog.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import com.persistent.assessment.auditlog.entity.AuditChainState;
import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.exception.AuditEventNotFoundException;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.repository.AuditChainStateRepository;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditChainTip;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventQuery;
import com.persistent.assessment.auditlog.service.AuditEventService.AuditEventSlice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

	private static final String GENESIS_HASH = "0".repeat(64);

	@Mock
	private AuditEventRepository eventRepository;

	@Mock
	private AuditChainStateRepository chainStateRepository;

	@Mock
	private HashService hashService;

	@InjectMocks
	private AuditEventService service;

	private AuditChainState chainState(long nextSequence, String latestHash) {
		AuditChainState state = new AuditChainState();
		state.setId(true);
		state.setNextSequenceNumber(nextSequence);
		state.setLatestHash(latestHash);
		return state;
	}

	private AuditEventRequest request() {
		return new AuditEventRequest()
				.eventType("USER_LOGIN")
				.actorId("user-1")
				.resourceType("Customer")
				.resourceId("cust-1")
				.payload(Map.of("field", "address"))
				.timestamp(OffsetDateTime.parse("2026-09-14T12:00:00.123456789+02:00"));
	}

	private void stubChainTip(AuditChainState state) {
		when(chainStateRepository.getChainTip()).thenReturn(state);
		lenient().when(hashService.canonicalize(anyString(), anyString(), anyString(), anyString(),
				anyMap(), anyString(), any(OffsetDateTime.class))).thenReturn("canonical-event");
		lenient().when(hashService.hash("canonical-event")).thenReturn("a".repeat(64));
		lenient().when(eventRepository.save(any(AuditEvent.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void appendAssignsSequenceNumberAndChainsOntoLatestHash() {
		AuditChainState state = chainState(7, "b".repeat(64));
		stubChainTip(state);

		AuditEvent saved = service.append(request());

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getSequenceNumber()).isEqualTo(7L);
		assertThat(saved.getPreviousHash()).isEqualTo("b".repeat(64));
		assertThat(saved.getContentHash()).isEqualTo("a".repeat(64));
		assertThat(saved.getEventType()).isEqualTo("USER_LOGIN");
		assertThat(saved.getActorId()).isEqualTo("user-1");
		assertThat(saved.getResourceType()).isEqualTo("Customer");
		assertThat(saved.getResourceId()).isEqualTo("cust-1");
		assertThat(saved.getPayload()).containsEntry("field", "address");
		assertThat(saved.getCreatedAt()).isNotNull();

		// The timestamp is normalized to UTC and truncated to microseconds before hashing.
		assertThat(saved.getEventTimestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(saved.getEventTimestamp().toInstant().toString())
				.isEqualTo("2026-09-14T10:00:00.123456Z");

		verify(hashService).canonicalize(eq("USER_LOGIN"), eq("user-1"), eq("Customer"),
				eq("cust-1"), eq(Map.of("field", "address")), eq("b".repeat(64)),
				eq(OffsetDateTime.parse("2026-09-14T10:00:00.123456Z")));
		verify(hashService).hash("canonical-event");

		// The chain tip advances to the new record.
		assertThat(state.getNextSequenceNumber()).isEqualTo(8L);
		assertThat(state.getLatestHash()).isEqualTo("a".repeat(64));
		verify(chainStateRepository).save(state);
	}

	@Test
	void appendDefaultsMissingTimestampAndPayload() {
		AuditChainState state = chainState(1, GENESIS_HASH);
		stubChainTip(state);

		AuditEventRequest request = request().timestamp(null).payload(null);

		AuditEvent saved = service.append(request);

		assertThat(saved.getEventTimestamp()).isNotNull();
		assertThat(saved.getEventTimestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(saved.getEventTimestamp().getNano() % 1_000).isZero();
		assertThat(saved.getPayload()).isEmpty();

		verify(hashService).canonicalize(anyString(), anyString(), anyString(), anyString(),
				eq(Map.of()), eq(GENESIS_HASH), any(OffsetDateTime.class));
	}

	@Test
	void appendFailsWhenChainStateIsMissing() {
		when(chainStateRepository.getChainTip()).thenReturn(null);

		assertThatThrownBy(() -> service.append(request()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("genesis");

		verify(eventRepository, never()).save(any());
		verify(chainStateRepository, never()).save(any());
	}

	@Test
	void findByIdReturnsTheEvent() {
		UUID id = UUID.randomUUID();
		AuditEvent event = new AuditEvent();
		event.setId(id);
		when(eventRepository.findById(id)).thenReturn(Optional.of(event));

		assertThat(service.findById(id)).isSameAs(event);
	}

	@Test
	void findByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(eventRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(id))
				.isInstanceOf(AuditEventNotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void findSliceReportsMorePagesAndNextCursor() {
		AuditEvent first = eventWithSequence(1L);
		AuditEvent second = eventWithSequence(2L);
		AuditEvent extra = eventWithSequence(3L);

		when(eventRepository.findSlice(any(), any(), any(), any(), any(), any(), any(),
				any(Limit.class)))
				.thenReturn(List.of(first, second, extra));

		AuditEventQuery query = new AuditEventQuery("user-1", "Customer", "cust-1",
				"USER_LOGIN", null, null, null, 2);

		AuditEventSlice slice = service.findSlice(query);

		assertThat(slice.events()).containsExactly(first, second);
		assertThat(slice.hasMore()).isTrue();
		assertThat(slice.nextCursor()).isEqualTo(2L);

		ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
		verify(eventRepository).findSlice(isNull(), eq("user-1"), eq("Customer"), eq("cust-1"),
				eq("USER_LOGIN"), isNull(), isNull(), limit.capture());
		assertThat(limit.getValue().max()).isEqualTo(3);
	}

	@Test
	void findSliceReturnsWholeResultOnLastPage() {
		AuditEvent first = eventWithSequence(1L);

		when(eventRepository.findSlice(any(), any(), any(), any(), any(), any(), any(),
				any(Limit.class)))
				.thenReturn(List.of(first));

		AuditEventSlice slice = service.findSlice(new AuditEventQuery(
				null, null, null, null, null, null, 5L, 20));

		assertThat(slice.events()).containsExactly(first);
		assertThat(slice.hasMore()).isFalse();
		assertThat(slice.nextCursor()).isNull();
	}

	@Test
	void findSliceReturnsEmptyPage() {
		when(eventRepository.findSlice(any(), any(), any(), any(), any(), any(), any(),
				any(Limit.class)))
				.thenReturn(List.of());

		AuditEventSlice slice = service.findSlice(new AuditEventQuery(
				null, null, null, null, null, null, null, 10));

		assertThat(slice.events()).isEmpty();
		assertThat(slice.hasMore()).isFalse();
		assertThat(slice.nextCursor()).isNull();
	}

	@Test
	void getChainTipReturnsCurrentTip() {
		when(chainStateRepository.findById(true))
				.thenReturn(Optional.of(chainState(9, "c".repeat(64))));

		AuditChainTip tip = service.getChainTip();

		assertThat(tip.nextSequenceNumber()).isEqualTo(9L);
		assertThat(tip.latestHash()).isEqualTo("c".repeat(64));
	}

	@Test
	void getChainTipThrowsWhenMissing() {
		when(chainStateRepository.findById(true)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getChainTip())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("chain state");
	}

	private AuditEvent eventWithSequence(long sequenceNumber) {
		AuditEvent event = new AuditEvent();
		event.setId(UUID.randomUUID());
		event.setSequenceNumber(sequenceNumber);
		return event;
	}

}
