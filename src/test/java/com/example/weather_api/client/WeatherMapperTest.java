package com.example.weather_api.client;

import com.example.weather_api.api.WeatherResponse;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WeatherMapperTest {
    private final WeatherMapper mapper = new WeatherMapper();

    // a realistic upstream object by hand, so no network is needed
    private UpstreamWeather sample(){
        return new UpstreamWeather(
                "Lagos",
                1757836800L,
                new UpstreamWeather.Main(29.42999 ,34.1,78,1012),
                new UpstreamWeather.Wind(4.1, 210),
                new UpstreamWeather.Sys("NG"),
                List.of(new UpstreamWeather.Condition(
                        "Clouds","partly cloudy", "03d"
                ))
        );
    }

    @Test
    void joinsCityAndCountry() {
        assertThat(mapper.toResponse(sample()).location()).isEqualTo("Lagos, NG");
    }
    @Test
    void roundsTemperatureToOneDecimal() {
        assertThat(mapper.toResponse(sample()).temperatureC()).isEqualTo(29.4);
    }
    @Test
    void convertsMetresPerSecondToKilometresPerHour() {
        // 4.1 m/s * 3.6 = 14.76, rounded to 14.8. If this test ever goes red,
        // somebody has quietly changed your API's units.
        assertThat(mapper.toResponse(sample()).windKph())
                .isEqualTo(14.8, within(0.001));
    }
    @Test
    void capitalisesTheConditionForHumans() {
        assertThat(mapper.toResponse(sample()).condition())
                .isEqualTo("Partly cloudy");
    }
    @Test
    void anEmptyConditionsArrayIsUnknownAndNotACrash() {
        UpstreamWeather noConditions = new UpstreamWeather(
                "Lagos", 1757836800L,
                new UpstreamWeather.Main(29.4, 34.1, 78, 1012),
                new UpstreamWeather.Wind(4.1, 210),
                new UpstreamWeather.Sys("NG"),
                List.of());                                  // empty on purpose
        WeatherResponse response = mapper.toResponse(noConditions);
        assertThat(response.condition()).isEqualTo("Unknown");
    }
    @Test
    void aFreshlyMappedResponseIsNotMarkedAsCached() {
        WeatherResponse response = mapper.toResponse(sample());
        assertThat(response.cached()).isFalse();
        assertThat(response.cacheAgeSeconds()).isNull();
    }
}

