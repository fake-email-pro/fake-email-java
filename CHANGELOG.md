# Changelog

All notable changes to this package are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the package uses [Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-11-22

### Changed

- `waitForMessage`, `waitForCode` and `waitForLink` wait out `429`, `502`, `503`, `504`, `520`–`524`, connection
  errors and timeouts until their deadline and then throw `WaitTimeoutError` (the last transient error as its
  cause): the wait request goes out without transport retries, each attempt asks the server for the time left
  (1..60 s), and no request or pause ends past the deadline, the extra `get` of `waitForCode` / `waitForLink`
  included. An attempt that hits its HTTP timeout ends as `WaitTimeoutError`, not `APITimeoutError`. A timeout of
  zero or less is already expired.
- A `wait` request's HTTP timeout is its server timeout + 10 s; a `RequestOptions` timeout may only lengthen it.
- A `503` with `Retry-After` over 5 s (e.g. no domain to create a mailbox on, `Retry-After: 60`) is thrown at once
  instead of stalling `mailboxes().create()` for up to two minutes.
- `GET` and `DELETE` are also retried on Cloudflare `520`–`524`, like `502` and `504`.

### Fixed

- A `DELETE` retried after a lost response (timeout, connection error, `502`/`504`/`52x`) that gets `404` is a
  success, not a `NotFoundError`.
- When a message the wait helpers skipped is deleted meanwhile (`404 message in after not found`), they go on after
  its time instead of failing.

### Documentation

- `DownloadedAttachment.fileName()` is never null from the API (the attachment id when the email gave no name) and
  is sender-controlled: the README keeps only its last path segment. `NotFoundError` also covers an unknown `domain`
  on create and a missing `after` message; the server lowercases a custom local part. The no-arg
  `messages().wait()` is `Object#wait()`.
- CI builds the release profile and javadoc on JDK 17, as JitPack does.

## [0.1.0] - 2026-11-22

### Added

- `FakeEmail` client for the fake-email.pro Public API v1: `mailboxes()` (create, list, listAll, get, delete),
  `messages()` (list, listAll, wait, get, delete, raw, attachment), `domains().list()`, `limits().get()`.
- Test helpers `waitForMessage`, `waitForCode` and `waitForLink` with a client-side deadline, server-side `from` /
  `subject` filters and a `match` predicate; interrupting the thread cancels them.
- Typed unchecked errors per status (`QuotaExhaustedError`, `RateLimitError`, ...) with `X-RateLimit-*` state.
- Retries on `429`/`503` honoring `Retry-After`, and on `502`/`504`/network errors for `GET` and `DELETE`.
- Immutable record models; Java 17+, `java.net.http.HttpClient`, zero runtime dependencies.
- Published through JitPack as `pro.fake-email:fake-email-java`.

[0.2.0]: https://github.com/fake-email-pro/fake-email-java/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/fake-email-pro/fake-email-java/releases/tag/v0.1.0
