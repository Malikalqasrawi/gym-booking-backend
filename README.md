# Gymbooking backend (Java + Spring Boot)

The REST API for the Gym Booking app. The Flutter app lives in `~/StudioProjects/gym_booking`.

**Stage 1 (done):** accounts. Includes sign-up, email verification code, login with a JWT token, and roles (Member / Trainer / Admin).

**Stage 2 (done):** MySQL database, branches CRUD API, trainers linked to branches, weekly working hours, and an availability API (free start times for a date + duration). The app shows a branch map → trainers → date/duration/time picker.

**Stage 3 (done):** booking requests. A member sends a request, the trainer accepts or declines it, and the time stays blocked while it waits (24 h max). Prices come from each trainer's hourly rate. Members see "My bookings"; trainers see "Requests" and "Schedule".

**Stage 4 (done):** payments with **Stripe (test mode)**. After the trainer accepts, the member has 12 h to pay in Stripe's payment screen; then the booking is `PAID` and a confirmation email goes out. Paid sessions can be cancelled with a full refund until 24 h before. See section 8 for the Stripe setup.

---

## 1. How to run it

**One-time setup:** install MySQL (see section 2), copy `local.properties.example` to `local.properties` (same folder as `pom.xml`), run `bash scripts/create-db-user.sh` (fills in the database password), and add a JWT secret (the example file shows the command that makes one).

**Secrets rule:** everything secret lives in `local.properties`, which `.gitignore` keeps off GitHub:

| Secret | Why it must stay private |
|---|---|
| `spring.datasource.password` | gymapp's password: read/change every row in gymdb |
| `app.jwt.secret` | Signs login tokens. Whoever has it can create a token for any user, including the admin |
| `app.payments.stripe.secret-key` | `sk_test_...`: can create payments and refunds on your Stripe account |
| `app.payments.stripe.webhook-secret` | `whsec_...`: proves a webhook message really comes from Stripe |
| `spring.mail.password` (later) | Lets anyone send email as you |

The Flutter app contains **no** secrets (anyone can unzip an APK and read it), only the backend's address. It gets Stripe's *publishable* key (`pk_test_...`) from the backend; that one is public by design. The starter passwords in `DataSeeder` (`Admin1234`, `Trainer1234`) are for development only; change them before real users.

1. Open the project in **NetBeans**. If it was already open, right-click the project and choose **Reload POM**.
2. Right-click the project and choose **Build with Dependencies**. The first time takes a few minutes because Maven downloads Spring Boot (~100 MB).
3. Press **Run** (the green ▶).
4. Wait for this line in the Output window: `Tomcat started on port 8080`.
5. In your browser, open **http://localhost:8080/api/health**. You should see `{"status":"UP", ...}`.

Starter data is created automatically when the backend starts (only what's missing; see `StarterData.java`): **5 branches** and **22 trainers**. Every trainer's password is `Trainer1234`, and the email is `firstname.trainer@gym.com`:

| Role | Email | Password |
|---|---|---|
| Admin | admin@gym.com | Admin1234 |
| Abdoun | sara, yousef, rania, khaled, maya | Trainer1234 |
| Khalda | omar, dana, ahmad, hala | Trainer1234 |
| Sweifieh | lina, faris, noor, hamza, jana | Trainer1234 |
| Shmeisani | laith, ruba, qais, aya | Trainer1234 |
| Jubeiha | mohammad, leen, bashar, tala | Trainer1234 |

(e.g. `yousef.trainer@gym.com`. The people are made up.)

Members create their own accounts in the app.

---

## 2. Where the data is stored (MySQL)

The data lives in a **MySQL** server running on your Mac. Java never stores data itself: it describes the tables (`@Entity` classes) and sends SQL through Hibernate.

- **Install:** MySQL Community Server 8.4 LTS (macOS ARM DMG) + MySQL Workbench.
- **Its own MySQL user:** the backend logs in as **`gymapp`**, not `root`. Create it once, in Terminal from this folder:
  ```bash
  bash scripts/create-db-user.sh
  ```
  It asks for your MySQL root password, creates `gymapp`, and writes gymapp's random password into `local.properties` (it's never shown).
  - `gymapp` may only read, add, change, and delete rows in **gymdb**, and create/alter its tables (Hibernate needs that for `ddl-auto=update`).
  - It can't see other databases, can't create users, and can't `DROP` tables. If someone ever managed to run their own SQL through the backend, that's all they could do.
  - `root` is still yours for Workbench.
- **The database is created automatically:** the URL has `createDatabaseIfNotExist=true`, so the first run creates `gymdb`, and Hibernate creates the tables (`ddl-auto=update`).
- **See your tables:** MySQL Workbench → *Local instance 3306* → schema **gymdb** → right-click a table → *Select Rows*.

| Table | What's in it |
|---|---|
| `users` | Members, trainers and admins (column `user_type` says which). Trainers have `branch_id` → `branches.id` |
| `branches` | The gym locations, with GPS position and opening hours |
| `working_hours` | Trainers' weekly schedule. `trainer_id` → `users.id` |
| `trainer_tags`, `trainer_certifications` | A trainer's tags ("Beginners", "Weight loss") and certificates: lists get their own table (`@ElementCollection`), one row per item, `trainer_id` → `users.id` |
| `bookings` | Session requests and bookings. `member_id` and `trainer_id` → `users.id`, `branch_id` → `branches.id`, plus date, times, price, `status`, `pay_by_at` (pay before), `refundable_until` (cancel with refund before) |
| `payments` | One row per paid booking: `booking_id` (unique → it can't be charged twice), amount, Stripe's id (`pi_...`), status, card brand + last 4 digits (never the card number), refund id |

- **To start fresh:** in Workbench (as root) run `DROP DATABASE gymdb;` then restart the backend. gymapp is allowed to create `gymdb` again, and the seeder refills everything.

## 3. How a request travels through the code

Example: the app sends `POST /api/auth/login`.

```
Flutter app
   │  JSON: { "email": "...", "password": "..." }
   ▼
JwtAuthenticationFilter   (security)    checks for a token; login doesn't need one
   ▼
AuthController            (controller)  receives the JSON as a LoginRequest, validates it (@Valid)
   ▼
AuthService               (service)     the rules: does the user exist? password correct? verified?
   ▼
UserRepository            (repository)  SELECT ... FROM users WHERE email = ?
   ▼
H2 database file
   ▲
AuthService creates a token with TokenService (JWT)
   ▲
AuthController returns AuthResponse → turned into JSON:
   { "token": "eyJ...", "user": { "id": 4, "role": "MEMBER", ... } }
```

If anything goes wrong, a service throws, for example, `new UnauthorizedException(...)`. **GlobalExceptionHandler** then turns it into:

```json
{ "status": 401, "code": "INVALID_CREDENTIALS", "message": "Email or password is incorrect" }
```

---

## 4. Folder structure (what each package is for)

```
com.mycompany.gymbooking
├── Gymbooking.java        ← main(): starts everything
├── controller/            ← "waiters": URLs → service calls → JSON        (AuthController, UserController, HealthController)
├── service/               ← "kitchen": business rules                      (AuthService + AuthServiceImpl)
├── repository/            ← "storage room door": database queries          (UserRepository)
├── model/                 ← entities = database tables                     (User, Member, Trainer, Admin, Role)
├── dto/                   ← shapes of the JSON going in and out            (SignUpRequest, AuthResponse, ...)
├── security/              ← tokens and who-is-logged-in                    (JwtTokenService, JwtAuthenticationFilter, ...)
├── notification/          ← sending messages                               (NotificationSender + Console/Email versions, PaymentEmailListener)
├── payment/               ← talking to the payment provider                (PaymentGateway + StripePaymentGateway, webhook signature check)
├── exception/             ← error types + one place that formats them
└── config/                ← security rules, starter data
```

**Rule of thumb:** controllers never talk to the database, and repositories never contain rules. Each layer only talks to the one below it.

---

## 5. OOP principles: where to find each one

| Principle | Where | What it does here |
|---|---|---|
| **Abstraction** | `User` is `abstract` | Nobody is "just a user". You must be a Member, Trainer, or Admin. |
| **Inheritance** | `Member`, `Trainer`, `Admin` extend `User`. Exception classes extend `ApiException`. | Shared fields (name, email, password) are written once. |
| **Polymorphism** | `getRole()`, `getDisplayTitle()`, `ApiException.getStatus()` | `user.getDisplayTitle()` gives "Member" or "Trainer · Yoga" depending on the real object. One `@ExceptionHandler` handles every error type. |
| **Encapsulation** | `User` fields are `private`. No `setPasswordHash`; instead there's `markVerified()` and `issueVerificationCode()`. | Other classes can't put a user into an invalid state (e.g., verified *and* still holding a code). |
| **Interfaces (loose coupling)** | `AuthService`, `TokenService`, `NotificationSender`, `UserRepository`, `RateLimiter`, `PaymentGateway` | `AuthServiceImpl` only knows the interfaces. Swapping console ↔ Gmail, JWT ↔ something else, in-memory ↔ Redis rate limits, or Stripe ↔ another payment provider doesn't touch the code that uses them. |
| **Dependency injection** | Constructors of every `@Service` / `@Component` | Classes never write `new SomethingService()`; Spring passes them in. This makes testing and swapping easy. |
| **Relationships** | `Trainer.branch`, `WorkingHours.trainer`, `Booking.member/trainer/branch` (all `@ManyToOne`) | Tables point to each other with foreign keys (`branch_id`, `trainer_id`, `member_id`) instead of copying data. |
| **State machine** | `Booking` + `BookingStatus`, `Payment` + `PaymentStatus` | The status only changes through methods that check the rules (`accept()` only works on a `REQUESTED` booking, `markRefunded()` only on a `SUCCEEDED` payment). |
| **Events (observer pattern)** | `BookingPaidEvent` → `PaymentEmailListener` | The payment code just announces "booking 58 was paid"; the listener sends the emails after the save. Adding SMS later = one more listener. |
| **Adapter pattern** | `SecurityUser` wraps `User` | Spring Security gets what it needs, while the `model` package stays free of security code. |

---

## 6. API endpoints (Stage 1)

| Method | URL | Needs token? | Body | Success |
|---|---|---|---|---|
| GET | `/api/health` | no | – | `{status:"UP"}` |
| POST | `/api/auth/signup` | no | fullName, email, phone, password | 201 `{message}` + code printed in the console |
| POST | `/api/auth/verify` | no | email, code | 200 `{token, user}` |
| POST | `/api/auth/resend-code` | no | email | 200 `{message}` |
| POST | `/api/auth/login` | no | email, password | 200 `{token, user}` |
| GET | `/api/users/me` | **yes** | – | 200 `{id, fullName, email, phone, role, title}` |

Error codes the app reacts to: `EMAIL_TAKEN`, `INVALID_CODE`, `CODE_EXPIRED`, `TOO_MANY_ATTEMPTS`, `RESEND_TOO_SOON`, `ALREADY_VERIFIED`, `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `EMAIL_NOT_VERIFIED`, `VALIDATION_FAILED`, `UNAUTHORIZED`, `RATE_LIMITED`.

### Protection against spam and password guessing

| Rule | Limit | Answer when exceeded |
|---|---|---|
| Requests to `/api/auth/**` per IP | 10 per minute | 429 `RATE_LIMITED` |
| Requests to anything else per IP | 100 per minute | 429 `RATE_LIMITED` |
| "Resend code" per account | 1 per 60 seconds | 429 `RESEND_TOO_SOON` |
| Wrong passwords in a row per account | 5, then locked 15 minutes | 429 `ACCOUNT_LOCKED` |
| Wrong verification codes | 5 per code | 400 `TOO_MANY_ATTEMPTS` |

Every 429 has a `Retry-After` header (seconds to wait). All numbers are in `application.properties`.

How the per-IP limit works (`InMemoryRateLimiter`, the *token bucket* algorithm): each IP has a bucket of 10 tokens; each request takes one; tokens drip back at 10 per minute (1 every 6 s). Quick bursts are fine, non-stop spam gets 1 request every 6 s. `RateLimitFilter` runs first in the security chain, before the JWT check and BCrypt, so blocked requests cost almost nothing.

Limits of this approach: counters live in memory (reset on restart, not shared between servers → swap in a Redis version via the `RateLimiter` interface), and someone with thousands of IPs can still get through, which is why production apps also put a proxy/CDN (Nginx, Cloudflare) in front. The login lock also means someone who knows your email can lock you out for 15 minutes; that's the usual trade-off.

**Verification code rules:** 6 digits, valid for 10 minutes, **5 wrong tries** (`app.verification.max-attempts`). After the 5th wrong try the code is locked, even the right code is refused (`TOO_MANY_ATTEMPTS`), until the user taps "Resend code". A new code resets the count. The counter is the `verification_attempts` column in `users`.

### Branches: a full CRUD API

One address (`/api/branches`); the **HTTP method** decides the action.

| Method | URL | CRUD | Who | Success |
|---|---|---|---|---|
| GET | `/api/branches` | Read all | any logged-in user | 200 + list |
| GET | `/api/branches?city=Amman` | Read (filtered) | any logged-in user | 200 + list |
| GET | `/api/branches/{id}` | Read one | any logged-in user | 200 + branch |
| POST | `/api/branches` | Create | **admin** | 201 + branch + `Location` header |
| PUT | `/api/branches/{id}` | Update | **admin** | 200 + updated branch |
| DELETE | `/api/branches/{id}` | Delete | **admin** | 204 (empty) |

Body for POST / PUT:

```json
{
  "name": "Jubaiha Branch",
  "address": "University Street",
  "city": "Amman",
  "latitude": 32.0167,
  "longitude": 35.8669,
  "phone": "+96265000004",
  "openingTime": "06:30",
  "closingTime": "22:00"
}
```

Branch error codes: `BRANCH_NOT_FOUND` (404), `BRANCH_NAME_TAKEN` (409), `INVALID_HOURS` (400), `VALIDATION_FAILED` (400), `FORBIDDEN` (403, not an admin), `INVALID_PARAMETER` (400, e.g. `/api/branches/abc`).

### Trainers & availability (Stage 2)

| Method | URL | Who | Returns |
|---|---|---|---|
| GET | `/api/branches/{branchId}/trainers` | any logged-in user | Trainers at that branch with their profile (category, gender, languages, tags, certifications, hourly rate) and weekly `schedule`. Optional filters: `?category=YOGA&gender=FEMALE&maxRate=20` |
| GET | `/api/trainers/{id}` | any logged-in user | One trainer + schedule |
| GET | `/api/trainers/{id}/availability?date=2026-10-04&duration=60` | any logged-in user | Free start times that day |

How free times are worked out (`AvailabilityServiceImpl`):
1. Take the trainer's working blocks for that weekday.
2. Cut each block to the branch's opening hours.
3. Offer a start every 30 minutes, as long as the whole session ends inside the block.
4. For today, skip times less than 60 minutes from now (Amman time).
5. *(Stage 3)* skip times that overlap requested or booked sessions.

Rules: `duration` must be 30, 45, 60 or 90 (`INVALID_DURATION`); `date` must be from today up to 13 days ahead (`DATE_OUT_OF_RANGE`); missing `date` → `MISSING_PARAMETER`; unknown trainer → `TRAINER_NOT_FOUND`.

### Bookings (Stage 3)

| Method | URL | Who | What it does |
|---|---|---|---|
| POST | `/api/bookings` | member | Request a session. Body: `{"trainerId":2,"date":"2026-10-07","startTime":"10:00","durationMinutes":60,"note":"optional"}` → 201 |
| GET | `/api/bookings/mine` | member | My bookings, newest date first |
| GET | `/api/bookings/{id}` | member | One of MY bookings |
| POST | `/api/bookings/{id}/cancel` | member | Cancel one of MY bookings |
| GET | `/api/trainer/requests` | trainer | Requests waiting for MY answer, most urgent first |
| GET | `/api/trainer/schedule` | trainer | MY accepted sessions that haven't finished |
| POST | `/api/trainer/requests/{id}/accept` | trainer | Optional body `{"message":"See you there!"}` |
| POST | `/api/trainer/requests/{id}/reject` | trainer | Optional body `{"message":"I'm away that day"}` |

**A booking's life:** `REQUESTED` → `ACCEPTED` or `REJECTED`, or `EXPIRED` if the trainer doesn't answer within 24 h (or before the session starts). `ACCEPTED` → `PAID`, or `EXPIRED` if not paid within 12 h. `REQUESTED` and `ACCEPTED` can be `CANCELLED` by the member before the session starts; `PAID` until 24 h before (with a full refund). The rules live in `Booking` itself (`accept()`, `markPaid()`, `cancelByMember()`...), and there is no `setStatus()`.

**Rules the backend checks when a request comes in:**
1. The time must be one the availability API would offer (same code: duration, 14 days, working hours, branch hours, 60 min notice, not taken).
2. No double booking: the trainer's row is locked (`SELECT ... FOR UPDATE`) while checking, so two members tapping the same time at the same second can't both get it.
3. A member can't have two sessions at the same time (`MEMBER_BUSY`).
4. A member can have at most 3 unanswered requests (`TOO_MANY_PENDING`), so nobody can block a trainer's whole week.
5. Price = trainer's hourly rate × duration (Sara 20, Omar 18, Lina 22 JOD/h). It's saved on the booking, so later rate changes don't touch old bookings.

**Who can see what:** the member/trainer always comes from the token. Queries include the owner (`findByIdAndMemberId`, `findByIdAndTrainerId`), so someone else's booking answers **404**, as if it didn't exist.

**Same moment, two changes:** `Booking` has a `@Version` number. If a trainer accepts while the member cancels, one wins and the other gets 409 `BOOKING_CHANGED` instead of silently overwriting.

**Expiry:** `BookingExpiryJob` runs every minute and marks unanswered requests and unpaid accepted bookings `EXPIRED` (and tells the member). Even between runs, the API already treats them as expired.

Booking error codes: `SLOT_NOT_AVAILABLE`, `MEMBER_BUSY`, `TOO_MANY_PENDING`, `TRAINER_NOT_BOOKABLE`, `BOOKING_NOT_FOUND`, `BOOKING_NOT_PENDING`, `REQUEST_EXPIRED`, `BOOKING_NOT_CANCELLABLE`, `SESSION_STARTED`, `TOO_LATE_TO_CANCEL`, `BOOKING_CHANGED`.

### Payments (Stage 4)

| Method | URL | Who | What |
|---|---|---|---|
| POST | `/api/bookings/{id}/payment` | member | Start paying one of MY accepted bookings → `clientSecret`, `publishableKey`, amount, `payBy`, `cancelUntilAfterPaying` |
| POST | `/api/bookings/{id}/payment/confirm` | member | "I paid": the backend asks **Stripe**, and if the money arrived the booking becomes `PAID` |
| POST | `/api/payments/stripe/webhook` | Stripe | Stripe's own "payment succeeded" message (optional, section 8). No login; checked by its signature |

**How a payment works:**
1. The app calls `/payment`. The backend creates a Stripe **PaymentIntent** ("please charge 20.000 JOD") and gives the app its `clientSecret`.
2. The app opens **Stripe's** payment screen with it. The card number goes from the phone **straight to Stripe**, never to our server.
3. The app calls `/payment/confirm`. The backend never trusts the app: it asks Stripe directly (`GET /v1/payment_intents/{id}`) and checks the amount.
4. `PAID` → confirmation email to the member (receipt) and the trainer. The emails are sent only **after** the database save (`@TransactionalEventListener`).

**Safety rules in the code:**
- Only Stripe **test** keys are accepted; the backend refuses to start with a live key.
- Money code locks the booking row first, so "confirm", "cancel" and the webhook can't run on the same booking at the same moment.
- One payment per booking (`booking_id` is unique), and requests to Stripe carry an **Idempotency-Key**, so a retry never charges or refunds twice.
- Paid after the deadline (the payment screen was still open)? The backend refunds it automatically (`PAID_TOO_LATE`).
- If Stripe refuses a refund, the cancel is undone too: the booking stays `PAID`.

Payment error codes: `NOT_ACCEPTED_YET`, `ALREADY_PAID`, `BOOKING_EXPIRED`, `PAYMENT_NOT_STARTED`, `PAYMENT_NOT_COMPLETED`, `PAYMENT_PROCESSING`, `PAID_TOO_LATE`, `PAYMENT_MISMATCH`, `PAYMENT_PROVIDER_ERROR` (502), `PAYMENTS_NOT_CONFIGURED` (503), `INVALID_SIGNATURE`.

### Try it without the app (Terminal)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@gym.com","password":"Admin1234"}'
```

Copy the `token` from the answer, then:

```bash
curl http://localhost:8080/api/users/me -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

Branches CRUD (with the **admin** token):

```bash
TOKEN=PASTE_ADMIN_TOKEN_HERE

# Read all
curl http://localhost:8080/api/branches -H "Authorization: Bearer $TOKEN"

# Create
curl -X POST http://localhost:8080/api/branches \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Jubaiha Branch","address":"University Street","city":"Amman","latitude":32.0167,"longitude":35.8669,"openingTime":"06:30","closingTime":"22:00"}'

# Update branch 4
curl -X PUT http://localhost:8080/api/branches/4 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Jubaiha Branch","address":"University Street","city":"Amman","latitude":32.0167,"longitude":35.8669,"openingTime":"05:30","closingTime":"23:00"}'

# Delete branch 4
curl -X DELETE http://localhost:8080/api/branches/4 -H "Authorization: Bearer $TOKEN"
```

---

## 7. Sending real emails (optional)

1. On your Google account, turn on 2-Step Verification. Then create an **App password** (Google Account → Security → App passwords).
2. In `local.properties` (not `application.properties`, so the password stays off GitHub):
   - Add `app.notifications.mode=email`.
   - Add the `spring.mail.*` lines from `local.properties.example` with your provider's details.
3. Restart the backend.

**Don't commit the app password to GitHub.**

---

## 8. Stripe test mode (payments)

Test mode = a Stripe sandbox. Everything works like real payments, but no real money moves and only **test cards** work.

1. **Account:** sign up at stripe.com (free). You only use the sandbox, so you don't need to activate the account or add business details. If Jordan isn't in the country list, pick any country; the sandbox never moves money.
2. **Keys:** Dashboard → make sure it says **Test mode / Sandbox** → *Developers* → *API keys*. Put both in `local.properties` yourself (never in chat, `application.properties` or GitHub):
   ```properties
   app.payments.stripe.secret-key=sk_test_...
   app.payments.stripe.publishable-key=pk_test_...
   ```
3. Restart the backend. Without the keys it still starts, but paying answers "Payments aren't set up yet".
4. **Test cards** (any future date, any CVC, any postcode):

   | Card number | Result |
   |---|---|
   | `4242 4242 4242 4242` | Paid (Visa) |
   | `5555 5555 5555 4444` | Paid (Mastercard) |
   | `4000 0000 0000 0002` | Declined |
   | `4000 0025 0000 3155` | Asks for 3-D Secure (tap "Complete") |

5. **See it in Stripe:** Dashboard → *Payments*. Each payment shows our booking number in its description and metadata. Refunds appear there too.

**Optional: the webhook** (Stripe tells the backend itself, even if the app closes right after paying):
1. Install the Stripe CLI: `brew install stripe/stripe-cli/stripe`, then `stripe login`.
2. Keep this running in a Terminal tab while you test:
   ```bash
   stripe listen --events payment_intent.succeeded --forward-to localhost:8080/api/payments/stripe/webhook
   ```
3. It prints `Your webhook signing secret is whsec_...`. Put it in `local.properties` as `app.payments.stripe.webhook-secret=whsec_...` and restart the backend.

**Currency:** prices are in JOD, which has 3 decimals (1 JOD = 1000 fils). Stripe wants whole numbers in the smallest unit, so 20.000 JOD is sent as `20000`. Stripe only accepts dinar amounts in steps of 0.010 (the last digit must be 0); our prices (whole JOD/h × 30/45/60/90 min) always are.

---

## 9. Coming next

| Stage | Adds |
|---|---|
| 2 | ✅ Done: MySQL, branches CRUD, trainers ↔ branches, working hours, availability, map screen |
| 3 | ✅ Done: booking requests, trainer accepts/declines, 24 h expiry, prices, My bookings, trainer Requests/Schedule |
| 4 | ✅ Done: Stripe test-mode payments (`PaymentGateway` interface), 12 h pay deadline, refunds, confirmation email |
| 5 | Admin endpoints: manage branches, trainers, and slots; cancel bookings. **2FA**: required for admins, optional for members (authenticator app codes) |
| 6 | Google sign-in |
