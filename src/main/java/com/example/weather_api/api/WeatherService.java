package com.example.weather_api.api;

import com.example.weather_api.cache.CachedWeather;
import com.example.weather_api.cache.WeatherCache;
import com.example.weather_api.client.UpstreamWeather;
import com.example.weather_api.client.WeatherClient;
import com.example.weather_api.client.WeatherMapper;
import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.ProviderUnavailableException;
import com.example.weather_api.metrics.WeatherMetrics;
import com.example.weather_api.resilience.CircuitBreaker;
import com.example.weather_api.resilience.RetryPolicy;
import com.example.weather_api.resilience.UpstreamBudget;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherClient client;
    private final WeatherMapper mapper;
    private final WeatherCache cache;
    private final RetryPolicy retryPolicy;          // step 9
    private final CircuitBreaker breaker;           // step 10
    private final UpstreamBudget budget;            // step 11
    private final WeatherMetrics metrics;           // step 12
    private final WeatherProperties properties;

    public WeatherService(WeatherClient client, WeatherMapper mapper, WeatherCache cache,
                          RetryPolicy retryPolicy, CircuitBreaker breaker,
                          UpstreamBudget budget, WeatherMetrics metrics,
                          WeatherProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.cache = cache;
        this.retryPolicy = retryPolicy;
        this.breaker = breaker;
        this.budget = budget;
        this.metrics = metrics;
        this.properties = properties;
    }

    public WeatherResponse current(String city) {
        Optional<CachedWeather> stored = cache.find(city);

        // ---- 1. a FRESH copy. the common case, and the fast one ----
        if (stored.isPresent() && !stored.get().olderThan(properties.cache().ttl())) {
            CachedWeather hit = stored.get();
            metrics.recordCacheHit();
            return hit.response().asCached(hit.ageSeconds());
        }

        // ---- 2. no fresh copy. try them, through the breaker and retries ----
        try {
            // THE ORDERING IS THE POINT. We are past the cache lookup, so this
            // request really is going to cost a call. Only now do we ask
            // whether we can afford one.
            if (!budget.tryConsume()) {
                // Same exception as "they are down", which means we inherit
                // the stale-copy fallback from step 10 for free.
                throw new ProviderUnavailableException("Upstream call budget for this minute is used up");
            }

            // The timer wraps ONLY the real HTTP call, so every attempt the provider
            // actually sees is timed -- and an open circuit, which calls nobody,
            // adds nothing. record(...) times it even when it fails.
            UpstreamWeather upstream = breaker.execute(
                    () -> retryPolicy.execute(
                            () -> metrics.upstreamTimer().record(
                                    () -> client.fetchCurrent(city))));
            metrics.recordCacheMiss();
            WeatherResponse fresh = mapper.toResponse(upstream);

            // Kept in Redis for the LONG stale-ttl, so an old copy is still here
            // to fall back on. "Fresh enough" is decided above, by cachedAt.
            cache.put(city, CachedWeather.of(fresh));
            return fresh;

        } catch (ProviderUnavailableException e) {
            // ---- 3. they are down. an old answer beats no answer ----
            if (stored.isPresent()) {
                CachedWeather stale = stored.get();
                log.warn("Serving {}s-old weather for {} because the provider is unavailable",
                        stale.ageSeconds(), city);
                metrics.recordStaleServed();
                // Marked as cached, with its REAL age, so the caller can see
                // exactly how old it is and decide for themselves.
                return stale.response().asCached(stale.ageSeconds());
            }
            // ---- 4. down, and nothing stored. now it really is a 503 ----
            throw e;
        }
    }
}
