package com.catalogapi.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProductView.class, name = "product"),
        @JsonSubTypes.Type(value = BundleView.class, name = "bundle")
})
public sealed interface CatalogPayload permits ProductView, BundleView {
}
