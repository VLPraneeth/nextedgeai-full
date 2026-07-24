package com.syncari.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.syncari.core.pipeline.FilterFailedResult;

import java.io.IOException;

public class FilterFailedResultSerializer extends JsonSerializer<FilterFailedResult> {
    @Override
    public void serialize(FilterFailedResult value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("filterResponse", "filterFailed");
        gen.writeStringField("originalValue", getValue(value));
        gen.writeEndObject();
    }

    private String getValue(FilterFailedResult value) {
        if (value.hasInvalidResults()) {
            return "no_filter_value";
        }
        return value.getValue() == null ? null : value.getValue().toString();
    }

}
