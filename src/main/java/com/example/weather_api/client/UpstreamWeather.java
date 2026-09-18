package com.example.weather_api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstreamWeather(

        //@json properties map their key on the left to the field name on the right

        @JsonProperty("name") String cityName,
        @JsonProperty("dt") long observedAtEpochSeconds,
        @JsonProperty("main") Main main,
        @JsonProperty("wind") Wind wind,
        @JsonProperty("sys") Sys sys,
        @JsonProperty("weather") List<Condition> conditions
) {
    //one nested record per JSON object. each one needs its own
    //ignoreUnknown is true because Jackson sets them independently

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            double temp,
            double feels_like,
            int humidity,
            int pressure
    ){ }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(
            double speed,
            int deg
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sys(
            String country
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
            String main,
            String description,
            String icon
    ){}
}
