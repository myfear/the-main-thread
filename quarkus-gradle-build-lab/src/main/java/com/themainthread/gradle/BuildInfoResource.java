package com.themainthread.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/build-info")
@Produces(MediaType.APPLICATION_JSON)
public class BuildInfoResource {

    private final BuildInfo buildInfo = loadBuildInfo();

    @GET
    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    private static BuildInfo loadBuildInfo() {
        Properties properties = new Properties();

        try (InputStream input = BuildInfoResource.class.getResourceAsStream("/build-info.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing generated build-info.properties");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read generated build-info.properties", exception);
        }

        return new BuildInfo(
                requiredProperty(properties, "application.name"),
                requiredProperty(properties, "application.version"));
    }

    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing build property: " + key);
        }
        return value;
    }

    public record BuildInfo(String name, String version) {
    }
}
