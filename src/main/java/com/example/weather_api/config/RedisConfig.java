package com.example.weather_api.config;

import com.example.weather_api.cache.CachedWeather;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, CachedWeather> weatherRedisTemplate(
            RedisConnectionFactory connectionFactory, JsonMapper jsonMapper) {
        // the same JsonMapper Spring uses for API responses, so Redis and HTTP agree on the JSON
        JacksonJsonRedisSerializer<CachedWeather> json =
                new JacksonJsonRedisSerializer<>(jsonMapper, CachedWeather.class);

        RedisTemplate<String, CachedWeather> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        template.afterPropertiesSet();
        return template;
    }
}
