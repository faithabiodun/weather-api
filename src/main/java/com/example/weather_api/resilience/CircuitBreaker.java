package com.example.weather_api.resilience;

import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.ProviderUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    /** The three states, and the whole idea, in three words. */
    public enum State {
        CLOSED,         // normal. calls go through
        OPEN,           // they are down. fail instantly, do not call at all
        HALF_OPEN       // maybe they are back. let ONE through and find out
    }

    // Atomic types, because several web requests run at the same time on
    // different threads and all of them share this one object.
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();

    private final WeatherProperties properties;

    public CircuitBreaker(WeatherProperties properties) {
        this.properties = properties;
    }

    public State state() {                  // used by the metrics in step 12
        return currentState();
    }

    /** Runs the work, unless the breaker says there is no point. */
    public <T> T execute(Supplier<T> work) {
        if (currentState() == State.OPEN) {
            // The saving is HERE: no connection, no timeout, no waiting.
            // Instant failure, so the caller can fall back straight away.
            throw new ProviderUnavailableException("Circuit is open: the weather provider is down");
        }
        try {
            T result = work.get();
            recordSuccess();                // back to normal, counter cleared
            return result;
        } catch (RuntimeException e) {
            // ONLY a genuine "they are down" counts. A 404 is a normal answer
            // and must never be able to open the circuit.
            if (e instanceof ProviderUnavailableException) {
                recordFailure();
            }
            throw e;
        }
    }

    /**
     * Works out the state now, moving OPEN to HALF_OPEN once enough time
     * has passed. Doing it here means no background timer is needed.
     */
    private State currentState() {
        if (state.get() != State.OPEN) {
            return state.get();
        }
        Duration openFor = Duration.between(openedAt.get(), Instant.now());
        if (openFor.compareTo(properties.breaker().openDuration()) > 0) {
            // The open interval has passed. Let the next request try.
            state.set(State.HALF_OPEN);
            log.info("Circuit is half-open: trying one request");
        }
        return state.get();
    }

    private void recordSuccess() {
        // Any success clears the count. Five failures spread over an hour is
        // not an outage; five IN A ROW is, and that is what we are counting.
        consecutiveFailures.set(0);
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            log.info("Circuit closed: the weather provider is answering again");
        }
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        // A failure while we were testing the water sends us straight back
        // to open, for another full interval.
        if (state.get() == State.HALF_OPEN || failures >= properties.breaker().failureThreshold()) {
            state.set(State.OPEN);
            openedAt.set(Instant.now());
            log.warn("Circuit OPEN after {} consecutive failures. Not calling the provider for {}",
                    failures, properties.breaker().openDuration());
        }
    }
}
