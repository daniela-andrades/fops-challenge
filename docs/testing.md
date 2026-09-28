# Testing

The suite is organised as a pyramid: many fast tests on the business rules, fewer and slower tests on the wiring, and a handful of browser tests on the critical user journeys.

| Layer | Where | Tool | Runs with | What it proves |
|---|---|---|---|---|
| Domain unit | `backend/src/test/.../domain` | JUnit 5 | `mvn test` | Entity invariants: status transitions, stock never negative, retry backoff |
| Service unit | `backend/src/test/.../application` (`*Test`) | JUnit 5 + Mockito | `mvn test` | Orchestration: FIFO allocation, email queueing, validation, duplicate checks |
| Persistence | `RepositoryIT` | `@DataJpaTest` + H2 | `mvn verify` | Custom queries, ordering, unique and check constraints |
| HTTP contract | `*ControllerIT` | `@WebMvcTest` | `mvn verify` | Status codes, payloads, error format; services mocked |
| Business flows | `FulfillmentFlowIT`, `OrderCompletionNotificationIT` | `@SpringBootTest` + H2 | `mvn verify` | The challenge scenarios end to end, through real services and database |
| Concurrency | `ConcurrencyIT` | `@SpringBootTest` + threads | `mvn verify` | Row locks prevent overselling under parallel orders and deliveries |
| API flow | `ApiFlowIT` | `@SpringBootTest` + MockMvc | `mvn verify` | The full API as the frontend uses it |
| Production database | `PostgresIT` | Testcontainers (PostgreSQL 16) | `mvn verify` | Row locks and constraints on the real engine; **skipped when Docker is not running** |
| Frontend unit | `frontend/.../src/**/*.spec.ts` | Vitest + Angular TestBed | `npm run test:ci` | Services, interceptor, components and pages against a mocked API |
| End to end | `frontend/.../e2e/*.e2e.ts` | Playwright (system Chrome) | `npm run e2e` | Critical journeys in a real browser against the real backend |

## Business scenarios covered

The five key cases from the spec (section 17) map to named tests in `FulfillmentFlowIT` and `OrderCompletionNotificationIT`:

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
- `PostgresIT` runs automatically when Docker is available.

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

## Conventions

- **Naming decides the runner.** `*Test` classes are plain unit tests (Surefire, no Spring context). `*IT` classes load Spring (Failsafe).
- **Integration tests extend `IntegrationTest`.** It provides one shared, cached Spring context with a mocked `JavaMailSender`, `TestFixtures` for creating data through the real services, and a `DatabaseCleaner` that empties the tables after each test.
- **Unit tests build data with `TestData`**, which creates in-memory entities with fixed ids.
- **Frontend tests build data with `src/testing/fixtures.ts`** (`anOrder({ status: 'COMPLETED' })` and similar) and use `createApiMock()`, where every endpoint succeeds by default and tests override only what they need.
- **Asynchronous email delivery** is awaited with Awaitility. The retry scheduler is disabled in the `test` profile, and tests trigger it explicitly.
