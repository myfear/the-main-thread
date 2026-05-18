package com.catalogapi.jackson;

import com.catalogapi.json.Money;
import com.catalogapi.json.MoneySerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class CatalogJacksonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Money.class, new MoneySerializer());
        mapper.registerModule(module);
    }
}
