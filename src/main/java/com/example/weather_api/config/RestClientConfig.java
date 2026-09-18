package com.example.weather_api.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final WeatherProperties properties;

    public RestClientConfig(WeatherProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestClient weatherRestClient() {
        Duration connect = properties.provider().connectTimeout();
        Duration read = properties.provider().readTimeout();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);

        return RestClient.builder()
                .baseUrl(properties.provider().baseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "faith-weather-api/1.0")
                .build();
    }
}
