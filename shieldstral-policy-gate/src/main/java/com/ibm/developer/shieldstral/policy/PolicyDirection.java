package com.ibm.developer.shieldstral.policy;

import java.util.Arrays;

import jakarta.ws.rs.NotFoundException;

public enum PolicyDirection {
    INPUT("input"),
    OUTPUT("output");

    private final String path;

    PolicyDirection(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static PolicyDirection fromPath(String path) {
        return Arrays.stream(values())
                .filter(direction -> direction.path.equals(path))
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }
}
