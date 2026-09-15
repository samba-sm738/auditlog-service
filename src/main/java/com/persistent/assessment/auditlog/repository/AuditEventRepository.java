package com.persistent.assessment.auditlog.repository;

import com.persistent.assessment.auditlog.entity.AuditEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

	/**
	 * Reads one keyset ("cursor") page of the audit log in append order.
	 *
	 * <p>The cursor is the sequence number of the last event already seen by the caller, and
	 * paging walks strictly forward from it. Because {@code sequence_number} is unique,
	 * monotonic and never rewritten, this ordering is total and stable: a caller walking the
	 * log can neither skip nor see an event twice, even while new events are being appended.
	 *
	 * <p>Every filter is optional; a {@code null} argument disables that predicate, and the
	 * remaining ones are combined with AND.
	 *
	 * @param cursor exclusive lower bound on the sequence number, or {@code null} to start
	 * from the beginning of the log
	 * @param actorId the actor to filter on, or {@code null} for any actor
	 * @param resourceType the resource type to filter on, or {@code null} for any type
	 * @param resourceId the resource id to filter on, or {@code null} for any resource
	 * @param eventType the event type to filter on, or {@code null} for any type
	 * @param fromTimestamp inclusive lower bound on the event timestamp, or {@code null} for
	 * no lower bound
	 * @param toTimestamp exclusive upper bound on the event timestamp, or {@code null} for no
	 * upper bound
	 * @param limit maximum number of rows to read; callers ask for one row more than the page
	 * size to detect whether a further page exists
	 * @return the matching events, in ascending sequence order
	 */
	@Query("""
			SELECT e
			FROM AuditEvent e
			WHERE (:cursor IS NULL OR e.sequenceNumber > :cursor)
				AND (:actorId IS NULL OR e.actorId = :actorId)
				AND (:resourceType IS NULL OR e.resourceType = :resourceType)
				AND (:resourceId IS NULL OR e.resourceId = :resourceId)
				AND (:eventType IS NULL OR e.eventType = :eventType)
				AND (:fromTimestamp IS NULL OR e.eventTimestamp >= :fromTimestamp)
				AND (:toTimestamp IS NULL OR e.eventTimestamp < :toTimestamp)
			ORDER BY e.sequenceNumber ASC
			""")
	List<AuditEvent> findSlice(
			@Param("cursor") Long cursor,
			@Param("actorId") String actorId,
			@Param("resourceType") String resourceType,
			@Param("resourceId") String resourceId,
			@Param("eventType") String eventType,
			@Param("fromTimestamp") OffsetDateTime fromTimestamp,
			@Param("toTimestamp") OffsetDateTime toTimestamp,
			Limit limit);

	/**
	 * Streams the whole log in append order, for hash chain verification.
	 *
	 * @return every audit event, in ascending sequence order
	 */
	@Query("""
			SELECT e
			FROM AuditEvent e
			ORDER BY e.sequenceNumber ASC
			""")
	List<AuditEvent> findAllInSequence();

	/**
	 * Archives every event recorded before the cutoff with a single bulk {@code DELETE}.
	 *
	 * <p>Filtering on {@code created_at} happens in the database, so arbitrarily many
	 * expired rows can be removed without loading them into application memory.
	 *
	 * @param cutoff exclusive upper bound on the record's {@code created_at} timestamp
	 * @return the number of archived rows
	 */
	@Modifying
	@Query("""
			DELETE
			FROM AuditEvent e
			WHERE e.createdAt < :cutoff
			""")
	int deleteEventsCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);

}
