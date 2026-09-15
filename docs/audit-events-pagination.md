# Querying the audit log: `GET /v1/audit/events`

The audit log is an append-only, hash-chained sequence of events. Reading it is
**cursor (keyset) paginated** on `sequenceNumber`, not offset paginated.

## Why a cursor and not `page` / `offset`

`sequence_number` is assigned by the server, is unique, increases by exactly one per
event, and is never rewritten. That makes it a perfect pagination key:

- **Nothing is skipped or repeated.** With `?page=N`, events appended while a client is
  walking the log shift the rows across page boundaries, so the client silently sees an
  event twice or misses one entirely. For an audit log, silently missing a record is the
  one failure mode you cannot accept. A cursor asks for "everything after sequence 42",
  which cannot shift.
- **It stays fast at depth.** `OFFSET 500000` makes the database count and discard half a
  million rows on every request. `WHERE sequence_number > 500000 ... LIMIT 21` seeks
  straight into the index, so page one million costs the same as page one.
- **No total count.** Counting every matching row on each request is the expensive part of
  offset paging, and the answer is stale the moment it is returned on a log that only
  grows. The response reports `hasMore` instead, which is what a client actually needs to
  decide whether to keep reading.

## Request

| Parameter      | Type              | Default | Notes |
| -------------- | ----------------- | ------- | ----- |
| `cursor`       | integer (int64)   | —       | Sequence number of the last event you have already seen. Returns events with a **strictly greater** sequence number. Omit for the first page. Must be `>= 0`. |
| `pageSize`     | integer (int32)   | `20`    | Maximum events per page, `1..100`. |
| `actorId`      | string            | —       | Exact match on who caused the event. |
| `resourceType` | string            | —       | Exact match on the type of resource affected. |
| `resourceId`   | string            | —       | Exact match on the specific resource affected. |
| `eventType`    | string            | —       | Exact match on what happened. |
| `from`         | date-time         | —       | Event timestamp lower bound, **inclusive**. |
| `to`           | date-time         | —       | Event timestamp upper bound, **exclusive**. |

All filters are optional and are combined with `AND`. They are applied *together with* the
cursor, so a filtered walk is paginated the same way as an unfiltered one.

## Response

```json
{
  "content": [ /* AuditEventResponse, ascending sequenceNumber */ ],
  "pageSize": 2,
  "nextCursor": 1024,
  "hasMore": true
}
```

- `content` — the events on this page, always in ascending sequence (append) order.
- `pageSize` — the page size that was applied.
- `hasMore` — `true` when at least one further matching event exists after this page.
- `nextCursor` — the value to pass back as `cursor` for the next page. It is the sequence
  number of the last event in `content`, and is **`null` when `hasMore` is `false`**.

Stop when `hasMore` is `false`. Never construct a cursor yourself; echo back what the
previous response gave you.

## Walking the whole log

```bash
# first page
curl -s "http://localhost:8080/v1/audit/events?pageSize=2"
```

```json
{
  "content": [ { "sequenceNumber": 1, "...": "..." }, { "sequenceNumber": 2, "...": "..." } ],
  "pageSize": 2,
  "nextCursor": 2,
  "hasMore": true
}
```

```bash
# next page: feed nextCursor back in
curl -s "http://localhost:8080/v1/audit/events?pageSize=2&cursor=2"
```

```json
{
  "content": [ { "sequenceNumber": 3, "...": "..." } ],
  "pageSize": 2,
  "nextCursor": null,
  "hasMore": false
}
```

A complete client loop:

```bash
cursor=""
while : ; do
  page=$(curl -s "http://localhost:8080/v1/audit/events?pageSize=100${cursor:+&cursor=$cursor}")
  echo "$page" | jq -r '.content[] | "\(.sequenceNumber) \(.eventType) \(.actorId)"'
  [ "$(echo "$page" | jq -r '.hasMore')" = "true" ] || break
  cursor=$(echo "$page" | jq -r '.nextCursor')
done
```

## Filtering

```bash
# everything one actor did
curl -s "http://localhost:8080/v1/audit/events?actorId=user-42"

# the history of one specific resource
curl -s "http://localhost:8080/v1/audit/events?resourceType=Customer&resourceId=cust-1001"

# one event type inside a time window (from inclusive, to exclusive)
curl -s "http://localhost:8080/v1/audit/events?eventType=RECORD_UPDATED\
&from=2026-09-01T00:00:00Z&to=2026-10-01T00:00:00Z"

# filters and cursor together: page 2 of one actor's history
curl -s "http://localhost:8080/v1/audit/events?actorId=user-42&pageSize=20&cursor=1024"
```

## Tailing the log for new events

Because `nextCursor` is `null` on the last page, a poller must remember the highest
sequence number it has seen and reuse it as the cursor on the next poll:

```bash
# remember the last sequenceNumber you processed, then poll with it
curl -s "http://localhost:8080/v1/audit/events?cursor=$last_seen"
```

## Errors

All failures return `ErrorResponse` (`{ "message": ..., "code": ... }`).

| Status | When | Example |
| ------ | ---- | ------- |
| `400` | `cursor` is not an integer | `{"message":"cursor has an invalid value","code":"VALIDATION_FAILED"}` |
| `400` | `cursor` is negative | `VALIDATION_FAILED` |
| `400` | `pageSize` outside `1..100` | `{"message":"listAuditEvents.pageSize must be less than or equal to 100","code":"VALIDATION_FAILED"}` |
| `400` | `from` is not strictly before `to` | `{"message":"from must be strictly before to","code":"VALIDATION_FAILED"}` |

A `cursor` past the end of the log is **not** an error: it returns an empty page with
`hasMore: false`.

## How it works internally

1. `AuditEventController.listAuditEvents` validates the `from`/`to` relationship (the rest
   of the bounds are enforced by the generated contract) and builds an `AuditEventQuery`.
2. `AuditEventService.findSlice` asks the repository for `pageSize + 1` rows. The extra row
   is how `hasMore` is determined without a `COUNT`; it is dropped before the response is
   built.
3. `AuditEventRepository.findSlice` runs a single JPQL query where each `null` argument
   disables its predicate, ordered by `sequenceNumber ASC` and bounded by a `Limit`.
   Composite indexes in `sql/schema.sql` keep every filtered variant on an index seek.

## Related: how events get into the log

```bash
curl -s -X POST http://localhost:8080/v1/audit/events \
  -H 'Content-Type: application/json' \
  -d '{
        "eventType": "RECORD_UPDATED",
        "actorId": "user-42",
        "resourceType": "Customer",
        "resourceId": "cust-1001",
        "payload": { "field": "address", "newValue": "456 Oak Ave" }
      }'
```

The server assigns `id`, `sequenceNumber`, `previousHash` and `contentHash`. `timestamp` is
optional: supply it to record when the event actually occurred, or omit it and the server
records the current time. Either way it is normalized to UTC and truncated to microseconds
(the precision of the column) before it is hashed, so that the stored value is exactly the
hashed value.

`GET /v1/audit/verify` walks the chain and recomputes it:

```bash
curl -s http://localhost:8080/v1/audit/verify
# {"valid":true,"recordsChecked":5,"firstViolation":null}
```

## Running locally

```bash
mvn spring-boot:run     # http://localhost:8080
mvn test                # includes the cursor pagination tests
```

The schema, its constraints, its indexes and the genesis chain-state row all live in
`src/main/resources/sql/schema.sql`, which is replayed idempotently on every startup.
Hibernate's `ddl-auto` is deliberately `none` so that it never recreates those tables.
