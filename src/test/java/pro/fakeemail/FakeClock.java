package pro.fakeemail;

import java.util.ArrayList;
import java.util.List;

/** Virtual clock: sleeping advances it instantly, so retry and wait tests take no real time. */
final class FakeClock {
    long t = 1_800_000_000_000L;
    final List<Long> sleeps = new ArrayList<>();

    long now() {
        return t;
    }

    void advance(long ms) {
        t += ms;
    }

    void sleep(long ms) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        sleeps.add(ms);
        t += ms;
    }
}
