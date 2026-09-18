package com.example.weather_api.error;

public class UpstreamRateLimitedException extends RuntimeException {
    private final String retryAfter;

    public UpstreamRateLimitedException(String retryAfter) {
        super("The Weather provider rate-limited us");
        this.retryAfter = retryAfter;
    }

    public String retryAfter() {
        return retryAfter;
    }
}
