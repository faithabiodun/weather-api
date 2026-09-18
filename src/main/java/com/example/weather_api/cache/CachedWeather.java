package com.example.weather_api.cache;

import com.example.weather_api.api.WeatherResponse;
import java.time.Duration;
import java.time.Instant;

// What actually goes into Redis: the answer, plus the moment we stored it.
// Storing the time OURSELVES is what lets the response say how old it is.
public record CachedWeather(
        WeatherResponse response,                       // the answer we would have sent anyway
        Instant cachedAt) {                             // when we put it here

    public static CachedWeather of(WeatherResponse response) {
        return new CachedWeather(response, Instant.now());
    }

    /** How many whole seconds ago this was written. */
    public long ageSeconds() {
        return Duration.between(cachedAt, Instant.now()).toSeconds();
    }

    /** True if this copy is older than the TTL and should only be used stale. */
    public boolean olderThan(Duration limit) {
        return Duration.between(cachedAt, Instant.now()).compareTo(limit) > 0;
    }
}
