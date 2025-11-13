package com.vicky.modularxero.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.vicky.modularxero.common.values.MessageValue;

import java.io.IOException;

public class MessageValueSerializer extends JsonSerializer<MessageValue<?>> {
    @Override
    public void serialize(MessageValue<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("valueType", value.getClass().getSimpleName());
        gen.writeFieldName("value");
        gen.writeObject(value.get());
        gen.writeEndObject();
    }
}
