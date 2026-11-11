# Secure Hello — Spring Boot 3.2.0 + Spring Security

A small, runnable backend for the interview task: a protected `GET /api/hello` endpoint that
returns exactly `Hello World`, plus a JSON username/password login endpoint that issues a
session credential.

The exercise asks for Spring Boot 3.2.0, a protected HelloWorld endpoint, and a username/password login; the implementation choices are documented below.

## Stack

| Component | Version |
| --- | --- |
| Spring Boot | **3.2.0** (pinned in the parent POM) |
| Spring Security | 6.2.0 (managed by Boot 3.2.0) |
| Java | 17 |
| Build | Maven 3.9 |
| Tests | JUnit 5 + MockMvc + TestRestTemplate |

## API

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| `GET` | `/api/hello` | required | `200` with `Hello World` (`text/plain`), or `401` JSON |
| `POST` | `/api/login` | public | `200` JSON + `Set-Cookie: JSESSIONID=...`, or `401` JSON |
| `GET` | `/error` | public | framework error passthrough |

Demo account (seeded at startup from configuration):

| Username | Password |
| --- | --- |
| `test` | `123456` |

`application.yml` stores only a **BCrypt hash** of the password (cost 10, `$2a$10$...`).
The demo password `123456` appears only in this README and in the tests, where it is needed to
exercise the real login endpoint; it is never stored in configuration, and the application only
calls `passwordEncoder.matches(rawPassword, hash)` — there is no plaintext comparison.

## Authentication: session cookie (design choice)

Login flow:

1. `POST /api/login` receives `{"username":"test","password":"123456"}`.
2. `AuthController` delegates to an `AuthenticationManager` backed by a
   `DaoAuthenticationProvider` and `BCryptPasswordEncoder`; the submitted password is
   compared against the stored hash only.
3. On success the authenticated `SecurityContext` is persisted with
   `HttpSessionSecurityContextRepository`, and Tomcat returns
   `JSESSIONID` (HttpOnly, SameSite=Lax).
4. Later requests send the cookie; `SecurityContextHolderFilter` restores the context from
   the session, and the authorization rule lets `/api/hello` through.

### Why a session instead of JWT

* Single same-origin consumer (curl/browser), so there is no need for portable tokens.
* Server-side sessions can be revoked centrally, so JWT's denylist / short-lived-token
  machinery is unnecessary. (This demo has no logout/revoke endpoint yet — see Limitations.)
* `HttpOnly` cookies cannot be read by JavaScript, unlike tokens kept in web storage.
* It needs no signing-key management and is supported by Spring Security out of the box,
  which keeps the security code small and auditable.

Trade-offs: horizontal scaling requires sticky sessions or a shared session store, and
cookie-based auth makes CSRF relevant. JWT would be the better fit for stateless
multi-service or cross-domain clients. This project documents the trade-off instead of
hiding it.

## Security decisions

* **401 instead of 302**: form login and HTTP Basic are disabled. A custom
  `AuthenticationEntryPoint` returns a JSON `401` for unauthenticated calls, which is the
  right contract for an API.
* **BCrypt everywhere**: `BCryptPasswordEncoder` is the only `PasswordEncoder`; no string
  equality or plaintext fallback exists.
* **CSRF is disabled deliberately**: this is a JSON-only, same-origin API with no HTML
  forms or browser UI, and the only state-changing endpoint today is `/api/login`, which does
  not act on an existing session. `SameSite=Lax` + `HttpOnly` **reduce but do not eliminate**
  cross-site POST risk (same-site subdomains remain in scope). This is a scoped trade-off, not
  "CSRF solved": any future cookie-authenticated write endpoint must add CSRF tokens
  (`CookieCsrfTokenRepository` plus a request header).
* **Session fixation**: `ChangeSessionIdAuthenticationStrategy` rotates the session id on
  login when a session already exists — asserted end-to-end by
  `SessionCookieTest.loginRotatesAnExistingSessionId`.
* **No user enumeration**: an unknown username and a wrong password produce the same
  `401` body and message — asserted byte-for-byte by
  `AuthFlowTest.wrongPasswordAndUnknownUserReturnTheSameBody`.
* **Same-origin only**: CORS is left disabled.
* **Session lifetime**: 30 minutes (configuration only — no test exercises expiry). The
  cookie is `HttpOnly` and `SameSite=Lax` (asserted by `SessionCookieTest`). This demo runs
  local HTTP, so `Secure` is off; a production HTTPS deployment should set
  `server.servlet.session.cookie.secure=true`.
* **Validation**: blank credentials are rejected with `400` by Jakarta Bean Validation before
  authentication. In a real Tomcat run the body is Boot's default error JSON:
  `{"timestamp":...,"status":400,"error":"Bad Request","path":"/api/login"}`.
* **Failed re-login keeps the existing session**: a request that already carries a valid
  session gets `401` on a wrong password but the session is not invalidated (verified by
  curl). A typo during re-authentication therefore does not log the user out.

## Project layout

```
pom.xml
src/main/java/com/example/securehello/
  SecureHelloApplication.java
  config/AppUserProperties.java     # binds the demo account from YAML
  config/SecurityConfig.java        # SecurityFilterChain, BCrypt, AuthenticationManager
  web/AuthController.java           # POST /api/login
  web/HelloController.java          # GET /api/hello
  web/dto/LoginRequest.java
  web/dto/LoginResponse.java
  web/dto/ErrorResponse.java
src/main/resources/application.yml
src/test/java/com/example/securehello/
  AuthFlowTest.java                 # MockMvc: 401 body, login, authenticated 200, 400/401 cases
  SessionCookieTest.java            # real HTTP: Set-Cookie attributes + session-id rotation
  PasswordStorageTest.java          # BCrypt storage guard (cost 10)
  LoginRequestTest.java             # password redaction in toString()
```

## Build and run

Prerequisites: Java 17 and Maven 3.9 (or newer).

```bash
mvn test            # compile and run the test suite
mvn spring-boot:run # start on http://localhost:8080
```

Packaged alternative:

```bash
mvn clean package
java -jar target/secure-hello-1.0.0.jar
```

The application listens on port `8080` (override with `--server.port=...`).

## Verify with curl (three commands)

### 1. Unauthenticated call is rejected with 401

```bash
curl -i http://localhost:8080/api/hello
```

Observed (the field order is fixed by the `ErrorResponse` record and pinned by a test):

```http
HTTP/1.1 401
Content-Type: application/json;charset=UTF-8
Content-Length: 97

{"status":401,"error":"Unauthorized","message":"Authentication required. POST /api/login first."}
```

### 2. Log in with test / 123456 and keep the session cookie

```bash
curl -i -c cookie.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"123456"}'
```

Observed:

```http
HTTP/1.1 200
Set-Cookie: JSESSIONID=<32 hex chars, new on every login>; Path=/; HttpOnly; SameSite=Lax
Content-Type: application/json

{"username":"test","authenticated":true,"message":"Login successful"}
```

### 3. Authenticated call returns Hello World

```bash
curl -i -b cookie.txt http://localhost:8080/api/hello
```

Observed:

```http
HTTP/1.1 200
Content-Type: text/plain;charset=UTF-8
Content-Length: 11

Hello World
```

### Optional negative check

```bash
curl -i -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"wrong"}'
```

Returns `401` with `{"status":401,"error":"Unauthorized","message":"Invalid username or password."}` —
byte-identical for an unknown username (no user enumeration; asserted by a test).

### Optional empty-credentials check

```bash
curl -i -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"","password":""}'
```

Observed: `400` with Boot's default error body (the timestamp changes on every run):

```json
{"timestamp":"2026-11-11T06:37:10.376+00:00","status":400,"error":"Bad Request","path":"/api/login"}
```

## Tests

`mvn test` currently reports **12 tests, 0 failures, 0 errors** (8 MockMvc flow tests, 2
real-HTTP cookie tests, 1 password-storage guard, 1 DTO redaction test). Each row below maps to
one test method:

| Test | What it asserts |
| --- | --- |
| `AuthFlowTest.unauthenticatedHelloReturns401WithFixedJsonOrder` | the protected endpoint returns 401 with the exact JSON body `{"status":401,"error":"Unauthorized","message":"..."}`; field order is pinned |
| `AuthFlowTest.loginWithDemoCredentialsSucceedsAndCreatesSession` | valid credentials return 200 and create an HTTP session |
| `AuthFlowTest.authenticatedHelloReturnsHelloWorld` | the session credential works and the body is exactly `Hello World` |
| `AuthFlowTest.loginWithWrongPasswordReturns401` | a bad password returns 401 with the generic message, not a 500 |
| `AuthFlowTest.loginWithUnknownUserReturns401` | an unknown user also returns 401 |
| `AuthFlowTest.wrongPasswordAndUnknownUserReturnTheSameBody` | the two 401 bodies are byte-identical (no user enumeration) |
| `AuthFlowTest.failedReLoginDoesNotInvalidateAnExistingSession` | a wrong-password re-login returns 401 but the existing session still reaches `/api/hello` (the documented trade-off is pinned) |
| `AuthFlowTest.blankCredentialsReturn400` | blank username/password are rejected with 400 before authentication |
| `SessionCookieTest.loginCookieIsHttpOnlyAndSameSiteLax` | the real `Set-Cookie` header contains `HttpOnly` and `SameSite=Lax` |
| `SessionCookieTest.loginRotatesAnExistingSessionId` | logging in with an existing `JSESSIONID` returns a different id (session-fixation protection) |
| `PasswordStorageTest.demoPasswordIsStoredAsBcryptHashWithCost10` | the configured value matches `^\$2a\$10\$...`, is not the plaintext, and verifies `123456` |
| `LoginRequestTest.toStringDoesNotLeakThePassword` | `LoginRequest.toString()` redacts the password |

The MockMvc tests log in through the real `/api/login` endpoint and reuse the resulting
`MockHttpSession`; `@WithMockUser` is intentionally not used, because it would bypass the
login and credential chain being tested. `SessionCookieTest` starts embedded Tomcat (random
port) because cookie attributes and session-id rotation are container behaviour that MockMvc
cannot prove.

**Not covered by tests** (and therefore not claimed as proven here): session expiry after the
configured 30 minutes, logout/revocation, rate limiting or account lockout, the CSRF token flow,
CORS, and framework-level log redaction (only the DTO `toString()` is tested).

## Limitations / next steps

* The demo user lives in memory and is seeded from configuration. Production would use a
  database-backed `UserDetailsService` with the same BCrypt encoder.
* No login rate limiting, account lockout, or audit logging.
* No logout/revocation endpoint (out of scope for this task). Combined with "failed re-login
  keeps the existing session", a session lives until the 30-minute timeout or a server restart.
* The 30-minute session timeout is configuration; no test waits for expiry.
* The demo password is intentionally present in this README and the tests; a real deployment
  must not ship credentials in documentation or source.
* CSRF protection and CORS policies must be revisited before exposing this to browsers.
