
/* create audit events table */
CREATE TABLE audit_events (
    id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,

    payload JSON NOT NULL,

    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,

    previous_hash VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_audit_events
        PRIMARY KEY (id),

    CONSTRAINT uq_audit_events_sequence
        UNIQUE (sequence_number),

    CONSTRAINT uq_audit_events_content_hash
        UNIQUE (content_hash),

    CONSTRAINT chk_audit_events_sequence_positive
        CHECK (sequence_number > 0),

    CONSTRAINT chk_audit_events_hash_length
        CHECK (
            LENGTH(previous_hash) = 64
            AND LENGTH(content_hash) = 64
        )
);

/* managing latest hash and sequence number is for last value in the audit_events */
CREATE TABLE audit_chain_state (
    id BOOLEAN NOT NULL,

    next_sequence_number BIGINT NOT NULL,
    latest_hash VARCHAR(64) NOT NULL,

    CONSTRAINT pk_audit_chain_state
        PRIMARY KEY (id),

    CONSTRAINT chk_audit_chain_state_singleton
        CHECK (id = TRUE),

    CONSTRAINT chk_audit_chain_state_sequence
        CHECK (next_sequence_number > 0),

    CONSTRAINT chk_audit_chain_state_hash_length
        CHECK (LENGTH(latest_hash) = 64)
);