# AuditLog Service

A **tamper-evident, append-only audit log** built with Spring Boot. Every event is
linked to its predecessor through a SHA-256 hash chain, so any modification,
deletion, or reordering of recorded events is detectable.

## Overview

`auditlog-service` records audit events — who did what, to which resource, and when —
into an immutable log. Each event receives a server-assigned sequence number and a
content hash that incorporates the previous event's hash, forming a verifiable chain
back to a genesis record. The service can verify chain integrity on demand and export
filtered, self-contained bundles that recipients can verify independently.

## Features

- **Tamper-evident hash chaining** — every event's `contentHash` covers its canonical
  content *and* the previous event's hash; tampering breaks the chain.
- **Integrity verification** — `GET /v1/audit/verify` replays the chain and reports
  the first violation found, if any.
- **Cursor (keyset) pagination** — sequence-number-based paging that never skips or
  repeats events and stays fast at any depth; no expensive `COUNT` queries.
- **Filtered exports** — self-contained `audit-bundle-v1` JSON bundles with chain
  anchors, verifiable without access to this service or its database.
- **Sensitive-data redaction** — `accountNumber` fields in event payloads are replaced
  with `[REDACTED]` before hashing and persistence.
- **Scheduled archival** — a background scheduler archives events older than the
  configured retention period.
- **Contract-first API** — API interfaces and models are generated from
  `openapi.yml` at build time; Swagger UI serves the same spec.

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 4.1.1 |
| Build tool | Maven 3.9+ |
| Database | H2 (in-memory) |
| API spec | OpenAPI 3.1 (openapi-generator) |
| Docs UI | springdoc-openapi / Swagger UI |
| Coverage | JaCoCo (90% line-coverage gate) |
| Boilerplate | Lombok |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/audit/events` | Append an audit event to the log |
| `GET` | `/v1/audit/events` | Query events (filters + cursor pagination) |
| `GET` | `/v1/audit/verify` | Verify hash-chain integrity |
| `GET` | `/v1/audit/export` | Export a verifiable bundle filtered by `resourceId` **or** `actorId` |

Interactive docs: `http://localhost:8080/swagger-ui/index.html`

### Quick example

```bash
# record an event
curl -X POST http://localhost:8080/v1/audit/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user-1",
       "resourceType":"Session","resourceId":"sess-9",
       "payload":{"ip":"10.0.0.1"}}'

# walk the log
curl "http://localhost:8080/v1/audit/events?pageSize=20"

# verify integrity
curl http://localhost:8080/v1/audit/verify
# {"valid":true,"recordsChecked":5,"firstViolation":null}
```

## Getting Started

```bash
mvn clean install        # build (generates API code, runs tests)
mvn spring-boot:run      # run on http://localhost:8080
mvn verify               # tests + JaCoCo 90% coverage gate
```

Prerequisites: **JDK 25** and **Maven 3.9+**. For detailed setup on Windows/macOS,
IDE configuration, and troubleshooting, see [SETUP.md](SETUP.md).

## Configuration

Runtime configuration lives in `src/main/resources/application.yaml`:

| Property | Default | Description |
|----------|---------|-------------|
| `audit.events.retention-days` | `30` | Events older than this are archived |
| `audit.events.archive-interval-ms` | `86400000` | Delay between archive runs |
| `audit.events.archive-initial-delay-ms` | `60000` | Delay before first archive run |
| `spring.datasource.url` | `jdbc:h2:mem:testdb` | In-memory H2 datasource |

The H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:testdb`, user `sa`, empty password).

## How It Works

```text
POST /v1/audit/events
       │
       ▼
AuditEventService.append()
       │  1. Lock the audit_chain_state singleton row (chain tip)
       │  2. Assign next sequence number + previous hash
       │  3. Redact accountNumber from payload
       │  4. Canonicalize event (fixed field order, sorted payload keys,
       │     UTC timestamp) → SHA-256 contentHash
       │  5. Persist event, advance chain tip
       ▼
 audit_events table  ──►  tamper-evident chain:
 event[n].previousHash == event[n-1].contentHash
```

Key design points:

- **Schema-owned SQL** — `sql/schema.sql` owns the schema, constraints, indexes,
  and the genesis chain-state row; Hibernate `ddl-auto` is `none`.
- **Concurrency-safe appends** — the chain tip row is locked during append, so
  concurrent writers cannot claim the same sequence number or previous hash.
- **Deterministic hashing** — `HashService` uses a dedicated `ObjectMapper` and a
  fixed canonicalization so stored hashes remain verifiable forever.

## Project Structure

```text
src/main/java/com/persistent/assessment/auditlog/
├── controller/   REST controller implementing the generated API
├── entity/       JPA entities (AuditEvent, AuditChainState)
├── repository/   Spring Data repositories
├── service/      append, query, verify, export, hash, redact, archive
├── scheduler/    retention-based archival job
└── exception/    error types + global handler

src/main/resources/
├── static/openapi.yml   contract-first API spec
├── sql/schema.sql       schema, constraints, indexes, genesis row
└── application.yaml     runtime configuration
```

## Documentation

- [SETUP.md](SETUP.md) — local setup, build, run, IDE, troubleshooting
- [docs/audit-events-pagination.md](docs/audit-events-pagination.md) — cursor pagination design
- [docs/audit-export.md](docs/audit-export.md) — export bundle format and verification
- [src/main/resources/static/openapi.yml](src/main/resources/static/openapi.yml) — full API contract
