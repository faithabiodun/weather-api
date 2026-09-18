package com.example.weather_api.client;

import com.example.weather_api.config.WeatherProperties;
import com.example.weather_api.error.CityNotFoundException;
import com.example.weather_api.error.ProviderUnavailableException;
import com.example.weather_api.error.UpstreamRateLimitedException;
import com.example.weather_api.error.WeatherConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;


@Component
public class WeatherClient {
    private static final Logger logger = LoggerFactory.getLogger(WeatherClient.class);

    private final RestClient http;
    private final WeatherProperties properties;

    public WeatherClient(RestClient weatherRestClient,
                         WeatherProperties properties) {
        this.http = weatherRestClient;
        this.properties = properties;
    }

    public UpstreamWeather fetchCurrent(String city){
        return fetchAs(city, UpstreamWeather.class);
    }

    //fetch the raw JSON for a city
    public String fetchRAWJson(String city) {
        return fetchAs(city, String.class);
    }

    private <T> T fetchAs(String city, Class<T> responseType) {
        long startedAt = System.nanoTime(); //for the timing log at the end
        try {
            return http.get()
                    .uri(builder -> builder
                            .path("/weather")
                            .queryParam("q", city)
                            .queryParam("units", "metric")
                            .queryParam("appid", properties.provider().apiKey())
                            .build())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new CityNotFoundException(city);
                    })
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw new WeatherConfigurationException("The weather API key was rejected");
                    })
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new UpstreamRateLimitedException(response.getHeaders().getFirst("Retry-After"));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ProviderUnavailableException(
                                "Weather provider returned " + response.getStatusCode());
                    })
                    .body(responseType);
        } catch (ResourceAccessException e) {
            throw new ProviderUnavailableException("Weather provider did not respond in time", e);
        } finally {
            long ms = (System.nanoTime() - startedAt) / 1_000_000;
            logger.debug("Upstream weather lookup for {} took {} ms", city, ms);
        }
    }
}
