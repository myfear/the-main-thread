package com.catalogapi.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class MoneySerializer extends StdSerializer<Money> {

    public MoneySerializer() {
        super(Money.class);
    }

    @Override
    public void serialize(Money value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("currency", value.currency());
        generator.writeNumberField("amountMinor", value.amountMinor());
        generator.writeStringField("display", value.currency() + " " + formatMinor(value.amountMinor()));
        generator.writeEndObject();
    }

    private static String formatMinor(long amountMinor) {
        long major = amountMinor / 100;
        long minor = Math.abs(amountMinor % 100);
        return major + "." + (minor < 10 ? "0" : "") + minor;
    }
}
