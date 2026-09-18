package com.example.weather_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "weather.provider.api-key=test-key")
class WeatherApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
