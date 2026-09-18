package com.example.weather_api.metrics;

import com.example.weather_api.resilience.CircuitBreaker;
import com.example.weather_api.resilience.UpstreamBudget;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Four questions, four numbers. Nothing here changes behaviour: this class
 * only watches, which is why it can safely be the last thing you add.
 */
@Component
public class WeatherMetrics {

    // Our own running totals. The gauge below reads these.
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong lookups = new AtomicLong();

    private final Counter fromCache;        // answered from a fresh cache entry
    private final Counter fromUpstream;     // we called the provider
    private final Counter fromStale;        // they were down, we served an old copy
    private final Timer upstreamDuration;   // how long THEIR call took

    public WeatherMetrics(MeterRegistry registry, CircuitBreaker breaker, UpstreamBudget budget) {

        // ---- 1. is the cache earning its keep? ----
        // A GAUGE is for a value that goes up and down and is read on demand.
        Gauge.builder("weather.cache.hit_ratio", this, WeatherMetrics::hitRatio)
                .description("Cache hits as a fraction of all lookups")
                .register(registry);

        // ---- 2. is it us or them? ----
        // A TIMER records durations and can report percentiles, which is the
        // only honest way to describe latency.
        this.upstreamDuration = Timer.builder("weather.upstream.duration")
                .description("How long the weather provider takes to answer")
                .publishPercentiles(0.5, 0.95, 0.99)            // p50 p95 p99, not an average
                .register(registry);

        // ---- 3. what is actually happening right now? ----
        // COUNTERS only ever go up. The tag has exactly THREE possible values.
        // A city tag here would be thousands of series: a cardinality
        // explosion, and the one way a metric can break the system.
        this.fromCache = Counter.builder("weather.requests").tag("source", "cache").register(registry);
        this.fromUpstream = Counter.builder("weather.requests").tag("source", "upstream").register(registry);
        this.fromStale = Counter.builder("weather.requests").tag("source", "stale").register(registry);

        // ---- 4. are we currently giving up on them? ----
        // 0 closed, 1 half-open, 2 open: a number, because gauges are numbers.
        Gauge.builder("weather.breaker.state", breaker, b -> switch (b.state()) {
                    case CLOSED -> 0;
                    case HALF_OPEN -> 1;
                    case OPEN -> 2;
                })
                .description("0 closed, 1 half-open, 2 open")
                .register(registry);

        // A bonus fifth: how much of this minute's allowance is gone.
        Gauge.builder("weather.budget.spent", budget, UpstreamBudget::spentThisMinute)
                .description("Upstream calls spent in the current minute")
                .register(registry);
    }

    // ---- the methods WeatherService calls ----

    public void recordCacheHit() {
        hits.incrementAndGet();
        lookups.incrementAndGet();
        fromCache.increment();
    }

    public void recordCacheMiss() {
        lookups.incrementAndGet();                              // a lookup that was not a hit
        fromUpstream.increment();
    }

    public void recordStaleServed() {
        lookups.incrementAndGet();
        fromStale.increment();
    }

    public Timer upstreamTimer() {
        return upstreamDuration;
    }

    /** Hits divided by lookups. Zero lookups is 1.0, not a divide-by-zero. */
    private double hitRatio() {
        long total = lookups.get();
        return total == 0 ? 1.0 : (double) hits.get() / total;
    }
}
