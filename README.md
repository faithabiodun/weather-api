A Spring Boot REST API that caches OpenWeatherMap data in Redis, cutting upstream calls by around 95% and serving repeat requests in milliseconds.
Stays up when the provider goes down, using retries with backoff, a circuit breaker, stale-data fallback and a self-imposed rate limit, all tested with JUnit and Testcontainers.
