package com.example.weather_api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.weather_api.api.WeatherResponse;
import com.example.weather_api.cache.CachedWeather;
import com.example.weather_api.cache.WeatherCache;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// The one test that uses a REAL Redis. Testcontainers starts it before the
// tests and throws it away afterwards, so nothing is left on your machine.
// No provider is called, so a placeholder key is enough to start the app.
@SpringBootTest(properties = "weather.provider.api-key=test-key")
@Testcontainers
class WeatherApiIT {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    /**
     * The container gets a RANDOM free port, so the port cannot be written in
     * application.yml. This method hands Spring the real one at startup.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    WeatherCache cache;

    @Test
    void whatWePutInIsWhatWeGetBack() {
        WeatherResponse original = new WeatherResponse(
                "Lagos, NG", 29.4, 34.1, "Partly cloudy",
                78, 14.8, Instant.parse("2026-09-14T10:00:00Z"), false, null);

        cache.put("Lagos", CachedWeather.of(original));
        CachedWeather readBack = cache.find("Lagos").orElseThrow();

        // If the serializers in step 7 are wrong, THIS is where you find out -
        // usually on the Instant, which is why it is asserted separately.
        assertThat(readBack.response().location()).isEqualTo("Lagos, NG");
        assertThat(readBack.response().windKph()).isEqualTo(14.8);
        assertThat(readBack.response().observedAt()).isEqualTo(Instant.parse("2026-09-14T10:00:00Z"));
    }

    @Test
    void twoSpellingsOfOneCityFindTheSameEntry() {
        WeatherResponse stored = new WeatherResponse(
                "Ilorin, NG", 31.0, 35.0, "Sunny", 40, 9.0, Instant.now(), false, null);

        cache.put(" ILORIN ", CachedWeather.of(stored));                // written one way
        assertThat(cache.find("ilorin")).isPresent();                   // found another
    }

    @Test
    void anEvictedEntryIsReallyGone() {
        cache.put("Kano", CachedWeather.of(new WeatherResponse(
                "Kano, NG", 35.0, 38.0, "Clear", 20, 12.0, Instant.now(), false, null)));

        cache.evict("Kano");

        assertThat(cache.find("Kano")).isEmpty();
    }
}
