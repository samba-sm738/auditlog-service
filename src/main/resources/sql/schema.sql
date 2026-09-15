
/* Every statement is idempotent, because this script is the single owner of the schema
   (Hibernate ddl-auto is none) and is replayed on every startup. */

/* create audit events table */
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID NOT NULL,
    sequence_number BIGINT NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,

    payload JSON NOT NULL,

    /* precision is explicit because the event timestamp is hashed: the value written must
       be exactly the value read back, or the record stops verifying */
    event_timestamp TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    previous_hash VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

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

/* supports keyset pagination: the cursor scans sequence_number, and the filters
   below narrow the scan before the limit is applied */
CREATE INDEX IF NOT EXISTS idx_audit_events_actor
    ON audit_events (actor_id, sequence_number);

CREATE INDEX IF NOT EXISTS idx_audit_events_resource
    ON audit_events (resource_type, resource_id, sequence_number);

CREATE INDEX IF NOT EXISTS idx_audit_events_event_type
    ON audit_events (event_type, sequence_number);

CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp
    ON audit_events (event_timestamp, sequence_number);

/* managing latest hash and sequence number is for last value in the audit_events */
CREATE TABLE IF NOT EXISTS audit_chain_state (
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

/* genesis row: the chain starts at sequence number 1 and links back to 64 zeroes.
   Appending reads and locks this row, so it has to exist before the first event. */
MERGE INTO audit_chain_state (id, next_sequence_number, latest_hash)
    KEY (id)
    VALUES (
        TRUE,
        1,
        '0000000000000000000000000000000000000000000000000000000000000000'
    );