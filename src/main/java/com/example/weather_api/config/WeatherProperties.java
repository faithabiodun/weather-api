package com.example.weather_api.config;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix="weather")
@Validated
public record WeatherProperties (
        @NotNull Provider provider,
        @NotNull Cache cache,
        @NotNull Retry retry,
        @NotNull Breaker breaker,
        @NotNull Budget budget
){
    public record Provider (
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout
    ){}

    public record Cache (
            @NotNull Duration ttl,
            @NotNull Duration staleTtl,
            @NotNull Duration jitter
    ){}

    public record Retry (
            @Min(1) int attempts,
            @NotNull Duration baseDelay
            ){}

    public record Breaker (
            @Min(1) int failureThreshold,
            @NotNull Duration openDuration
    ){}

    public record Budget (
            @Min(1) int perMinute
    ){}

}
