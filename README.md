# Money Transfer API

A Spring Boot REST API for transferring money between in-memory accounts. It uses embedded Tomcat,
so no pre-installed application server or container is required.

## Run

Requirements: Java 21+ and Maven 3.9+.

```bash
mvn clean package
java -jar target/money-transfer-api.jar
```

The server listens on port `8080`. Override it with `PORT=9090`.

Transfer worker settings can also be changed without rebuilding:

```bash
TRANSFER_WORKERS=8 TRANSFER_QUEUE_CAPACITY=200 java -jar target/money-transfer-api.jar
```

## Try it

Create two accounts:

```bash
curl -s -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"owner":"Alice","initialBalance":100.00}'

curl -s -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"owner":"Bob","initialBalance":20.00}'
```

Use the returned IDs to transfer money:

```bash
curl -i -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: payment-2026-0001' \
  -d '{
    "sourceAccountId":"SOURCE_UUID",
    "destinationAccountId":"DESTINATION_UUID",
    "amount":30.50
  }'
```

Retrying the same body with the same `Idempotency-Key` returns the original transfer (HTTP `200`
and `Idempotent-Replayed: true`) without moving money again. Reusing the key with a different body
returns `409 Conflict`.

Other endpoints:

| Method | Path | Result |
|---|---|---|
| `POST` | `/accounts` | Creates an account |
| `GET` | `/accounts/{id}` | Reads an account and balance |
| `POST` | `/transfers` | Executes a transfer |
| `GET` | `/transfers/{id}` | Reads a transfer and its state history |
| `GET` | `/ledger` | Reads the full immutable ledger for reconciliation |
| `GET` | `/transfers/{id}/ledger` | Reads the debit and credit postings for a transfer |
| `GET` | `/accounts/{id}/ledger` | Reads an account's immutable ledger entries |
| `GET` | `/health` | Liveness check |

## Design

- **Clean boundaries:** Spring MVC controllers and DTOs live in `web`; orchestration and use cases
  live in `service`; business state and invariants live in `domain`; storage contracts and their
  in-memory implementations live in `repository`.
- **Correct money representation:** amounts are converted from decimal JSON values to integer cents.
- **Atomicity and concurrency:** both accounts are locked for the debit/credit. Locks are always
  acquired in UUID order, preventing deadlocks. Balance checks and mutations happen in one critical
  section, so concurrent requests cannot overspend.
- **Double-entry ledger:** every completed transfer atomically appends an immutable `DEBIT` entry
  for the source and a matching `CREDIT` entry for the destination. Both carry the transfer ID,
  amount, timestamp, and resulting account balance. Failed transfers create no entries, and a
  transfer can be posted only once.
- **Idempotency:** an atomic in-memory key registry binds each key to a request fingerprint and a
  shared result future. Concurrent duplicates wait for the same operation; only one reaches the
  transfer executor.
- **State machine:** every accepted transfer follows
  `PENDING -> PROCESSING -> COMPLETED | FAILED`. Invalid or terminal transitions are rejected, and
  the API exposes the timestamped transition history.
- **Backpressure:** transfer execution uses a fixed worker pool and a bounded queue of 100. A full
  queue returns `429 Too Many Requests`; the idempotency reservation is removed so the caller can
  safely retry with the same key.
- **Failure semantics:** insufficient funds produces a persisted `FAILED` transfer and HTTP `422`;
  neither account is modified.

This is intentionally a single-process assessment implementation. Account data, transfer records,
and idempotency keys disappear on restart. A production version would put all three in one
transactional database, retain idempotency records for a defined TTL, authenticate callers, add
metrics/tracing, and use durable queue and ledger storage.

## Tests

```bash
mvn test
```

The tests exercise the API over real HTTP and separately verify balanced ledger postings,
idempotent retries without duplicate entries, concurrent idempotency, prevention of overspending,
legal state transitions, failed-transfer behavior, and deterministic rejection when the bounded
work queue is full.
