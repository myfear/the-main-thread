package com.themainthread.exoplanets;

import java.util.List;

public record Catalogue(String retrievedAt, String query, List<Planet> planets) {
    public Catalogue {
        planets = List.copyOf(planets);
    }
}
