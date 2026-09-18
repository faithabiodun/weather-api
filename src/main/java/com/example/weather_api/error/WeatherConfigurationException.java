package com.example.weather_api.error;

public class WeatherConfigurationException extends RuntimeException {
    public WeatherConfigurationException(String message) {
        super(message);
    }
}
