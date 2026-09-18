package com.example.weather_api.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.CityNotFoundException;
import com.example.weather_api.error.ProviderUnavailableException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    /** Threshold 3 and a 100ms open window, so the test runs in a blink. */
    private final WeatherProperties properties = new WeatherProperties(
            new WeatherProperties.Provider("http://x", "k", Duration.ofSeconds(2), Duration.ofSeconds(4)),
            new WeatherProperties.Cache(Duration.ofMinutes(10), Duration.ofHours(6), Duration.ofSeconds(60)),
            new WeatherProperties.Retry(1, Duration.ofMillis(5)),
            new WeatherProperties.Breaker(3, Duration.ofMillis(100)),
            new WeatherProperties.Budget(50));

    private final CircuitBreaker breaker = new CircuitBreaker(properties);

    @Test
    void opensAfterThreeFailuresInARowAndThenStopsCallingAtAll() {
        AtomicInteger calls = new AtomicInteger();

        // three real failures
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                calls.incrementAndGet();
                throw new ProviderUnavailableException("down");
            })).isInstanceOf(ProviderUnavailableException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        // a fourth request: rejected WITHOUT running the work at all
        assertThatThrownBy(() -> breaker.execute(() -> {
            calls.incrementAndGet();
            return "never reached";
        })).isInstanceOf(ProviderUnavailableException.class);

        // still 3. the fourth call never left the building, and THAT is
        // the entire value of a circuit breaker.
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aCityThatDoesNotExistNeverOpensTheCircuit() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new CityNotFoundException("Zzzzqqq");
            })).isInstanceOf(CityNotFoundException.class);
        }
        // Ten typos must not take a healthy integration offline.
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void recoversOnItsOwnOnceTheProviderAnswersAgain() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new ProviderUnavailableException("down");
            })).isInstanceOf(ProviderUnavailableException.class);
        }

        Thread.sleep(150);                                      // longer than the 100ms window

        String result = breaker.execute(() -> "sunny");        // the half-open probe
        assertThat(result).isEqualTo("sunny");
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
