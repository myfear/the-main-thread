package com.example.flags;

import java.util.Map;

import io.quarkiverse.flags.hibernate.common.FlagDefinition;
import io.quarkiverse.flags.hibernate.common.FlagFeature;
import io.quarkiverse.flags.hibernate.common.FlagMetadata;
import io.quarkiverse.flags.hibernate.common.FlagValue;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;

@FlagDefinition
@Entity
public class FeatureFlag extends PanacheEntity {

    @FlagFeature
    public String feature;

    @FlagValue
    public String value;

    @FlagMetadata
    @ElementCollection
    @CollectionTable
    public Map<String, String> metadata;

    public String description;
}