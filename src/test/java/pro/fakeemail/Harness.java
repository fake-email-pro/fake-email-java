package pro.fakeemail;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** A client wired to a {@link FakeHttpClient} and a {@link FakeClock}, like the JS suite's {@code setup()}. */
final class Harness {
    final FakeClock clock = new FakeClock();
    final FakeHttpClient http;
    final FakeEmail client;

    private Harness(Consumer<FakeEmail.Builder> customize, List<FakeHttpClient.Reply> replies) {
        this.http = new FakeHttpClient(clock, replies);
        FakeEmail.Builder builder = FakeEmail.builder()
                .apiKey("fep_test")
                .baseUrl("https://api.test")
                .httpClient(http)
                .env(name -> null)
                .sleeper(clock::sleep)
                .clock(clock::now)
                .random(() -> 0);
        customize.accept(builder);
        this.client = builder.build();
    }

    static Harness of(FakeHttpClient.Reply... replies) {
        return new Harness(b -> {}, Arrays.asList(replies));
    }

    static Harness with(Consumer<FakeEmail.Builder> customize, FakeHttpClient.Reply... replies) {
        return new Harness(customize, Arrays.asList(replies));
    }

    List<FakeHttpClient.Recorded> requests() {
        return http.requests;
    }

    FakeHttpClient.Recorded request(int i) {
        return http.requests.get(i);
    }

    List<Long> sleeps() {
        return clock.sleeps;
    }
}
