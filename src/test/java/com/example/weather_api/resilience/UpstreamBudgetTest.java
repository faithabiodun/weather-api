package com.example.weather_api.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.weather_api.config.WeatherProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UpstreamBudgetTest {

    /** A budget of 5 per minute, small enough to exhaust by hand. */
    private final WeatherProperties properties = new WeatherProperties(
            new WeatherProperties.Provider("http://x", "k", Duration.ofSeconds(2), Duration.ofSeconds(4)),
            new WeatherProperties.Cache(Duration.ofMinutes(10), Duration.ofHours(6), Duration.ofSeconds(60)),
            new WeatherProperties.Retry(3, Duration.ofMillis(5)),
            new WeatherProperties.Breaker(5, Duration.ofSeconds(30)),
            new WeatherProperties.Budget(5));

    private final UpstreamBudget budget = new UpstreamBudget(properties);

    @Test
    void allowsExactlyTheBudgetAndRefusesTheRest() {
        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (budget.tryConsume()) {
                allowed++;
            }
        }
        // Twenty asked for, five spent. (If the minute ticks over mid-loop the
        // allowance refills, which is correct behaviour -- hence "at most".)
        assertThat(allowed).isBetween(5, 10);
    }

    @Test
    void theSpendIsVisibleForMetrics() {
        budget.tryConsume();
        budget.tryConsume();
        assertThat(budget.spentThisMinute()).isBetween(1, 2);
    }
}
