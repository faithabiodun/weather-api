package com.example.weather_api;

import org.springframework.boot.SpringApplication;

public class TestWeatherApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(WeatherApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
