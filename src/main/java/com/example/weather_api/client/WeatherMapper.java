package com.example.weather_api.client;

import com.example.weather_api.api.WeatherResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class WeatherMapper {
    public WeatherResponse toResponse(UpstreamWeather upstream) {
        String condition = upstream.conditions().isEmpty()
                ? "Unknown"
                : capitalise(upstream.conditions().get(0).description());

        return new WeatherResponse(
                upstream.cityName() + ", " + upstream.sys().country(),

                round1(upstream.main().temp()),
                round1(upstream.main().feels_like()),

                condition,
                upstream.main().humidity(),

                round1(upstream.wind().speed() * 3.6),

                Instant.ofEpochSecond(upstream.observedAtEpochSeconds()),

                false,
                null
        );
    }


    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    //partly cloudy becomes Partly cloudy
    private String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return "Unknown";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
