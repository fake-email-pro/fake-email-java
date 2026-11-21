package pro.fakeemail;

import pro.fakeemail.models.Limits;

/** {@code /v1/limits}. Get it with {@link FakeEmail#limits()}. */
public final class LimitsResource {
    private final HttpCore http;
    private final RequestOptions options;

    LimitsResource(HttpCore http, RequestOptions options) {
        this.http = http;
        this.options = options;
    }

    /** Your quota, rate limit, concurrent waits, storage and mailbox lifetime. Does not spend quota. */
    public Limits get() {
        return Decode.limits(http.json(new HttpCore.Call("GET", "/v1/limits").options(options)));
    }
}
