package com.example.weather_api.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheKeyTest {

    @Test
    void differentSpellingsOfOneCityShareOneKey() {
        // If this test ever fails, your hit rate silently drops to near zero
        // and absolutely nothing else looks broken. That is why it exists.
        String expected = "weather:v1:lagos";

        assertThat(CacheKey.forCity("Lagos")).isEqualTo(expected);
        assertThat(CacheKey.forCity("lagos")).isEqualTo(expected);
        assertThat(CacheKey.forCity(" LAGOS ")).isEqualTo(expected);
    }

    @Test
    void repeatedSpacesInsideANameAreCollapsed() {
        assertThat(CacheKey.forCity("New   York")).isEqualTo("weather:v1:new york");
    }

    @Test
    void theKeyIsPrefixedAndVersioned() {
        // The prefix lets you find them; the version lets you throw them away.
        assertThat(CacheKey.forCity("Ilorin")).startsWith("weather:v1:");
    }
}
