# Exporting audit events: `GET /v1/audit/export`

`/v1/audit/export` returns every audit event matching **exactly one** filter —
`resourceId` or `actorId` — as a versioned, self-contained bundle that a
recipient can verify without access to this service or its database.

## Request

```bash
# the history of one resource
curl -s "http://localhost:8080/v1/audit/export?resourceId=cust-1001"

# everything one actor caused
curl -s "http://localhost:8080/v1/audit/export?actorId=user-42"
```

Supplying both filters, or neither, is rejected with `400`
(`VALIDATION_FAILED`). Blank values count as absent. When nothing matches, the
export is still a valid bundle with `recordCount: 0` and no chain anchors.

## Bundle format (`audit-bundle-v1`)

```json
{
  "format": "audit-bundle-v1",
  "exportedAt": "2026-09-15T08:30:00Z",
  "filter": { "type": "resourceId", "value": "cust-1001" },
  "chain": {
    "algorithm": "SHA-256",
    "firstRecordId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "firstSequenceNumber": 1024,
    "firstPreviousHash": "a94a8fe5...",
    "lastRecordId": "9b2f...-...-...-...-............",
    "lastSequenceNumber": 1031,
    "lastRecordHash": "9f86d081...",
    "recordCount": 3
  },
  "records": [ /* AuditEventResponse objects, ascending sequenceNumber */ ]
}
```

Each record is a full `AuditEventResponse`, including `previousHash` and
`contentHash`. Nothing is recomputed or rewritten during export — persisted
records are authoritative and read-only.

## Partial-chain semantics

A filtered export is usually **not** a contiguous segment of the global chain.
If the log is `1(A) 2(B) 3(A) 4(B) 5(A)`, exporting actor `A` yields sequence
numbers `1, 3, 5`. Each record keeps its original `previousHash`, which may
point at a record the filter excluded — no links are fabricated. The chain
metadata anchors the segment:

- `firstPreviousHash` — the hash preceding the first exported record (possibly
  an unexported one);
- `lastRecordHash` — the `contentHash` of the last exported record.

Together they pin the export to the global chain and can be cross-checked
against the source service.

## Verifying a bundle

A bundle is self-contained: a recipient verifies it with the same
canonicalization and SHA-256 algorithm the write path uses
(`HashService.canonicalize` — fixed field order `eventType`, `actorId`,
`resourceType`, `resourceId`, `payload` with recursively sorted keys,
`timestamp` as a UTC instant, `previousHash`), needing no database access.
The checks to apply, in order:

1. **Structure** — `format` is `audit-bundle-v1`, `algorithm` is `SHA-256`,
   the filter type is a known scope, and `recordCount` equals the number of
   records.
2. **Each record** — required fields present, `contentHash` recomputes from
   the canonical event content, and the record matches the declared filter.
3. **Ordering and links** — sequence numbers strictly increase. Where two
   exported records have *consecutive* sequence numbers they are adjacent in
   the global chain and `current.previousHash` must equal the predecessor's
   `contentHash`; where they do not, `previousHash` is an external reference.
   The first record's `previousHash` must equal `chain.firstPreviousHash`.
4. **Anchors** — `firstRecordId`/`firstSequenceNumber` and
   `lastRecordId`/`lastSequenceNumber`/`lastRecordHash` must match the records
   actually present (and must all be absent for an empty bundle).

## Limitations

- The bundle is **hash-verified but not signed**. There is no digital
  signature or key management: a recipient can confirm internal consistency,
  but proof that the bundle came from this service requires comparing the chain
  anchors (`firstPreviousHash`, `lastRecordHash`) or record hashes with a
  trusted source, or a future signing mechanism.
- A whole-chain completeness guarantee is not possible for filtered exports:
  the verifier cannot know how many records a filter *should* have matched.
  `recordCount`, the anchors, and contiguous-link checks make omission
  detectable wherever chain continuity applies.
- Export loads the full filtered result set into memory. Database indexes on
  `actor_id` and `resource_id` keep the lookup on an index seek; very large
  exports may need streaming in a future iteration.
