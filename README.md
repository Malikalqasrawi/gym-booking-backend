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
- **Nobody misses a session or a change to their account.** Members and trainers get a reminder the day before a paid session, and every password or two-factor change is emailed to the account owner.

## Features

| Area | Details |
|---|---|
| Accounts | Sign-up with email verification code or with Google (members), password reset by emailed code, change password, member / trainer / admin roles, lockout after 5 failed logins |
| Phone numbers | Checked against each country's rules (Google's libphonenumber) and stored in international format; members confirm theirs with a code by SMS (Twilio Verify) before their first booking |
| Sessions | 15-minute JWT access tokens renewed with rotating refresh tokens (30 days), logout per device or on all devices |
| Two-factor login | Codes from an authenticator app (TOTP, RFC 6238), required for admins and optional for members and trainers, 8 one-time recovery codes, moving to a new phone |
| Branches & trainers | 5 branches with coordinates and opening hours, 22 trainer profiles, filters (category, gender, max rate), weekly schedules |
| Availability | Free start times per trainer, date and duration (30/45/60/90 min), based on working hours, branch hours and existing bookings |
| Bookings | Request, accept or decline, 24 h reply deadline, max 3 pending requests per member, cancellation rules |
| Payments | Stripe PaymentIntents (test mode), 12 h payment window, email receipts, automatic refunds, signed webhooks |
| Emails | Branded HTML with a plain-text version, sent through Gmail from an outbox with retries, security alerts (password, two-factor, Google sign-in), reminders the day before a session |
| Admin | Add trainers by email invite, edit profiles and weekly schedules, deactivate (cancels and refunds their upcoming bookings) or reactivate |
| Gym operations | See every booking with contact details, cancel any session before it starts (full refund), close a branch or give a trainer time off for whole days or set hours |
| Reviews | Members rate a paid session (1 to 5 stars and an optional comment) for 30 days after it takes place, once; trainers reply; admins hide reviews with a reason the member is emailed; average ratings on every trainer |
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
        Services --> Notify["NotificationSender<br/>(email outbox)"]
        Notify --> Dispatcher["EmailDispatcher<br/>(background, retries)"]
        Job["Scheduled jobs<br/>(expiry, reminders)"] --> Services
    end

    Repositories --> DB[("MySQL 8.4")]
    Dispatcher -->|"SMTP"| Gmail["Gmail"]
    Gateway -->|"REST"| Stripe["Stripe API"]
    Stripe -.->|"signed webhook"| Controllers
    App -.->|"card details"| Stripe
```

Controllers handle HTTP and validation only, services own the business rules, and repositories handle persistence. External dependencies sit behind interfaces (`PaymentGateway`, `NotificationSender`, `MailTransport`, `RateLimiter`, `TokenService`), which keeps the rules independent of Stripe, SMTP or the JWT library and lets the tests replace them with fakes.

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
    API->>Stripe: create PaymentIntent (USD equivalent, Idempotency-Key)
    Stripe-->>API: id + client_secret
    API-->>App: client_secret + publishable key
    App->>Stripe: card entered in Stripe PaymentSheet
    Stripe-->>App: succeeded
    App->>API: POST /api/bookings/{id}/payment/confirm
    API->>Stripe: retrieve PaymentIntent
    API->>DB: booking PAID, payment SUCCEEDED
    API-->>App: booking + receipt
    API-)Mail: confirmation to member and trainer (sent in the background after commit)
```

## Design decisions

- **State lives in the entities.** `Booking` and `Payment` have no status setters. Transitions go through methods like `accept()`, `markPaid()`, `cancelByMember()` and `markRefunded()`, which reject invalid transitions with `409`.
- **Concurrency.** A booking request takes a pessimistic lock on the trainer row (`SELECT ... FOR UPDATE`) while it checks for overlaps. Payment, cancellation and webhook handling lock the booking row. Bookings also carry a `@Version` column, so conflicting updates fail with `BOOKING_CHANGED` instead of silently overwriting each other.
- **Stripe is the source of truth for payments.** The confirm endpoint and the webhook both re-fetch the PaymentIntent and check the amount. Neither trusts the client or the event body. Stripe calls carry an `Idempotency-Key`, and `payments.booking_id` is unique.
- **Late payments are refunded.** If a payment succeeds after the booking has expired or been cancelled, it is refunded right away and the client gets `PAID_TOO_LATE`.
- **Emails go through an outbox.** `NotificationSender` doesn't send anything itself: it saves the email in `outgoing_emails` in the same transaction as the change it reports. A rolled-back change never sends an email, and a committed one always does, even after a restart. Once the transaction commits, `EmailDispatcher` sends the email on a background thread, so a slow or unreachable mail server never slows down or fails a request. A failed attempt is retried after 1, 5, 15 and 60 minutes, then the email is marked `FAILED`. A version column makes sure two senders can't send the same email. The text is deleted once it's sent, and the rows after 30 days. Payment emails come from domain events (`BookingPaidEvent`, `BookingRefundedEvent`) handled just before the payment commits.
- **One text, two formats.** Each email is written once, as plain text. `EmailLayout` builds the branded HTML version from it (details as a table, steps as a list, codes in large print), so both versions always match. Anything a user typed, like a booking note, is HTML-escaped.
- **Phone numbers by country, confirmed by SMS.** `PhoneNumbers` checks numbers with Google's libphonenumber, which knows every country's numbering plan, and stores them in international format (E.164, e.g. `+962791234567`). Numbers typed without a country code are read as Jordanian, and numbers saved before this are converted at startup (`PhoneNumberUpgrade`). Members confirm their number with a 6-digit code by SMS before their first booking, and again after changing it, so the trainer and the gym can reach them. `PhoneCodes` hides the provider: Twilio Verify creates, texts and checks the codes, and without Twilio keys the code is written to the log. The limits are the app's own, the same for both: a new code once a minute per number, 5 a day per account (each SMS costs money), 5 tries per code, 10 minutes to enter it.
- **Security alerts and reminders.** Changing or resetting the password, turning two-factor authentication on or off, moving it to a new phone and adding Google sign-in to an existing account each send an email that says what to do if it wasn't you. `SessionReminders` emails the member and the trainer 24 hours before a paid session. It locks each booking and records the reminder in `reminder_checked_at`, so nobody gets two. Sessions paid within those 24 hours get none, because the confirmation was just sent.
- **Plain REST instead of the Stripe SDK.** The API only needs three Stripe endpoints, so it calls them with Spring's `RestClient`. The integration tests then run against a local fake Stripe server instead of mocks.
- **Time is injected.** Services use a `Clock` in the gym's timezone (`Asia/Amman`), so deadlines are deterministic in tests (`MutableClock`).
- **Admins never know trainer passwords.** A trainer added by an admin gets an account with a random password and an emailed one-time invite code. Accepting the invite sets their own password. Deactivated trainers are hidden from members, their tokens stop working, and their upcoming bookings are cancelled by the gym with full refunds.
- **The gym can always cancel, and always refunds in full.** Members can't cancel a paid session in its last 24 hours, but the gym can until it starts. Cancelling a single booking, deactivating a trainer and blocking time all go through the same path: each booking is locked and re-checked, paid ones are refunded in full, and the member and trainer are emailed the reason.
- **Blocked times are applied in two places.** Availability never offers a blocked slot, and creating a block cancels the bookings already inside it. The admin first gets a preview of how many bookings that would be. Deleting a block frees the time again but doesn't restore cancelled bookings.
- **One review per session, from someone who was there.** A review belongs to a booking (`reviews.booking_id` is unique), so only a member who paid for a session that already took place can rate it, and only once. Two requests at the same moment can't both get in: the second hits the unique key and gets `409 ALREADY_REVIEWED`. Reviews are final, which keeps ratings honest. Profiles show the member's first name and initial. Hidden reviews stay in the database, so the admin can show one again, but they leave the trainer's average. Averages are computed in one grouped query for a whole list of trainers, not one query per trainer.
- **Expiry is enforced on read.** `Booking.statusAt(now)` already reports overdue bookings as expired, and the scheduled job persists that every minute, one transaction per booking.
- **Money.** Prices are `BigDecimal` in JOD, which has three decimal places (1 JOD = 1000 fils). Stripe can't charge JOD, so cards are charged the USD equivalent at the dinar's fixed peg (`ChargeConversion`), and receipts show both amounts. Charge currency and rate are configuration, so a JOD-capable provider needs no code change.
- **Short access tokens, rotating refresh tokens.** Access tokens last 15 minutes. Refresh tokens are random, stored only as SHA-256 hashes, and replaced on every use. If a replaced token shows up again later, someone may have copied it, so all of that user's sessions end. Every token carries the user's token version: changing or resetting the password, "log out of all devices" and deactivation raise it, which ends all existing sessions at once without a token blocklist.
- **Google sign-in without a Google library.** The app sends the ID token Google gave it, and `GoogleIdTokenVerifier` checks it with JJWT against Google's published keys (cached, fetched again when Google changes them): signature, issuer, audience (our client ID) and expiry. Accounts are tied to Google's permanent `sub` ID, not the email. A first sign-in links an existing member only when Google owns the address (Gmail, or a Google Workspace domain); otherwise the member logs in with the password. If someone had signed up with that address but never verified it, their password is removed. Trainers and admins keep their password logins, and two-factor authentication still applies after Google.
- **Two-factor login in two steps.** A correct password for a two-factor account returns a challenge token instead of a session: a 10-minute JWT that only the code step accepts. The codes are computed in `Totp` (HMAC-SHA1 over 30-second steps, no library) and each code works once. Wrong codes count toward the same lock as wrong passwords, and entering the password again doesn't reset the count, so codes can't be guessed. Recovery codes are stored as SHA-256 hashes. An admin can't get a session without it, and admin sessions from before it was required are ended. The app's secret is stored as it is, because the server needs it to check codes; a production setup would encrypt it with a key from a secrets manager.
- **Secrets stay out of the repo.** Configuration comes from a git-ignored `local.properties` or `.env`. A pre-commit hook blocks key-like strings, and the app refuses to start with live Stripe keys.

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail) |
| Database | MySQL 8.4, Hibernate 6 |
| Auth | JWT (JJWT), BCrypt |
| Payments | Stripe REST API via `RestClient` |
| Tests | JUnit 5, H2, fake Stripe and Google key servers |
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
# optional: set app.admin.initial-password in local.properties (otherwise a random one is printed once)
# optional: send real emails through Gmail (see "Real emails with Gmail" below)
mvn spring-boot:run
```

The `gymdb` schema and its tables are created on first start. The `gymapp` user can only access `gymdb` and cannot drop tables.

### Demo data

On startup the app seeds any missing branches, trainers and the admin account (`admin@gym.com`). The admin password comes from `app.admin.initial-password` in `local.properties` (`ADMIN_PASSWORD` in `.env`). If it isn't set, a random password is generated and printed once in the log. Change it in the app under Profile > Change password. At the admin's first login, the app asks them to set up an authenticator app such as Google Authenticator or the iPhone's Passwords app. If the phone and the recovery codes are both lost, setting `two_factor_secret` to `NULL` for `admin@gym.com` in MySQL starts the setup again at the next login. The trainer accounts are demo data for local development only.

| Role | Email | Password |
|---|---|---|
| Trainer | `<firstname>.trainer@gym.com`, e.g. `sara.trainer@gym.com` | `Trainer1234` |

Trainers by branch: Abdoun (sara, yousef, rania, khaled, maya), Khalda (omar, dana, ahmad, hala), Sweifieh (lina, faris, noor, hamza, jana), Shmeisani (laith, ruba, qais, aya), Jubeiha (mohammad, leen, bashar, tala). Members sign up through the app.

### Configuration

Settings are in `src/main/resources/application.properties`. Secrets go in `local.properties` (or `.env` for Docker):

| Property | Env var (Docker) | Notes |
|---|---|---|
| `spring.datasource.password` | `DB_PASSWORD` | Set by `create-db-user.sh` |
| `app.jwt.secret` | `JWT_SECRET` | Required. Base64, 64+ bytes |
| `app.admin.initial-password` | `ADMIN_PASSWORD` | First admin's password; random and logged once if empty |
| `app.auth.google.client-id` | `GOOGLE_CLIENT_ID` | Optional. Google's "Web application" client ID; empty turns Google sign-in off |
| `app.payments.stripe.secret-key` | `STRIPE_SECRET_KEY` | `sk_test_...` only |
| `app.payments.stripe.publishable-key` | `STRIPE_PUBLISHABLE_KEY` | `pk_test_...`, passed to the app |
| `app.payments.stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | Optional |
| `app.payments.charge-currency` / `app.payments.jod-exchange-rate` | | `USD` / `1.41044` (fixed peg). Use `JOD` / `1` with a provider that supports dinars |
| `app.notifications.mode` | `EMAIL_MODE` | `console` (default) writes emails to the log; `email` sends them |
| `spring.mail.username` | `MAIL_USERNAME` | The Gmail address that sends the emails |
| `spring.mail.password` | `MAIL_PASSWORD` | A Gmail app password, not the Google account password |
| `app.sms.twilio.account-sid` | `TWILIO_ACCOUNT_SID` | Optional. With the two below, SMS codes are texted by Twilio Verify; without them they are written to the log |
| `app.sms.twilio.auth-token` | `TWILIO_AUTH_TOKEN` | |
| `app.sms.twilio.verify-service-sid` | `TWILIO_VERIFY_SERVICE_SID` | The Verify service (`VA...`) |

Without Stripe keys the service still starts, and the payment endpoints return `503 PAYMENTS_NOT_CONFIGURED`. Without a Google client ID, `/api/auth/google` returns `503 GOOGLE_SIGN_IN_OFF`.

### Google sign-in (optional)

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project and open **Google Auth Platform**. Fill in **Branding** (app name, support email) and, under **Audience**, choose **External** and add your Google account as a test user.
2. Under **Clients**, create three OAuth clients:
   - **Web application**, with no origins or redirect URIs. Its client ID goes into `app.auth.google.client-id` here, and into the app as its server client ID.
   - **Android**, with package name `com.malik.gym_booking` and the SHA-1 of your debug key: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   - **iOS**, with bundle ID `com.malik.gymBooking`.
3. Set up the app as described in its README.

### Real emails with Gmail (optional)

By default, emails are written to the log. To send them from a Gmail account:

1. Turn on **2-Step Verification** for that Google account at [myaccount.google.com/security](https://myaccount.google.com/security). Google only offers app passwords after that.
2. Create an app password at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), named for example "Gym Booking". Google shows a 16-letter password once.
3. Add to `local.properties`, with the password written without spaces:

   ```properties
   app.notifications.mode=email
   spring.mail.username=your.address@gmail.com
   spring.mail.password=your16letterapppassword
   ```

4. Restart. Emails come from that address with the name "Gym Booking" (`app.mail.from-name`).

Gmail limits how many emails a personal account can send per day, which is fine for development; a real deployment would use an email service, set with `spring.mail.host` and `spring.mail.port`. If Gmail rejects the login, the log says so and the emails wait in the outbox until it works.

### Real SMS with Twilio (optional)

By default, the SMS codes for phone numbers are written to the log. To text them:

1. Sign up for a free trial at [twilio.com](https://www.twilio.com/try-twilio). On a trial account, Twilio only texts numbers you have verified in its console, such as your own.
2. On the Twilio Console home page, copy the **Account SID** and the **Auth Token**.
3. Under **Verify > Services**, create a service named "Gym Booking" and copy its **Service SID** (it starts with `VA`).
4. Add to `local.properties` and restart:

   ```properties
   app.sms.twilio.account-sid=AC...
   app.sms.twilio.auth-token=your-auth-token
   app.sms.twilio.verify-service-sid=VA...
   ```

Twilio charges for each verification; see the [Verify page](https://www.twilio.com/en-us/user-authentication-identity/verify) for prices.

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
| POST | `/api/auth/verify` | `email, code` | `200 {token, refreshToken, user}` |
| POST | `/api/auth/resend-code` | `email` | `200` |
| POST | `/api/auth/google` | `idToken` | Members only. Same answers as login. A first sign-in links the member with the same email if Google owns it, or creates a member (no phone or password yet) |
| POST | `/api/auth/login` | `email, password` | `200 {token, refreshToken, user}`, or for two-factor accounts `200 {twoFactor, challengeToken}` with `twoFactor` = `CODE_REQUIRED` or `SETUP_REQUIRED` (an admin without an authenticator app yet) |
| POST | `/api/auth/login/2fa` | `challengeToken, code` | `200 {token, refreshToken, user}`. `code` is the 6-digit code from the app or a recovery code |
| POST | `/api/auth/login/2fa/setup` | `challengeToken` | `200 {secret, otpauthUri}` for an admin's first login; show `otpauthUri` as a QR code |
| POST | `/api/auth/login/2fa/confirm` | `challengeToken, code` | `200 {recoveryCodes, token, refreshToken, user}`; the admin's older sessions end |
| POST | `/api/auth/accept-invite` | `email, code, password` | `200 {token, refreshToken, user}`; for trainers added by an admin |
| POST | `/api/auth/forgot-password` | `email` | `200`. Emails a reset code (valid 10 minutes) to a verified account; the same answer whether or not the email exists |
| POST | `/api/auth/reset-password` | `email, code, password` | `200`; then log in with the new password. Also lifts a login lock |
| POST | `/api/auth/refresh` | `refreshToken` | `200 {token, refreshToken, user}`; the old refresh token stops working |
| POST | `/api/auth/logout` | `refreshToken` | `204`; ends this device's session |
| GET | `/api/users/me` | | `200` current user, including `phoneVerified`, `twoFactorEnabled` and `hasPassword` (false after signing up with Google, until one is set with Forgot password) |
| PUT | `/api/users/me/phone` | `phone` | `200` user, e.g. after signing up with Google. A different number has to be confirmed again |
| POST | `/api/users/me/phone/code` | | `200 {phone, resendAfterSeconds, expiresInMinutes}`; texts a 6-digit code to the user's number |
| POST | `/api/users/me/phone/confirm` | `code` | `200` user with `phoneVerified: true` |
| POST | `/api/users/me/password` | `currentPassword, newPassword` | `200 {token, refreshToken, user}`; other devices are logged out |
| POST | `/api/users/me/logout-all` | | `204`; ends every session on every device |
| POST | `/api/users/me/2fa/setup` | `password, code?` | `200 {secret, otpauthUri}`. `code` (from the current app, or a recovery code) only when two-factor is already on, to move to a new phone |
| POST | `/api/users/me/2fa/confirm` | `code` | `200 {recoveryCodes}`; turns two-factor on with a code from the new app and replaces old recovery codes |
| POST | `/api/users/me/2fa/disable` | `password, code` | `204`; not allowed for admins (`403 TWO_FACTOR_REQUIRED`) |

### Branches and trainers

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/api/branches` | any | Optional `?city=` |
| GET | `/api/branches/{id}` | any | |
| POST / PUT / DELETE | `/api/branches[/{id}]` | admin | A branch with trainers or bookings can't be deleted (`409 BRANCH_IN_USE`) |
| GET | `/api/branches/{id}/trainers` | any | Optional `?category=&gender=&maxRate=` |
| GET | `/api/trainers` | any | Trainers at every branch, with the same optional filters, e.g. `?category=YOGA` |
| GET | `/api/trainers/{id}` | any | Profile and weekly schedule. Trainer lists and profiles include `averageRating` (null without reviews) and `reviewCount` |
| GET | `/api/trainers/{id}/reviews` | any | `{averageRating, reviewCount, reviews}`, latest 50, hidden ones left out |
| GET | `/api/trainers/{id}/availability?date=&duration=` | any | Free start times. `duration` is 30/45/60/90, `date` is within the next 14 days. `closedReason` is set when the branch is closed or the trainer is off all day |

### Admin: trainers

| Method | Path | Notes |
|---|---|---|
| GET | `/api/admin/trainers` | All trainers with status (`INVITED`, `ACTIVE`, `DEACTIVATED`), contact details, schedule and upcoming bookings |
| GET | `/api/admin/trainers/{id}` | |
| POST | `/api/admin/trainers` | Creates the trainer and emails an invite code (valid 7 days) |
| PUT | `/api/admin/trainers/{id}` | Edits details; a new email address for an invited trainer gets a fresh invite |
| PUT | `/api/admin/trainers/{id}/schedule` | `{blocks: [{dayOfWeek, startTime, endTime}]}` replaces the weekly schedule |
| POST | `/api/admin/trainers/{id}/invite` | Sends a new invite code |
| POST | `/api/admin/trainers/{id}/deactivate` | Optional `{reason}`, shown to members whose bookings are cancelled |
| POST | `/api/admin/trainers/{id}/reactivate` | |

### Admin: bookings, blocked times and reviews

| Method | Path | Notes |
|---|---|---|
| GET | `/api/admin/bookings` | Sessions that haven't ended, soonest first. `?past=true` lists ended ones (latest 200). Optional `branchId`, `trainerId`, `status`. Adds member email and phone, and `gymCanCancel` |
| GET | `/api/admin/bookings/{id}` | |
| POST | `/api/admin/bookings/{id}/cancel` | Optional `{reason}`. Allowed until the session starts; a paid session is refunded in full |
| GET | `/api/admin/blocked-times` | Blocks that haven't ended |
| POST | `/api/admin/blocked-times/preview` | Same body as below; returns `{bookings, paidBookings}` it would cancel |
| POST | `/api/admin/blocked-times` | `{branchId or trainerId, startDate, endDate, startTime?, endTime?, reason?}`. Without times the whole days are blocked. Cancels the bookings inside |
| DELETE | `/api/admin/blocked-times/{id}` | Frees the time again |
| GET | `/api/admin/reviews` | Newest first, with the member's full name. `?hidden=true` lists only hidden ones |
| POST | `/api/admin/reviews/{id}/hide` | `{reason}`, emailed to the member. The review leaves the profile and the average |
| POST | `/api/admin/reviews/{id}/show` | Shows a hidden review again |

### Bookings

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/bookings` | member | `{trainerId, date, startTime, durationMinutes, note?}`. `403 PHONE_NOT_VERIFIED` until the member has confirmed their phone number |
| GET | `/api/bookings/mine` | member | |
| GET | `/api/bookings/{id}` | member | Bookings include `rating` (once rated) and `canReview` |
| POST | `/api/bookings/{id}/review` | member | `{rating: 1-5, comment?}` (max 500 characters). Paid sessions that took place in the last 30 days, once: `409 CANNOT_REVIEW`, `409 ALREADY_REVIEWED` |
| POST | `/api/bookings/{id}/cancel` | member | Refunds automatically if paid |
| GET | `/api/trainer/requests` | trainer | Pending requests, most urgent first |
| GET | `/api/trainer/schedule` | trainer | Upcoming accepted and paid sessions |
| POST | `/api/trainer/requests/{id}/accept` | trainer | Optional `{message}` |
| POST | `/api/trainer/requests/{id}/reject` | trainer | Optional `{message}` |
| GET | `/api/trainer/reviews` | trainer | Their own reviews, like the profile |
| PUT | `/api/trainer/reviews/{id}/reply` | trainer | `{reply}`; answering again replaces the reply. `409 REVIEW_HIDDEN` for hidden reviews |

Booking lifecycle:

```
REQUESTED -> ACCEPTED -> PAID
    |            |         |
    |            |         +-> CANCELLED (until 24 h before, full refund)
    |            +-> EXPIRED (not paid within 12 h) / CANCELLED
    +-> REJECTED / EXPIRED (no answer within 24 h) / CANCELLED
```

The gym can cancel any booking until it starts (for example when a trainer is deactivated); paid ones are always refunded in full.

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
| SMS codes | 1 / 60 s per number, 5 a day per account | `429 RESEND_TOO_SOON`, `429 TOO_MANY_CODES` |
| Wrong passwords or two-factor codes | 5 in a row, then 15 min lock | `429 ACCOUNT_LOCKED` |
| Wrong verification or SMS codes | 5 per code | `400 TOO_MANY_ATTEMPTS` |

`429` responses include `Retry-After`. All limits are configurable in `application.properties`.

## Project structure

```
src/main/java/com/mycompany/gymbooking
├── config/         security, clock, seed data, schema upgrades
├── controller/     REST endpoints
├── dto/            request and response records
├── exception/      API exceptions and the global handler
├── model/          JPA entities and enums
├── notification/   email outbox and sending, HTML layout, payment emails, security alerts
├── payment/        PaymentGateway, Stripe client, webhook verification
├── phone/          phone number rules, SMS codes (Twilio Verify or the log)
├── repository/     Spring Data repositories
├── security/       JWT, Google ID tokens, two-factor codes (TOTP), rate limiting, security error handling
└── service/        business logic, the expiry job and session reminders
```

## Tests

```bash
mvn verify
```

124 tests. They need no MySQL, network, Stripe, Google, mail or Twilio account.

| Test | Covers |
|---|---|
| `BookingTest` | Booking state machine, deadlines, expiry, 24 h cancellation rule, gym cancellations |
| `TrainerTest` | Trainer status (invited, active, deactivated) and when a trainer is bookable |
| `PaymentTest` | Payment status transitions |
| `CurrencyUnitsTest` | Minor units per currency and amounts Stripe cannot charge |
| `ChargeConversionTest` | JOD to USD conversion and rounding |
| `StripeWebhookVerifierTest` | Signature and timestamp checks |
| `StripePaymentGatewayTest` | Requests sent to Stripe, idempotency, error handling |
| `InMemoryRateLimiterTest` | Token bucket refill and `Retry-After` |
| `GoogleIdTokenVerifierTest` | Google ID tokens: signature, audience, issuer, expiry, key rotation, Google being unreachable |
| `TotpTest` | Authenticator codes against the RFC 6238 test values, Base32, clock drift, the QR code link |
| `PhoneNumbersTest` | Jordanian numbers typed in different ways, numbers that can't exist, landlines, other countries |
| `TwilioPhoneCodesTest` | Requests sent to a local fake of Twilio Verify, its answers, refused numbers, Twilio being down |
| `EmailLayoutTest` | The HTML version of an email: details, steps, codes, paragraphs, escaping |
| `EmailDeliveryTest` | Real SMTP against a local fake mail server: text and HTML parts, sender name, rolled-back transactions, retries, giving up, a rejected login, cleanup |
| `SessionApiTest` | End-to-end: refresh token rotation and reuse, logout, logout on all devices, change password |
| `GoogleSignInApiTest` | End-to-end: Google sign-up and phone number, linking members, members only, unverified sign-ups, invalid tokens, two-factor after Google |
| `SecurityAlertsApiTest` | End-to-end: alerts for password changes and resets, two-factor on, moved and off, Google sign-in added |
| `PhoneVerificationApiTest` | End-to-end: numbers by country, the SMS code before the first booking, changing the number, wrong and expired codes, daily limit, converting old numbers |
| `SessionRemindersApiTest` | End-to-end: one reminder to the member and the trainer a day before, none for sessions paid within that day |
| `TwoFactorApiTest` | End-to-end: admin setup at first login, codes and recovery codes, used codes, lockout, expired challenges, moving to a new phone, turning it off |
| `GymBookingApiTest` | End-to-end over HTTP: auth, password reset, roles, access control, double booking, payment and refund flow, declines, webhooks, late payments, Stripe outages, trainers by category |
| `AdminTrainerApiTest` | End-to-end: admin-only access, invite flow, validation, editing, schedules, deactivation with refunds, branches in use |
| `AdminBookingApiTest` | End-to-end: admin bookings list and filters, gym cancellations with refunds, trainer and branch blocks, validation |
| `ReviewApiTest` | End-to-end: rating only after the session, once, within 30 days, averages on profiles and lists, trainer replies, hiding with an email to the member, showing again |

CI runs the test suite and a Docker Compose smoke test on every push and pull request.

## Roadmap

- [x] Accounts, email verification, JWT, roles
- [x] Branches, trainers, availability
- [x] Booking requests, accept / decline, expiry
- [x] Stripe payments, refunds, confirmation emails
- [x] Admin: trainer invites, profiles, weekly schedules, deactivation
- [x] Admin: all bookings, gym cancellations, blocked times, branch management
- [x] Refresh tokens, logout on all devices, change password
- [x] Two-factor authentication with an authenticator app
- [x] Google sign-in for members
- [x] Real emails: Gmail, HTML layout, retries, security alerts, session reminders
- [x] Phone numbers by country, confirmed by SMS
- [x] Session reviews, trainer replies and moderation

## License

[MIT](LICENSE)
