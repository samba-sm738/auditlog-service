package com.persistent.assessment.auditlog.repository;

import com.persistent.assessment.auditlog.entity.AuditChainState;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditChainStateRepository extends JpaRepository<AuditChainState, Boolean> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM AuditChainState s
        WHERE s.id = true
    """)
	AuditChainState getChainTip();

}
