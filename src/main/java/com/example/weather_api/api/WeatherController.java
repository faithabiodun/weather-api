package com.example.weather_api.api;

import com.example.weather_api.config.WeatherProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/weather")
@Validated
public class WeatherController {
    private final WeatherService service;
    private final WeatherProperties properties;

    public WeatherController(WeatherService service, WeatherProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    //GET api/v1/weather/{city}
    @GetMapping("/{city}")
    public ResponseEntity<WeatherResponse> current(
            @PathVariable @Size(max = 60) @NotBlank String city
    ){
        WeatherResponse response = service.current(city);

        return ResponseEntity.ok()
                .header("X-Cache", cacheState(response))
                .body(response);
    }

    // Three states, and the caller can tell them apart:
    //   MISS  - we called the provider for this
    //   HIT   - fresh, from the cache
    //   STALE - from the cache, but older than the TTL. they are down.
    private String cacheState(WeatherResponse response) {
        if (!response.cached()) {
            return "MISS";
        }
        long freshForSeconds = properties.cache().ttl().toSeconds();   // from application.yml, not a magic 600
        return response.cacheAgeSeconds() > freshForSeconds ? "STALE" : "HIT";
    }
}
