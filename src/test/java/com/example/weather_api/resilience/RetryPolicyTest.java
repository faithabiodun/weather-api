package com.example.weather_api.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.CityNotFoundException;
import com.example.weather_api.error.ProviderUnavailableException;
import com.example.weather_api.error.UpstreamRateLimitedException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    /** Settings built by hand, with tiny delays so the test stays fast. */
    private final WeatherProperties properties = new WeatherProperties(
            new WeatherProperties.Provider("http://x", "k", Duration.ofSeconds(2), Duration.ofSeconds(4)),
            new WeatherProperties.Cache(Duration.ofMinutes(10), Duration.ofHours(6), Duration.ofSeconds(60)),
            new WeatherProperties.Retry(3, Duration.ofMillis(5)),          // 5ms, not 200
            new WeatherProperties.Breaker(5, Duration.ofSeconds(30)),
            new WeatherProperties.Budget(50));

    private final RetryPolicy policy = new RetryPolicy(properties);

    @Test
    void succeedsOnTheSecondAttemptWhenTheFirstOneWasTransient() {
        AtomicInteger calls = new AtomicInteger();                          // counts the attempts

        String result = policy.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new ProviderUnavailableException("a blip");
            }
            return "sunny";
        });

        assertThat(result).isEqualTo("sunny");
        assertThat(calls.get()).isEqualTo(2);                               // tried twice, not three
    }

    @Test
    void neverRetriesACityThatDoesNotExist() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new CityNotFoundException("Zzzzqqq");
        })).isInstanceOf(CityNotFoundException.class);

        // ONE call. Retrying this would burn three times the quota for an
        // answer that is never going to change.
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void neverRetriesWhenTheyHaveAlreadyRateLimitedUs() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new UpstreamRateLimitedException("60");
        })).isInstanceOf(UpstreamRateLimitedException.class);

        // Retrying here is the exact behaviour they are punishing us for.
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new ProviderUnavailableException("still down");
        })).isInstanceOf(ProviderUnavailableException.class);

        assertThat(calls.get()).isEqualTo(3);                               // three, and no more
    }
}
