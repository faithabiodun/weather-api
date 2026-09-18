package com.example.weather_api.cache;

import java.util.Locale;

// Tiny class, enormous effect. Every cache hit you will ever get depends on
// two spellings of the same city producing the same string here.
public final class CacheKey {

    // A prefix, so "KEYS weather:*" shows you only this project's entries.
    // A VERSION, so changing the shape of what you store is one character
    // away from invalidating every old entry at once.
    private static final String PREFIX = "weather:v1:";

    private CacheKey() { }                              // a utility class, never instantiated

    /** " LAGOS " becomes "weather:v1:lagos". */
    public static String forCity(String city) {
        String normalised = city
                .trim()                                 // drop spaces at both ends
                .toLowerCase(Locale.ROOT)               // ROOT, not the default locale: Turkish
                                                        // lowercases I to a dotless i, and your key
                                                        // would change with the server's locale
                .replaceAll("\\s+", " ");               // "New   York" -> "new york"
        return PREFIX + normalised;
    }
}
