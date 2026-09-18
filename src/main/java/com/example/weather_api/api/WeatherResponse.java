package com.example.weather_api.api;

import java.time.Instant;

public record WeatherResponse(
        String location,
        double temperatureC,
        double feelsLike,
        String condition,
        int humidity,
        double windKph,
        Instant observedAt,
        boolean cached,
        Long cacheAgeSeconds
) {
    public WeatherResponse asCached(long ageSeconds) {
        return new WeatherResponse(
                location, temperatureC,
                feelsLike, condition, humidity, windKph, observedAt, true, ageSeconds
        );
    }
}
