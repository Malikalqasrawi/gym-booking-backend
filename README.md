# Gym Booking API

A REST API for booking personal-training sessions across a gym chain. Members pick a trainer at one of five branches and request a time slot. The trainer accepts, the member pays through Stripe, and both get an email at each step.

[![CI](https://github.com/Malikalqasrawi/gym-booking-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Malikalqasrawi/gym-booking-backend/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)
![Stripe](https://img.shields.io/badge/Stripe-test%20mode-635BFF?logo=stripe&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose-2496ED?logo=docker&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Mobile client: [gym-booking-app](https://github.com/Malikalqasrawi/gym-booking-app) (Flutter)

## Why

Booking a trainer over the phone or by chat breaks in predictable ways. Two people get the same hour, a trainer never replies, a session is booked but never paid, or a late cancellation turns into an argument. This service makes those rules explicit and enforces them in one place:

- **No double booking.** Requests for the same trainer are serialized with a row lock, so two members can't get the same slot.
- **Nothing stays pending forever.** Trainers have 24 h to answer and accepted sessions must be paid within 12 h. After that, the slot is released automatically.
- **Payments are verified server-side.** Card details go straight from the app to Stripe, and the API confirms every payment with Stripe. Refunds are automatic when a paid session is cancelled at least 24 h in advance.
- **Data is scoped to its owner.** Members see their own bookings and trainers see requests addressed to them. Any other booking returns `404`.

## Features

| Area | Details |
|---|---|
| Accounts | Sign-up with email verification code, JWT auth, member / trainer / admin roles, lockout after 5 failed logins |
| Branches & trainers | 5 branches with coordinates and opening hours, 22 trainer profiles, filters (category, gender, max rate), weekly schedules |
| Availability | Free start times per trainer, date and duration (30/45/60/90 min), based on working hours, branch hours and existing bookings |
| Bookings | Request, accept or decline, 24 h reply deadline, max 3 pending requests per member, cancellation rules |
| Payments | Stripe PaymentIntents (test mode), 12 h payment window, email receipts, automatic refunds, signed webhooks |
| Hardening | Per-IP rate limiting, owner-scoped queries, optimistic and pessimistic locking, secrets kept out of the repo |

## Architecture

```mermaid
flowchart LR
    App["Flutter app"] -->|"HTTPS + JSON<br/>Bearer JWT"| Filters

    subgraph Backend["Spring Boot"]
        direction LR
        Filters["RateLimitFilter<br/>JwtAuthenticationFilter"] --> Controllers
        Controllers --> Services
        Services --> Repositories["Repositories<br/>(Spring Data JPA)"]
        Services --> Gateway["PaymentGateway"]
        Services --> Notify["NotificationSender<br/>(console / SMTP)"]
        Job["BookingExpiryJob<br/>(every minute)"] --> Services
    end

    Repositories --> DB[("MySQL 8.4")]
    Gateway -->|"REST"| Stripe["Stripe API"]
    Stripe -.->|"signed webhook"| Controllers
    App -.->|"card details"| Stripe
```

Controllers handle HTTP and validation only, services own the business rules, and repositories handle persistence. External dependencies sit behind interfaces (`PaymentGateway`, `NotificationSender`, `RateLimiter`, `TokenService`), which keeps the rules independent of Stripe, SMTP or the JWT library and lets the tests replace them with fakes.

### Payment flow

```mermaid
sequenceDiagram
    participant App as Flutter app
    participant API as Backend
    participant DB as MySQL
    participant Stripe
    participant Mail as Email

    App->>API: POST /api/bookings/{id}/payment
    API->>DB: lock booking, check it is ACCEPTED and within the deadline
    API->>Stripe: create PaymentIntent (amount in fils, Idempotency-Key)
    Stripe-->>API: id + client_secret
    API-->>App: client_secret + publishable key
    App->>Stripe: card entered in Stripe PaymentSheet
    Stripe-->>App: succeeded
    App->>API: POST /api/bookings/{id}/payment/confirm
    API->>Stripe: retrieve PaymentIntent
    API->>DB: booking PAID, payment SUCCEEDED
    API-->>App: booking + receipt
    API-)Mail: confirmation to member and trainer (after commit)
```

## Design decisions

- **State lives in the entities.** `Booking` and `Payment` have no status setters. Transitions go through methods like `accept()`, `markPaid()`, `cancelByMember()` and `markRefunded()`, which reject invalid transitions with `409`.
- **Concurrency.** A booking request takes a pessimistic lock on the trainer row (`SELECT ... FOR UPDATE`) while it checks for overlaps. Payment, cancellation and webhook handling lock the booking row. Bookings also carry a `@Version` column, so conflicting updates fail with `BOOKING_CHANGED` instead of silently overwriting each other.
- **Stripe is the source of truth for payments.** The confirm endpoint and the webhook both re-fetch the PaymentIntent and check the amount. Neither trusts the client or the event body. Stripe calls carry an `Idempotency-Key`, and `payments.booking_id` is unique.
- **Late payments are refunded.** If a payment succeeds after the booking has expired or been cancelled, it is refunded right away and the client gets `PAID_TOO_LATE`.
- **Side effects run after commit.** Emails are triggered by domain events (`BookingPaidEvent`, `BookingRefundedEvent`) handled with `@TransactionalEventListener(AFTER_COMMIT)`. A rolled-back transaction never sends an email, and a mail failure never fails a request.
- **Plain REST instead of the Stripe SDK.** The API only needs three Stripe endpoints, so it calls them with Spring's `RestClient`. The integration tests then run against a local fake Stripe server instead of mocks.
- **Time is injected.** Services use a `Clock` in the gym's timezone (`Asia/Amman`), so deadlines are deterministic in tests (`MutableClock`).
- **Expiry is enforced on read.** `Booking.statusAt(now)` already reports overdue bookings as expired, and the scheduled job persists that every minute, one transaction per booking.
- **Money.** Amounts are `BigDecimal` in JOD, which has three decimal places (1 JOD = 1000 fils). Amounts that Stripe cannot charge are rejected, never rounded.
- **Secrets stay out of the repo.** Configuration comes from a git-ignored `local.properties` or `.env`. A pre-commit hook blocks key-like strings, and the app refuses to start with live Stripe keys.

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail) |
| Database | MySQL 8.4, Hibernate 6 |
| Auth | JWT (JJWT), BCrypt |
| Payments | Stripe REST API via `RestClient` |
| Tests | JUnit 5, H2, fake Stripe HTTP server |
| Delivery | Docker Compose, GitHub Actions |

## Getting started

### Docker

```bash
git clone https://github.com/Malikalqasrawi/gym-booking-backend.git
cd gym-booking-backend
cp .env.example .env    # fill in the values
docker compose up --build
```

The API runs on <http://localhost:8080>. `GET /api/health` returns `{"status":"UP", ...}`.

### Local (Maven)

Requires JDK 21+ and MySQL 8.4.

```bash
cp local.properties.example local.properties
bash scripts/create-db-user.sh    # creates the MySQL user "gymapp" and stores its password in local.properties
# add a JWT secret to local.properties: openssl rand -base64 64 | tr -d '\n'
mvn spring-boot:run
```

The `gymdb` schema and its tables are created on first start. The `gymapp` user can only access `gymdb` and cannot drop tables.

### Demo data

On startup the app seeds any missing branches, trainers and the admin account. These credentials are for local development only.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@gym.com` | `Admin1234` |
| Trainer | `<firstname>.trainer@gym.com`, e.g. `sara.trainer@gym.com` | `Trainer1234` |

Trainers by branch: Abdoun (sara, yousef, rania, khaled, maya), Khalda (omar, dana, ahmad, hala), Sweifieh (lina, faris, noor, hamza, jana), Shmeisani (laith, ruba, qais, aya), Jubeiha (mohammad, leen, bashar, tala). Members sign up through the app.

### Configuration

Settings are in `src/main/resources/application.properties`. Secrets go in `local.properties` (or `.env` for Docker):

| Property | Env var (Docker) | Notes |
|---|---|---|
| `spring.datasource.password` | `DB_PASSWORD` | Set by `create-db-user.sh` |
| `app.jwt.secret` | `JWT_SECRET` | Required. Base64, 64+ bytes |
| `app.payments.stripe.secret-key` | `STRIPE_SECRET_KEY` | `sk_test_...` only |
| `app.payments.stripe.publishable-key` | `STRIPE_PUBLISHABLE_KEY` | `pk_test_...`, passed to the app |
| `app.payments.stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | Optional |
| `app.notifications.mode` | | `console` (default) or `email` (uses `spring.mail.*`) |

Without Stripe keys the service still starts, and the payment endpoints return `503 PAYMENTS_NOT_CONFIGURED`.

### Stripe test mode

1. In the Stripe Dashboard (test mode), copy the keys from **Developers > API keys** into `local.properties`.
2. Pay with a test card, using any future expiry date and any CVC:

   | Card | Result |
   |---|---|
   | `4242 4242 4242 4242` | Succeeds (Visa) |
   | `5555 5555 5555 4444` | Succeeds (Mastercard) |
   | `4000 0000 0000 0002` | Declined |
   | `4000 0025 0000 3155` | Requires 3-D Secure |

3. Optional: forward webhooks with the Stripe CLI, then set the printed `whsec_...` as `app.payments.stripe.webhook-secret`:

   ```bash
   stripe listen --events payment_intent.succeeded --forward-to localhost:8080/api/payments/stripe/webhook
   ```

## API

All endpoints except `/api/health`, `/api/auth/**` and the Stripe webhook require `Authorization: Bearer <token>`.

### Auth

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/signup` | `fullName, email, phone, password` | `201`, verification code sent by email |
| POST | `/api/auth/verify` | `email, code` | `200 {token, user}` |
| POST | `/api/auth/resend-code` | `email` | `200` |
| POST | `/api/auth/login` | `email, password` | `200 {token, user}` |
| GET | `/api/users/me` | | `200` current user |

### Branches and trainers

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/branches` | any | Optional `?city=` |
| GET | `/api/branches/{id}` | any | |
| POST / PUT / DELETE | `/api/branches[/{id}]` | admin | |
| GET | `/api/branches/{id}/trainers` | any | Optional `?category=&gender=&maxRate=` |
| GET | `/api/trainers/{id}` | any | Profile and weekly schedule |
| GET | `/api/trainers/{id}/availability?date=&duration=` | any | Free start times. `duration` is 30/45/60/90, `date` is within the next 14 days |

### Bookings

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/bookings` | member | `{trainerId, date, startTime, durationMinutes, note?}` |
| GET | `/api/bookings/mine` | member | |
| GET | `/api/bookings/{id}` | member | |
| POST | `/api/bookings/{id}/cancel` | member | Refunds automatically if paid |
| GET | `/api/trainer/requests` | trainer | Pending requests, most urgent first |
| GET | `/api/trainer/schedule` | trainer | Upcoming accepted and paid sessions |
| POST | `/api/trainer/requests/{id}/accept` | trainer | Optional `{message}` |
| POST | `/api/trainer/requests/{id}/reject` | trainer | Optional `{message}` |

Booking lifecycle:

```
REQUESTED -> ACCEPTED -> PAID
    |            |         |
    |            |         +-> CANCELLED (until 24 h before, full refund)
    |            +-> EXPIRED (not paid within 12 h) / CANCELLED
    +-> REJECTED / EXPIRED (no answer within 24 h) / CANCELLED
```

### Payments

| Method | Path | Caller | Notes |
|---|---|---|---|
| POST | `/api/bookings/{id}/payment` | member | Creates or reuses the PaymentIntent and returns `clientSecret` and `publishableKey` |
| POST | `/api/bookings/{id}/payment/confirm` | member | Verifies the PaymentIntent with Stripe and marks the booking `PAID` |
| POST | `/api/payments/stripe/webhook` | Stripe | Verified by `Stripe-Signature` |

### Errors and limits

Errors share one format:

```json
{ "status": 409, "code": "SLOT_NOT_AVAILABLE", "message": "...", "fieldErrors": {}, "timestamp": "..." }
```

| Limit | Value | Response |
|---|---|---|
| `/api/auth/**` per IP | 10 / min | `429 RATE_LIMITED` |
| Other endpoints per IP | 100 / min | `429 RATE_LIMITED` |
| Verification code resend | 1 / 60 s | `429 RESEND_TOO_SOON` |
| Failed logins | 5, then 15 min lock | `429 ACCOUNT_LOCKED` |
| Wrong verification codes | 5 per code | `400 TOO_MANY_ATTEMPTS` |

`429` responses include `Retry-After`. All limits are configurable in `application.properties`.

## Project structure

```
src/main/java/com/mycompany/gymbooking
├── config/         security, clock, seed data, schema upgrades
├── controller/     REST endpoints
├── dto/            request and response records
├── exception/      API exceptions and the global handler
├── model/          JPA entities and enums
├── notification/   email senders and payment email listener
├── payment/        PaymentGateway, Stripe client, webhook verification
├── repository/     Spring Data repositories
├── security/       JWT, rate limiting, security error handling
└── service/        business logic and the expiry job
```

## Tests

```bash
mvn verify
```

49 tests. They need no MySQL, network or Stripe account.

| Test | Covers |
|---|---|
| `BookingTest` | Booking state machine, deadlines, expiry, 24 h cancellation rule |
| `PaymentTest` | Payment status transitions |
| `CurrencyUnitsTest` | JOD minor units and amounts Stripe cannot charge |
| `StripeWebhookVerifierTest` | Signature and timestamp checks |
| `StripePaymentGatewayTest` | Requests sent to Stripe, idempotency, error handling |
| `InMemoryRateLimiterTest` | Token bucket refill and `Retry-After` |
| `GymBookingApiTest` | End-to-end over HTTP: auth, roles, access control, double booking, payment and refund flow, declines, webhooks, late payments, Stripe outages |

CI runs the test suite and a Docker Compose smoke test on every push and pull request.

## Roadmap

- [x] Accounts, email verification, JWT, roles
- [x] Branches, trainers, availability
- [x] Booking requests, accept / decline, expiry
- [x] Stripe payments, refunds, confirmation emails
- [ ] Admin tools for branches, trainers and schedules
- [ ] Refresh tokens and 2FA for admins
- [ ] Google sign-in

## License

[MIT](LICENSE)
