package com.example.weather_api.resilience;

import com.example.weather_api.config.WeatherProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * How many calls we allow OURSELVES to make to the provider each minute.
 *
 * OpenWeatherMap's free tier allows 60/minute. We allow ourselves 50, and the
 * gap is deliberate: room for a retry, and for our clock not quite matching theirs.
 */
@Component
public class UpstreamBudget {

    private static final Logger log = LoggerFactory.getLogger(UpstreamBudget.class);

    // Which minute we are counting, and how many calls we have spent in it.
    private final AtomicReference<Instant> currentMinute =
            new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MINUTES));
    private final AtomicInteger spent = new AtomicInteger(0);

    private final WeatherProperties properties;

    public UpstreamBudget(WeatherProperties properties) {
        this.properties = properties;
    }

    /**
     * Takes one call out of this minute's allowance.
     * Returns false when the allowance is gone.
     */
    public boolean tryConsume() {
        // truncatedTo(MINUTES) turns 10:17:43 into 10:17:00, so every call
        // in the same minute produces the same value. That is the "window".
        Instant thisMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        // A new minute? Then the allowance starts again from zero.
        if (!thisMinute.equals(currentMinute.get())) {
            currentMinute.set(thisMinute);
            spent.set(0);
        }

        int used = spent.incrementAndGet();
        int allowed = properties.budget().perMinute();              // 50

        if (used > allowed) {
            // Log it once per exhausted minute, not once per refused request,
            // or a busy minute fills your log with the same line.
            if (used == allowed + 1) {
                log.warn("Upstream budget of {} calls/minute is used up. "
                        + "Serving from cache only until the next minute.", allowed);
            }
            return false;
        }
        return true;
    }

    /** For the metrics in step 12. */
    public int spentThisMinute() {
        return spent.get();
    }
}
