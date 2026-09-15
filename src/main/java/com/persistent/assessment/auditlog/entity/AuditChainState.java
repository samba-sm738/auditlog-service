package com.persistent.assessment.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_chain_state")
@Getter
@Setter
public class AuditChainState {

	@Id
	@Column(name = "id", nullable = false)
	private Boolean id;

	@Column(name = "next_sequence_number", nullable = false)
	private Long nextSequenceNumber;

	@Column(name = "latest_hash", nullable = false, length = 64)
	private String latestHash;

	public void advance(String newHash) {
        this.nextSequenceNumber++;
        this.latestHash = newHash;
    }

}
