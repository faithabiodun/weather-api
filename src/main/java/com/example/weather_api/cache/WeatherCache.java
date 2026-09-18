package com.example.weather_api.cache;

import com.example.weather_api.config.WeatherProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

// THE ONLY CLASS IN THIS PROJECT THAT KNOWS REDIS EXISTS.
// Swap Redis for something else and exactly this file changes.
@Component
public class WeatherCache {

    private static final Logger log = LoggerFactory.getLogger(WeatherCache.class);

    private final RedisTemplate<String, CachedWeather> redis;
    private final WeatherProperties properties;

    public WeatherCache(RedisTemplate<String, CachedWeather> weatherRedisTemplate,
                        WeatherProperties properties) {
        this.redis = weatherRedisTemplate;
        this.properties = properties;
    }

    /**
     * Looks for a copy. Returns empty on a miss AND on a Redis failure,
     * because a broken cache must never become a broken request.
     */
    public Optional<CachedWeather> find(String city) {
        String key = CacheKey.forCity(city);
        try {
            // Optional.ofNullable turns "might be null" into something the
            // caller cannot forget to check.
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            // Redis is down or slow. Log it, and pretend it was a miss.
            // The request then goes to the provider and still succeeds.
            log.warn("Cache lookup failed for {}, treating as a miss: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** Stores a copy with a TTL. Failing to store is not worth an exception. */
    public void put(String city, CachedWeather value) {
        String key = CacheKey.forCity(city);
        try {
            // set(key, value, ttl) both writes it and tells Redis to delete it
            // automatically after the TTL. You never clean up.
            redis.opsForValue().set(key, value, keepForWithJitter());
        } catch (Exception e) {
            log.warn("Cache write failed for {}, carrying on without it: {}", key, e.getMessage());
        }
    }

    /** Removes one entry. Used by the tests and the Postman warm-up. */
    public void evict(String city) {
        try {
            redis.delete(CacheKey.forCity(city));
        } catch (Exception e) {
            log.warn("Cache evict failed: {}", e.getMessage());
        }
    }

    /**
     * How long Redis KEEPS the copy: the long stale-ttl (6h), plus a random
     * slice of up to a minute.
     *
     * Kept with the LONG one, so that when the provider is down (step 10) an
     * old copy is still here to fall back on. Whether a copy is "fresh" is a
     * separate question, answered in WeatherService from cachedAt and the
     * short ttl (10m) -- not by whether Redis still has it.
     *
     * Without the random part, every key written during a busy minute expires
     * during the SAME later minute. That is a cache stampede, and jitter is
     * the two-line fix for it.
     */
    private Duration keepForWithJitter() {
        Duration base = properties.cache().staleTtl();                  // 6h
        long jitterMillis = properties.cache().jitter().toMillis();     // 60000
        long extra = ThreadLocalRandom.current().nextLong(0, jitterMillis + 1);
        return base.plusMillis(extra);
    }
}
