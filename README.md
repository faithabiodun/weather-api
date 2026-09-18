# weather-api

A caching wrapper around a third-party weather provider (OpenWeatherMap).
Answers come from Redis when they can, from the provider when they must, and
from a stale copy -- clearly labelled -- when the provider is down.

## Run it

Needs Java 21+, Docker Desktop running, and an OpenWeatherMap API key.

```bash
cp .env.example .env                 # then put your key in it
docker compose up -d                 # starts Redis
set -a; source .env; set +a          # loads WEATHER_API_KEY (Git Bash)
./mvnw spring-boot:run
```

## Try it

```bash
curl -i localhost:8080/api/v1/weather/Lagos      # first call: X-Cache: MISS
curl -i localhost:8080/api/v1/weather/Lagos      # second call: X-Cache: HIT
```

## The X-Cache header

| Value   | Meaning                                                        |
|---------|----------------------------------------------------------------|
| `MISS`  | we called the provider for this                                |
| `HIT`   | served from a fresh cache entry                                |
| `STALE` | provider unavailable; this copy is older than the TTL          |

The body always says `cached` and `cacheAgeSeconds`, so a caller can see
exactly how old an answer is.

## How it survives the provider

| Problem                        | What happens                                         |
|--------------------------------|------------------------------------------------------|
| Provider slow or blipping      | retried, with exponential backoff and jitter         |
| Provider down                  | circuit breaker opens; requests fail fast            |
| Provider down, copy in cache   | stale copy served with `X-Cache: STALE`              |
| Provider down, nothing cached  | `503` with `Retry-After`                             |
| Our own call budget used up    | treated as "provider down"; cache hits still free    |
| City does not exist            | `404`, never retried, never trips the breaker        |
| Redis down                     | every request goes to the provider -- slower, not broken |

## The four numbers

```bash
curl -s localhost:8080/actuator/metrics/weather.cache.hit_ratio
curl -s localhost:8080/actuator/metrics/weather.upstream.duration
curl -s localhost:8080/actuator/metrics/weather.requests
curl -s localhost:8080/actuator/metrics/weather.breaker.state      # 0 closed, 1 half-open, 2 open
```

## Tests

```bash
./mvnw verify        # unit tests, plus WeatherApiIT against a real Redis (Docker must be running)
```

## Settings

Everything is in `src/main/resources/application.yml`.
The only thing that is not is `WEATHER_API_KEY`, which comes from `.env`.
