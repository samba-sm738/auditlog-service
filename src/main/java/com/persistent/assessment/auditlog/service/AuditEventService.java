package com.persistent.assessment.auditlog.service;

import com.persistent.assessment.auditlog.entity.AuditChainState;
import com.persistent.assessment.auditlog.entity.AuditEvent;
import com.persistent.assessment.auditlog.exception.AuditEventNotFoundException;
import com.persistent.assessment.auditlog.model.AuditEventRequest;
import com.persistent.assessment.auditlog.repository.AuditChainStateRepository;
import com.persistent.assessment.auditlog.repository.AuditEventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

	private final AuditEventRepository eventRepository;

	private final AuditChainStateRepository chainStateRepository;

	private final HashService hashService;

	public AuditEventService(AuditEventRepository eventRepository,
			AuditChainStateRepository chainStateRepository, HashService hashService) {
		this.eventRepository = eventRepository;
		this.chainStateRepository = chainStateRepository;
		this.hashService = hashService;
	}

	@Transactional
	public AuditEvent append(AuditEventRequest request) {
		AuditChainState chainState =
                chainStateRepository.getChainTip();

        long sequenceNumber =
                chainState.getNextSequenceNumber();

        String previousHash =
                chainState.getLatestHash();

        OffsetDateTime timestamp =
                OffsetDateTime.now(ZoneOffset.UTC);

        String canonicalEvent =
                hashService.canonicalize(
                        request.getEventType(),
                        request.getActorId(),
                        request.getResourceType(),
                        request.getResourceId(),
                        request.getPayload().toString(),
                        timestamp
                );

        String contentHash =
                hashService.hash(canonicalEvent);

        AuditEvent event = new AuditEvent();

        event.setId(UUID.randomUUID());
        event.setSequenceNumber(sequenceNumber);
        event.setEventType(request.getEventType());
        event.setActorId(request.getActorId());
        event.setResourceType(request.getResourceType());
        event.setResourceId(request.getResourceId());
        event.setPayload(request.getPayload());
        event.setEventTimestamp(timestamp);
        event.setPreviousHash(previousHash);
        event.setContentHash(contentHash);
        event.setCreatedAt(timestamp);

        AuditEvent saved =
                eventRepository.save(event);

        chainState.advance(contentHash);

        chainStateRepository.save(chainState);

        return saved;
	}

	@Transactional(readOnly = true)
	public AuditEvent findById(UUID id) {
		return eventRepository.findById(id)
				.orElseThrow(() -> new AuditEventNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<AuditEvent> findPage(Pageable pageable) {
		return eventRepository.findAll(pageable);
	}

	public List<AuditEvent> findAllInSequence() {
		return eventRepository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));
	}

	@Transactional(readOnly = true)
	public AuditChainTip getChainTip() {
		AuditChainState state = chainStateRepository.findById(true)
				.orElseThrow(() -> new IllegalStateException("Audit chain state is missing"));

		return new AuditChainTip(state.getNextSequenceNumber(), state.getLatestHash());
	}

	public record AuditChainTip(
			long nextSequenceNumber,
			String latestHash
	) {
	}

}
