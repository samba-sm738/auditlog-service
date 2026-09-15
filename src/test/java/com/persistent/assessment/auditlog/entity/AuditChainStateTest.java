package com.persistent.assessment.auditlog.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuditChainStateTest {

	@Test
	void advanceIncrementsSequenceAndStoresNewHash() {
		AuditChainState state = new AuditChainState();
		state.setId(true);
		state.setNextSequenceNumber(41L);
		state.setLatestHash("a".repeat(64));

		state.advance("b".repeat(64));

		assertThat(state.getId()).isTrue();
		assertThat(state.getNextSequenceNumber()).isEqualTo(42L);
		assertThat(state.getLatestHash()).isEqualTo("b".repeat(64));
	}

}
