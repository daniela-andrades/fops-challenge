# Testing

The suite is organised as a pyramid: many fast tests on the business rules, fewer and slower tests on the wiring, and a handful of browser tests on the critical user journeys.

| Layer | Where | Tool | Runs with | What it proves |
|---|---|---|---|---|
| Domain unit | `backend/src/test/.../domain` | JUnit 5 | `mvn test` | Entity invariants: status transitions, stock never negative, retry backoff |
| Service unit | `backend/src/test/.../application` (`*Test`) | JUnit 5 + Mockito | `mvn test` | Orchestration: FIFO allocation, email queueing, validation, duplicate checks; cancellation locks the item before reading the order and returns each allocation separately; listener and retry scheduler survive failures; exact retry times with a fixed `Clock` |
| Controller unit | `OrderControllerIdempotencyTest`, `IdempotencyKeyTest` | JUnit 5 + Mockito | `mvn test` | Idempotent creation without Spring: replay with 200, mismatch with 409, non-duplicate violations rethrown |
| Invariant simulation | `FulfillmentSimulationTest` | JUnit 5 + Mockito (in-memory repositories) | `mvn test` | 25 seeded runs × 400 random orders, deliveries and cancellations through the real services; after every step: stock = ledger and never negative, no over-allocation, FIFO (only the oldest open order can be partial), cancellations return everything, one email per completed order |
| Persistence | `RepositoryIT` | `@DataJpaTest` + H2 | `mvn verify` | Custom queries, ordering, unique and check constraints |
| HTTP contract | `*ControllerIT` | `@WebMvcTest` | `mvn verify` | Status codes, payloads, error format; services mocked |
| Business flows | `FulfillmentFlowIT`, `OrderCompletionNotificationIT` | `@SpringBootTest` + H2 | `mvn verify` | The challenge scenarios end to end, through real services and database |
| Concurrency | `ConcurrencyIT` | `@SpringBootTest` + threads | `mvn verify` | Row locks prevent overselling under parallel orders and deliveries |
| API flow | `ApiFlowIT` | `@SpringBootTest` + MockMvc | `mvn verify` | The full API as the frontend uses it |
| Production database | `PostgresIT` | Testcontainers (PostgreSQL 16) | `mvn verify` | Per-item row locks and the stock `CHECK` constraint on the real engine; needs Docker running (see *Testcontainers and the Docker API version* below) |
| Frontend unit | `frontend/.../src/**/*.spec.ts` | Vitest + Angular TestBed | `npm run test:ci` | Services, interceptor, components and pages against a mocked API |
| End to end | `frontend/.../e2e/*.e2e.ts` | Playwright (system Chrome) | `npm run e2e` | Critical journeys in a real browser against the real backend |

## Business scenarios covered

The five key cases, described under *How fulfillment works* in the [README](../README.md), map to named tests in `FulfillmentFlowIT` and `OrderCompletionNotificationIT`:

1. Order completed with stock available
2. Order partially fulfilled when stock is short
3. Incoming stock fills a pending order and keeps the leftover
4. Email sent exactly once when an order reaches 100%, retried if SMTP fails, never duplicated
5. Every OUT movement is traceable to its order and to the IN movement that fed it

## Running the tests

### Backend

The backend requires JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS).

```bash
cd backend
mvn test      # unit tests only, a few seconds
mvn verify    # unit + integration tests + coverage gate
```

- Coverage report: `backend/target/site/jacoco/index.html`
- The build fails below 80% line or 75% branch coverage.
- `PostgresIT` runs automatically when Docker is running. A clean `mvn verify` on a machine with Docker reports **0 skipped**; any skip means Docker was not reachable and is worth investigating.

### Frontend

```bash
cd frontend/frontend/fops-frontend
npm test                # watch mode
npm run test:ci         # single run
npm run test:coverage   # single run + coverage gate, report in coverage/
npm run e2e             # Playwright; starts backend and frontend if not already running
npm run e2e:ui          # Playwright interactive mode
```

- Coverage thresholds (in `angular.json`): 85% statements and lines, 75% branches, 70% functions.
- E2E uses the locally installed Google Chrome (`channel: 'chrome'`), so no browser download is needed.
- The E2E backend must have JDK 21 available (see above). Each test creates its own uniquely named data, so the suite can run against a backend that already has data.

## Testcontainers and the Docker API version

`PostgresIT` is annotated `@Testcontainers(disabledWithoutDocker = true)`. That condition calls `DockerClientFactory.isDockerAvailable()`, which returns `false` on **any** exception and does not log why. For a long time the suite reported `PostgresIT: 3 skipped` with Docker running, and the cause was hidden:

- Testcontainers 1.19.8 talks to Docker with API version **1.32** unless one is configured, and does not negotiate.
- Docker Engine 29.4.1 accepts API **1.40 and above**: `GET /v1.32/info` returns HTTP 400, while `/v1.40/info` returns 200.
- The resulting `BadRequestException` became "Docker is not available", and the class was skipped instead of failing.

The fix lives in `backend/pom.xml`: the Failsafe configuration sets the system property `api.version=1.41` for integration tests. Testcontainers only falls back to 1.32 when no version is configured. 1.41 (Docker Engine 20.10, 2020) is used rather than the newest version so the suite keeps working on older engines too. No user-level file is needed: `~/.testcontainers.properties` and `~/.docker-java.properties` play no part. Upgrading Testcontainers to a version that negotiates the API version would make the pin unnecessary.

To check the API levels an engine accepts:

```bash
docker version --format 'server {{.Server.Version}} · API {{.Server.APIVersion}} · min API {{.Server.MinAPIVersion}}'
```

**Lesson:** a skipped test is an invisible failure. Until this was fixed, the per-item pessimistic lock and the `CHECK` constraint had never actually been verified on PostgreSQL.

## Does the simulation catch real bugs?

It was checked by injecting bugs into production code and restoring it afterwards:

| Injected bug | Repetitions that failed |
|---|---|
| Allocation serves the newest order first (breaks FIFO) | 25 of 25 |
| Cancellation marks the order CANCELLED after re-allocating (it receives its own returned stock) | 23 of 25 |

Failures name the seed and step that reproduce them, for example `seed 1, step 102 (deliver 10 of item 3)`.

## Conventions

- **Naming decides the runner.** `*Test` classes are plain unit tests (Surefire, no Spring context). `*IT` classes load Spring (Failsafe).
- **Integration tests extend `IntegrationTest`.** It provides one shared, cached Spring context with a mocked `JavaMailSender`, `TestFixtures` for creating data through the real services, and a `DatabaseCleaner` that empties the tables after each test.
- **Unit tests build data with `TestData`**, which creates in-memory entities with fixed ids. Domain objects are never mocked; Mockito is used at service boundaries (and, in the simulation, to back repositories with in-memory maps).
- **Time comes from an injected `Clock`** in `NotificationService`, so retry schedules are asserted exactly (30 s, 60 s, 120 s…).
- **Frontend tests build data with `src/testing/fixtures.ts`** (`anOrder({ status: 'COMPLETED' })` and similar) and use `createApiMock()`, where every endpoint succeeds by default and tests override only what they need.
- **Asynchronous email delivery** is awaited with Awaitility. The retry scheduler is disabled in the `test` profile, and tests trigger it explicitly.
