# fake-email-pro for Java

[![Maven Central](https://img.shields.io/maven-central/v/pro.fake-email/fake-email-pro.svg)](https://central.sonatype.com/artifact/pro.fake-email/fake-email-pro)
[![CI](https://github.com/fake-email-pro/fake-email-java/actions/workflows/ci.yml/badge.svg)](https://github.com/fake-email-pro/fake-email-java/actions/workflows/ci.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

The official Java client for the [fake-email.pro API](https://fake-email.pro/api): create a disposable mailbox,
wait for the email, get the one-time code or the magic link. Built for end-to-end tests of signup, OTP and
password-reset flows with JUnit 5, Playwright and Selenium.

```java
String address = mail.mailboxes().create().address();
// ...sign up with the address in your app...
String code = mail.waitForCode(address); // "048213"
```

- Zero dependencies: `java.net.http.HttpClient` and the JDK only, so nothing clashes with your test classpath
- Java 17+, immutable record models, unchecked exceptions
- Long-poll waiting: the message is returned within about a second of delivery, no polling loops in your tests
- Codes and confirm links are extracted on the server
- Retries on rate limits and transient errors, honoring `Retry-After`

API reference: [api.fake-email.pro/docs](https://api.fake-email.pro/docs) · Guides:
[fake-email.pro/api/docs/sdks](https://fake-email.pro/api/docs/sdks)

## Install

Maven:

```xml
<dependency>
  <groupId>pro.fake-email</groupId>
  <artifactId>fake-email-pro</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Gradle:

```kotlin
testImplementation("pro.fake-email:fake-email-pro:0.1.0")
```

The Java package is `pro.fakeemail` (models in `pro.fakeemail.models`); the JPMS module name is `pro.fakeemail`.

## Get an API key

1. Sign in at [fake-email.pro](https://fake-email.pro). Any account can use the API, the free plan included.
2. Open **Settings → API keys** ([fake-email.pro/settings/api-keys](https://fake-email.pro/settings/api-keys)) and
   create a key. It starts with `fep_` and is shown only once.
3. Put it in the environment of your test runner (and in your CI secrets):

```sh
export FAKE_EMAIL_API_KEY=fep_...
```

The client reads `FAKE_EMAIL_API_KEY` by default. A bigger monthly quota and higher limits come with
[PRO](https://fake-email.pro/pricing).

> **Server-side only.** The key belongs in your test code and CI, never in a web page or a mobile app: anyone
> could read it there.

## Quickstart

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import pro.fakeemail.FakeEmail;
import pro.fakeemail.WaitOptions;

FakeEmail mail = new FakeEmail(); // or FakeEmail.builder().apiKey("fep_...").build()

// 1. A fresh address, valid for 24 hours
String address = mail.mailboxes().create().address();

// 2. Make your app send an email to it
HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("https://your-app.example/api/signup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + address + "\"}"))
                .build(),
        HttpResponse.BodyHandlers.discarding());

// 3. Wait for the email and take the code
String code = mail.waitForCode(address, WaitOptions.builder().timeout(Duration.ofSeconds(60)).build());

// Or the confirm / magic link
// String link = mail.waitForLink(address);

mail.mailboxes().delete(address);
```

`waitForCode` returns the code found in the email when it was delivered. If none was found then, it reads the
full message and takes its first code; an email with no code at all is skipped and the wait goes on.
`waitForLink` does the same with the confirm link.

A `FakeEmail` is thread-safe: create one per test class (or per suite) and share it. It implements
`AutoCloseable`; closing it releases the `HttpClient` it created (Java 21+).

## JUnit 5 and Playwright

```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;
import pro.fakeemail.FakeEmail;
import pro.fakeemail.WaitOptions;

class SignupTest {
    static final FakeEmail mail = new FakeEmail();
    static Playwright playwright;
    static Browser browser;

    Page page;
    String inbox;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void shutdown() {
        playwright.close();
        mail.close();
    }

    // A fresh mailbox per test, deleted afterwards
    @BeforeEach
    void setUp() {
        page = browser.newPage();
        inbox = mail.mailboxes().create().address();
    }

    @AfterEach
    void tearDown() {
        mail.mailboxes().delete(inbox);
        page.close();
    }

    @Test
    void signsUpWithAnEmailCode() {
        page.navigate("https://your-app.example/signup");
        page.getByLabel("Email").fill(inbox);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send code")).click();

        String code = mail.waitForCode(inbox, WaitOptions.builder().subject("verification").build());

        page.getByLabel("Code").fill(code);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continue")).click();
        assertThat(page.getByText("Welcome")).isVisible();
    }
}
```

The same works with Selenium or with plain API tests: the SDK only needs the address your app sends mail to. If
your test methods have a timeout (`@Timeout`), keep it above the wait's own (60 s by default).

## Waiting for messages

```java
SimpleMessage message = mail.waitForMessage(address, WaitOptions.builder()
        .timeout(Duration.ofSeconds(60)) // client-side deadline, then WaitTimeoutError
        .from("acme.example")            // case-insensitive substring of the sender address
        .subject("verify")               // case-insensitive substring of the subject
        .match(m -> m.attachmentsCount() > 0) // any extra check
        .build());
message.code();        // "048213" or null
message.confirmLink(); // "https://..." or null
```

`from` and `subject` are filtered on the server. A message that fails `match` is skipped and the wait continues
with the next one. Under the hood the client repeats the server's long poll (up to 60 s each) until the deadline.

### The next message

Without `after`, the wait returns the **earliest** message in the mailbox, even one that arrived before you
started waiting, so nothing is lost. To wait for a newer one, for example after "Resend code", pass the previous
message's id:

```java
SimpleMessage first = mail.waitForMessage(address);
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Resend code")).click();
SimpleMessage second = mail.waitForMessage(address, WaitOptions.builder().after(first.id()).build());
```

`after` also takes an `Instant` (or an RFC 3339 string): "messages that arrived after this moment" by the server's
clock. Prefer a message id: it is exact, while a time depends on clock skew. Do not pass a message's `createdAt`
as `after`: a second message in the same millisecond would be skipped.

An `after` id that is no longer in the mailbox (you deleted that message) is a `NotFoundError`: if your tests
delete processed messages, wait without `after`.

### Cancelling

Every call blocks its thread. Interrupting the thread (for example `Future.cancel(true)`, or JUnit's
`@Timeout` with its default thread mode) stops the call: it throws `java.util.concurrent.CancellationException`
with the `InterruptedException` as its cause, and the thread's interrupt flag stays set.

## Messages, attachments and raw EML

```java
Page<SimpleMessage> page = mail.messages().list(address, PageParams.of(0, 20)); // newest first
for (SimpleMessage m : mail.messages().listAll(address)) System.out.println(m.subject());

DetailedMessage full = mail.messages().get(address, message.id());
full.html(); full.text(); full.codes(); full.links(); full.attachments();

DownloadedAttachment file = mail.messages().attachment(address, message.id(), full.attachments().get(0).id());
Files.write(Path.of(file.fileName() != null ? file.fileName() : "attachment.bin"), file.data());

byte[] eml = mail.messages().raw(address, message.id()); // the message as received

mail.messages().delete(address, message.id());
```

`listAll` fetches pages lazily as you iterate; `.stream()` gives a `Stream`. One long poll without the
client-side loop: `mail.messages().wait(address, WaitParams.builder().timeout(Duration.ofSeconds(30)).build())`
returns an `Optional<SimpleMessage>`, empty when the server timeout passed.

`Page` here is `pro.fakeemail.models.Page`; in a Playwright test, import one of the two by its full name.

## Mailboxes, domains and limits

```java
mail.mailboxes().create(); // random address on a random domain
mail.mailboxes().create(CreateMailboxParams.builder().domain("example.org").build()); // random address on that domain
mail.mailboxes().create(CreateMailboxParams.builder().localPart("signup-test").domain("example.org").build()); // exactly that address

mail.mailboxes().get(address);
mail.mailboxes().list(PageParams.of(0, 50));
mail.mailboxes().listAll().stream().map(Mailbox::address).forEach(System.out::println);
mail.mailboxes().delete(address); // messages are erased; the address is never issued again

List<Domain> domains = mail.domains().list(); // [Domain[name=example.org, type=PUBLIC], ...]
Limits limits = mail.limits().get(); // quota, requestsPerMinute, concurrentWaits, storage, mailboxTtlSeconds
```

A mailbox accepts mail for 24 hours and cannot be renewed; after that it answers `404`. Each created mailbox
spends one unit of your monthly quota (deleting it does not give it back). Custom local parts depend on the plan.

Models are immutable records named after the API's schemas: `Mailbox`, `SimpleMessage`, `DetailedMessage`,
`Attachment`, `Link`, `Sender`, `Domain`, `Limits`, `Quota`, `Storage`, `Page`. Times are `Instant`s. `BodyState`
and `DomainType` keep values a newer server may add (`isKnown()` is `false` for them) instead of failing.

## Errors

All errors are unchecked and extend `FakeEmailError`. Errors from the server are `APIError` with `status()`,
`code()` (the API's `error` field, see `ErrorCode`), `getMessage()`, `body()`, `headers()` and `rateLimit()`:

| Class                   | When                                                                         |
| ----------------------- | ---------------------------------------------------------------------------- |
| `BadRequestError`       | 400 `bad_request`: an invalid parameter or body                              |
| `AuthenticationError`   | 401 `unauthorized`: missing, unknown, revoked or expired key                 |
| `QuotaExhaustedError`   | 402 `quota_exhausted`: monthly mailbox quota spent (`limit`, `used`, `resetsAt`, `upgradeUrl`) |
| `PermissionDeniedError` | 403 `plan_forbidden`: the plan does not allow this                           |
| `NotFoundError`         | 404 `not_found`: mailbox expired, deleted or not yours; no such message     |
| `ConflictError`         | 409 `address_taken`: the address was issued before                           |
| `RateLimitError`        | 429: `code()` is `rate_limit_exceeded` or `too_many_waits`; `retryAfter()`   |
| `InternalServerError`   | 5xx                                                                          |
| `APIConnectionError`    | no response (DNS, refused, reset)                                            |
| `APITimeoutError`       | an HTTP attempt took longer than the timeout                                 |
| `WaitTimeoutError`      | `waitForMessage` / `waitForCode` / `waitForLink` reached their deadline      |

```java
try {
    String address = mail.mailboxes().create().address();
} catch (QuotaExhaustedError e) {
    System.err.printf("Quota %d/%d, renews %s. More: %s%n", e.used(), e.limit(), e.resetsAt(), e.upgradeUrl());
    throw e;
}
```

### Retries and rate limits

The client retries up to `maxRetries` times (default 2):

- `429` and `503`, for every method: the server refuses these before doing anything. The wait is `Retry-After`
  (at most 60 s).
- `502`, `504`, timeouts and network errors, for `GET` and `DELETE` only, never for `POST` (a mailbox could be
  created twice). Exponential backoff with jitter: 0.5 s, 1 s, 2 s, ... up to 8 s.

Inside `waitForMessage` and friends, a `429` keeps being waited out while the deadline allows, and no retry is
started that would end past the deadline.

Your plan's numbers are in `mail.limits().get()`. Every `APIError` carries the window state from the
`X-RateLimit-*` headers as `rateLimit()` (`RateLimitInfo(limit, remaining, reset)`, or `null` when the server did
not send them). Each `wait` request counts once, however long it is held open.

## Configuration

```java
FakeEmail mail = FakeEmail.builder()
        .apiKey("fep_...")                       // default: the FAKE_EMAIL_API_KEY environment variable
        .baseUrl("https://api.fake-email.pro")   // default
        .timeout(Duration.ofSeconds(30))         // per HTTP attempt; a wait request gets its server timeout + 10 s
        .maxRetries(2)
        .httpClient(HttpClient.newBuilder().proxy(ProxySelector.of(proxy)).build()) // default: a new HttpClient
        .userAgent("my-e2e-suite/1.0")           // appended to "fake-email-pro-java/<version>"
        .build();
```

Per-call overrides: `mail.withOptions(RequestOptions.builder().timeout(Duration.ofSeconds(5)).maxRetries(0).build())`
returns a view of the client that shares its connections; unlike the client's timeout, its timeout also applies to
`wait` requests.

## Links

- Product: [fake-email.pro/api](https://fake-email.pro/api)
- API reference: [api.fake-email.pro/docs](https://api.fake-email.pro/docs) (OpenAPI:
  [public.yaml](https://api.fake-email.pro/docs/public.yaml))
- SDK docs: [fake-email.pro/api/docs/sdks](https://fake-email.pro/api/docs/sdks)
- Issues: [github.com/fake-email-pro/fake-email-java/issues](https://github.com/fake-email-pro/fake-email-java/issues)
- Changelog: [CHANGELOG.md](CHANGELOG.md)

## Development

```sh
./mvnw verify               # build and test (Java 17+)
./mvnw spotless:apply       # format (palantir-java-format)
FAKE_EMAIL_LIVE=1 FAKE_EMAIL_API_KEY=fep_... ./mvnw test -Dtest=LiveTest  # smoke test against the real API
```

The live test creates and deletes one mailbox (one unit of the monthly quota); `FAKE_EMAIL_BASE_URL` points it at
another environment.

## License

[MIT](LICENSE)
