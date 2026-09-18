package com.example.weather_api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Fake collaborators, so this test needs no Redis, no network and no Spring.
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock WeatherClient client;           // a pretend provider
    @Mock WeatherCache cache;             // a pretend Redis
    @Mock WeatherMapper mapper;           // a pretend translator

    // Real settings, real retry policy and breaker: they are pure logic, and a
    // mocked WeatherProperties would hand the service nulls. One attempt, so a
    // failing provider fails once and quickly.
    private final WeatherProperties properties = new WeatherProperties(
            new WeatherProperties.Provider("http://x", "k", Duration.ofSeconds(2), Duration.ofSeconds(4)),
            new WeatherProperties.Cache(Duration.ofMinutes(10), Duration.ofHours(6), Duration.ofSeconds(60)),
            new WeatherProperties.Retry(1, Duration.ofMillis(5)),
            new WeatherProperties.Breaker(5, Duration.ofSeconds(30)),
            new WeatherProperties.Budget(50));

    private WeatherService service;       // the real thing, wired to the fakes

    @BeforeEach
    void wire() {
        CircuitBreaker breaker = new CircuitBreaker(properties);
        UpstreamBudget budget = new UpstreamBudget(properties);
        WeatherMetrics metrics = new WeatherMetrics(new SimpleMeterRegistry(), breaker, budget);
        service = new WeatherService(client, mapper, cache,
                new RetryPolicy(properties), breaker, budget, metrics, properties);
    }

    private WeatherResponse anyResponse() {
        return new WeatherResponse("Lagos, NG", 29.4, 34.1, "Partly cloudy",
                78, 14.8, Instant.parse("2026-09-14T10:00:00Z"), false, null);
    }

    @Test
    void aCacheHitNeverCallsTheProvider() {
        // GIVEN the cache already has an answer, stored 142 seconds ago
        CachedWeather stored = new CachedWeather(anyResponse(), Instant.now().minusSeconds(142));
        when(cache.find("Lagos")).thenReturn(Optional.of(stored));

        // WHEN we ask for it
        WeatherResponse result = service.current("Lagos");

        // THEN the provider is never called. THIS is the assertion that
        // proves the project works. Everything else is detail.
        verify(client, never()).fetchCurrent(anyString());
        assertThat(result.cached()).isTrue();
        assertThat(result.cacheAgeSeconds()).isBetween(140L, 150L);
    }

    @Test
    void aCacheMissCallsTheProviderExactlyOnceAndStoresTheResult() {
        // GIVEN the cache is empty
        when(cache.find("Lagos")).thenReturn(Optional.empty());
        when(client.fetchCurrent("Lagos")).thenReturn(upstreamSample());
        when(mapper.toResponse(any())).thenReturn(anyResponse());

        WeatherResponse result = service.current("Lagos");

        verify(client, times(1)).fetchCurrent("Lagos");     // exactly one call out
        verify(cache, times(1)).put(anyString(), any());    // and we stored it
        assertThat(result.cached()).isFalse();
    }

    @Test
    void whenTheProviderIsDownAnOldCopyIsServedHonestly() {
        // GIVEN a copy from 30 minutes ago: past the 10m ttl, so not fresh
        CachedWeather old = new CachedWeather(anyResponse(), Instant.now().minusSeconds(1800));
        when(cache.find("Lagos")).thenReturn(Optional.of(old));
        when(client.fetchCurrent("Lagos")).thenThrow(new ProviderUnavailableException("down"));

        WeatherResponse result = service.current("Lagos");

        // An old answer beats no answer -- but it says how old it is.
        assertThat(result.cached()).isTrue();
        assertThat(result.cacheAgeSeconds()).isGreaterThanOrEqualTo(1800L);
    }

    @Test
    void whenTheProviderIsDownAndNothingIsStoredItReallyIsA503() {
        when(cache.find("Kano")).thenReturn(Optional.empty());
        when(client.fetchCurrent("Kano")).thenThrow(new ProviderUnavailableException("down"));

        assertThatThrownBy(() -> service.current("Kano"))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private UpstreamWeather upstreamSample() {
        return new UpstreamWeather("Lagos", 1757836800L,
                new UpstreamWeather.Main(29.4, 34.1, 78, 1012),
                new UpstreamWeather.Wind(4.1, 210),
                new UpstreamWeather.Sys("NG"),
                List.of(new UpstreamWeather.Condition("Clouds", "partly cloudy", "03d")));
    }
}
