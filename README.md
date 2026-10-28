# PlotPack: e-token parcel pickup for multi-floor stores

**Problem (Jayalakshmi Silks):** customers shop on floors 1–3, and every item is sent down to the ground floor.
Staff used to search through piles of packages by hand for every customer (3–4 minutes each), which led to bad reviews.

**Solution:** when an item is billed upstairs, the system puts it in a **bin** in a ground-floor grid and texts the
customer an **e-token**. At the ground floor the customer says the token, the system shows exactly which bins to open,
the packages are handed over, the bins are freed, and a "thank you" SMS goes out.

## Architecture: 4 services

```
 React UI ──▶ API Gateway (:8090) ──▶ User Service (:8181)            customers
                                 ├──▶ Package Service (:8183)          bins, allocation, e-token, delivery
                                 │        │  MySQL (truth) + Redis (locks, idempotency)
                                 │        └── outbox ──▶ Kafka "plotpack.package-events"
                                 │                              │
                                 └──▶ Notification Service (:8185) ◀┘  SMS + live push to UI (SSE)
```

| Flow | Path |
|---|---|
| **Allocate** | Floor executive → `POST /api/users` (find/register) → `POST /api/packages` → bin + e-token → **Kafka `PACKAGE_ALLOCATED`** → SMS "your e-token is PP-XXXXXX" |
| **Pickup** | Counter → `GET /api/packages/tokens/{token}` (which bins) → `POST …/deliver` → bins released → **Kafka `ORDER_DELIVERED`** → SMS "delivered, thank you for shopping" |

The REST calls are synchronous because the floor executive needs the bin number immediately. The SMS goes through Kafka
asynchronously, so a slow SMS provider never slows down billing.

## Run it

```powershell
.\run-local.ps1          # Docker: MySQL/Redis/Kafka · local JVMs: 4 services · React on http://localhost:5173
.\run-local.ps1 -Stop
```
Or run everything in Docker: `docker compose up -d --build`, then open http://localhost:3000.

Ports were picked to avoid conflicts on this machine: Jenkins uses 8080, clinevo uses 8081, and local MySQL uses 3306,
so the gateway is on **8090** and Docker MySQL is on **3307**.

## The UI
- **Floor executive:** pick the floor, find or register the customer, bill items (estimated bill), then *Send down & allocate bins*.
  Click a green bin tag to resend that request with the same Idempotency-Key and see that no duplicate is created.
- **Pickup counter:** type the e-token (or tap it on the customer's SMS) to highlight the bins, then *Hand over & release bins*.
- **Simulation:** live traffic (customers per minute) and one-click edge-case drills, each showing PASS/FAIL.
- **Request path strip:** lights up each service a request passes through. Dashed lines are the asynchronous Kafka hops.

## APIs (through the gateway)

| | |
|---|---|
| `POST /api/users` `{name, phone}` | find-or-register (idempotent on phone) |
| `POST /api/packages` + `Idempotency-Key` `{customerId, floor, description, amount}` | allocate bin, issue/reuse e-token → `201`, or `200 Idempotent-Replayed: true`, or `503 NO_FREE_BIN` |
| `GET /api/packages/tokens/{token}` | bins + items + estimated bill for the token |
| `POST /api/packages/tokens/{token}/deliver` | hand over, release bins → `409 ALREADY_DELIVERED` on repeat |
| `GET /api/packages/bins` · `/stats` · `/tokens` | live grid · numbers · waiting tokens |
| `GET /api/notifications` · `/api/notifications/stream` | SMS log · live stream (SSE) |

## Edge cases, and where they are handled

| Edge case | How it's handled | Code |
|---|---|---|
| Two requests grab the same free bin | **Redis `SET NX` lock** per bin → **re-check** in a `REQUIRES_NEW` transaction → **`UNIQUE(active_slot)`** in MySQL as the final guard | [AllocationService](package-service/src/main/java/com/plotpack/parcel/service/AllocationService.java), [BinAllocator](package-service/src/main/java/com/plotpack/parcel/service/BinAllocator.java) |
| Two floors add to the same customer's bin at once | **`PESSIMISTIC_WRITE`** (`SELECT … FOR UPDATE`) on the bin row before `count+1`; plus a per-customer Redis lock so one customer gets one token | [PackageWriter](package-service/src/main/java/com/plotpack/parcel/service/PackageWriter.java), [PackageOrchestrator](package-service/src/main/java/com/plotpack/parcel/service/PackageOrchestrator.java) |
| Bin is full | Customer's next item goes to a new bin (up to 3 bins per customer) | AllocationService |
| No free bin at all | Clean `503 NO_FREE_BIN`; nothing is half-created | AllocationService |
| Tablet retries the same request | **Idempotency-Key**: Redis fast path + `UNIQUE(idempotency_key)` in MySQL | [IdempotencyService](package-service/src/main/java/com/plotpack/parcel/service/IdempotencyService.java) |
| Two counter staff deliver the same token | Row locks; the second gets `409 ALREADY_DELIVERED`; bins are released exactly once | [DeliveryService](package-service/src/main/java/com/plotpack/parcel/service/DeliveryService.java) |
| Crash between DB commit and Kafka publish | **Transactional outbox**: the event row is committed with the data, and a poller publishes it later | [common/outbox](common/src/main/java/com/plotpack/common/outbox) |
| Kafka delivers an event twice | `UNIQUE(event_id)` on notifications, so the SMS is never sent twice | [PackageEventsConsumer](notification-service/src/main/java/com/plotpack/notification/PackageEventsConsumer.java) |
| Request dies after claiming a bin | Empty bins older than 30 s are released automatically | [EmptyBinReaper](package-service/src/main/java/com/plotpack/parcel/service/EmptyBinReaper.java) |
| Redis is down | Locks report UNAVAILABLE and the flow continues, because the MySQL constraints still guarantee correctness | [RedisLockService](package-service/src/main/java/com/plotpack/parcel/service/RedisLockService.java) |

**Lesson learned (good interview story):** under load test, deliveries started timing out. The outbox poller's
`SELECT … FOR UPDATE SKIP LOCKED` ran at MySQL's default REPEATABLE READ, which also takes *gap locks*, and those
blocked new outbox inserts. Switching package-service to **READ COMMITTED** removed the gap locks and fixed it.

## 60-second pitch (STAR)
- **S:** At a multi-floor silk store, every package came down to one ground-floor counter, and staff searched by hand: 3–4 minutes per customer.
- **T:** Build a backend that assigns every package a known location and lets the counter find it instantly, safely under concurrency.
- **A:** Spring Boot microservices behind a gateway. Package-service allocates the nearest free bin, protected by a Redis lock,
  a pessimistic row lock and a unique constraint. It issues an e-token and publishes events through a transactional outbox to Kafka.
  Notification-service consumes them idempotently and texts the customer. APIs are idempotent, so retries never duplicate.
- **R:** Pickup became "say token → open bins": retrieval went from ~3–4 minutes to ~20 seconds per package.
