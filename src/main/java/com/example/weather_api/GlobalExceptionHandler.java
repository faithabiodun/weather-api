package com.example.weather_api;

import com.example.weather_api.api.ApiError;
import com.example.weather_api.error.CityNotFoundException;
import com.example.weather_api.error.ProviderUnavailableException;
import com.example.weather_api.error.UpstreamRateLimitedException;
import com.example.weather_api.error.WeatherConfigurationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CityNotFoundException.class)
    public ResponseEntity<ApiError> cityNotFound(
            CityNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        404,
                        "CITY_NOT_FOUND",
                        exception.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ApiError> providerUnavailable(
            ProviderUnavailableException exception, HttpServletRequest request) {
        log.warn("Weather provider unavailable: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(ApiError.of(
                        503,
                        "PROVIDER_UNAVAILABLE",
                        "The weather provider is not responding. Try again shortly.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(UpstreamRateLimitedException.class)
    public ResponseEntity<ApiError> rateLimited(
            UpstreamRateLimitedException exception, HttpServletRequest request) {
        String retryAfter = exception.retryAfter() == null ? "60" : exception.retryAfter();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", retryAfter)
                .body(ApiError.of(
                        429,
                        "RATE_LIMITED",
                        "Too many requests. Try again shortly.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(WeatherConfigurationException.class)
    public ResponseEntity<ApiError> configuration(
            WeatherConfigurationException exception, HttpServletRequest request) {
        log.error("Weather API is misconfigured: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        500,
                        "INTERNAL_ERROR",
                        "Something went wrong on our side.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> anythingElse(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        500,
                        "INTERNAL_ERROR",
                        "Something went wrong on our side.",
                        request.getRequestURI()));
    }
}
