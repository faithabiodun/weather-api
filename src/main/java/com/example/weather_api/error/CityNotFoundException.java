package com.example.weather_api.error;

public class CityNotFoundException extends RuntimeException {
    public CityNotFoundException(String city) {
        super("No Weather found for " + city);
    }
}
