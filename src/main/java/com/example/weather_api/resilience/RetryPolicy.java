package com.example.weather_api.resilience;

import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.ProviderUnavailableException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final WeatherProperties properties;

    public RetryPolicy(WeatherProperties properties) {
        this.properties = properties;
    }

    /**
     * THE decision of this whole step, in one method.
     *
     * Only "they are down or too slow" is worth asking again about. A missing
     * city will still be missing. A rejected key will still be rejected. Being
     * rate limited is made WORSE by asking again.
     */
    public boolean isWorthRetrying(Throwable failure) {
        return failure instanceof ProviderUnavailableException;
    }

    /**
     * Runs the work, and asks again if it failed in a way worth asking about.
     *
     * Supplier<T> just means "a piece of code that returns a T when called",
     * so the caller passes the work itself rather than the result.
     */
    public <T> T execute(Supplier<T> work) {
        int maxAttempts = properties.retry().attempts();        // 3
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return work.get();
            } catch (RuntimeException e) {
                // Not worth retrying? Then stop immediately and rethrow.
                // A 404 must cost exactly one call, not three.
                if (!isWorthRetrying(e)) {
                    throw e;
                }
                lastFailure = e;

                // Was that the last attempt? Then there is nothing left to do.
                if (attempt == maxAttempts) {
                    break;
                }

                Duration wait = backoffFor(attempt);
                log.warn("Attempt {} of {} failed ({}). Waiting {} ms",
                        attempt, maxAttempts, e.getClass().getSimpleName(), wait.toMillis());
                sleep(wait);
            }
        }

        // Every attempt failed. Throw the last failure, so the caller sees the
        // real reason rather than an invented "retries exhausted" message.
        throw lastFailure;
    }

    /**
     * 200ms, then 400ms, then 800ms - each doubled - PLUS a random slice.
     *
     * The doubling gives a struggling server a bit more room each time.
     * The random part stops a hundred clients that failed together from
     * retrying together, which would just be the same spike again.
     */
    private Duration backoffFor(int attempt) {
        long base = properties.retry().baseDelay().toMillis();          // 200
        // 1 << 0 is 1, 1 << 1 is 2, 1 << 2 is 4 ... a cheap way to double.
        long doubled = base * (1L << (attempt - 1));
        // Up to half the delay again, chosen fresh every single time.
        long jitter = ThreadLocalRandom.current().nextLong(0, doubled / 2 + 1);
        return Duration.ofMillis(doubled + jitter);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            // Somebody is shutting us down. Restore the flag so the code above
            // knows, and stop retrying immediately.
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Interrupted while retrying", e);
        }
    }
}
