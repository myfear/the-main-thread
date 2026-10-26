package com.themainthread.vex;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

final class SchemaChecks {
    static void validate(String name, JsonNode value) throws IOException {
        try (var stream = SchemaChecks.class.getResourceAsStream("/schemas/" + name)) {
            if (stream == null) throw new IOException("Missing bundled schema: " + name);
            var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(stream);
            var errors = schema.validate(value);
            if (!errors.isEmpty()) throw new CaseException(2, "Schema validation: " + errors);
        }
    }
}
