package com.ibm.developer.shieldstral.policy;

import java.util.Arrays;

import jakarta.ws.rs.NotFoundException;

public enum PolicySurface {
    PUBLIC_SUPPORT("public"),
    SECURITY_RESEARCH("security");

    private final String path;

    PolicySurface(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static PolicySurface fromPath(String path) {
        return Arrays.stream(values())
                .filter(surface -> surface.path.equals(path))
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }
}
