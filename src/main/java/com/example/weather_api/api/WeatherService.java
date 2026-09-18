package com.example.weather_api.api;

import com.example.weather_api.api.WeatherResponse;
import com.example.weather_api.client.UpstreamWeather;
import com.example.weather_api.client.WeatherClient;
import com.example.weather_api.client.WeatherMapper;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private final WeatherClient client;
    private final WeatherMapper mapper;

    public WeatherService(WeatherClient client, WeatherMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public WeatherResponse current(String city) {
        UpstreamWeather upstream = client.fetchCurrent(city);

        return mapper.toResponse(upstream);
    }





}
