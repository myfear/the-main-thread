package dev.themainthread.catalog;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "catalog.instance")
public interface CatalogInstanceConfig {

    String id();

    String color();
}
