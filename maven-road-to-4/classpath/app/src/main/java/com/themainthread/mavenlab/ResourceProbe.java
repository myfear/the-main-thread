package com.themainthread.mavenlab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceProbe {
    private ResourceProbe() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass the report file path");
        }
        try (InputStream resource = ResourceProbe.class.getResourceAsStream("/collision.properties")) {
            if (resource == null) {
                throw new IllegalStateException("collision.properties is missing");
            }
            Files.writeString(Path.of(args[0]), new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
