package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.vicky.modularxero.common.MessageValueSerializer;

import java.util.Objects;

/**
 * Base wrapper for any value sent over the wire.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "valueType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringValue.class, name = "StringValue"),
        @JsonSubTypes.Type(value = BooleanValue.class, name = "BooleanValue"),
        @JsonSubTypes.Type(value = MapValue.class, name = "MapValue"),
        @JsonSubTypes.Type(value = ListValue.class, name = "ListValue"),
        @JsonSubTypes.Type(value = IntegerValue.class, name = "IntegerValue"),
        @JsonSubTypes.Type(value = FloatValue.class, name = "FloatValue"),
        @JsonSubTypes.Type(value = DoubleValue.class, name = "DoubleValue"),
        @JsonSubTypes.Type(value = TimestampValue.class, name = "TimestampValue"),
        @JsonSubTypes.Type(value = CurrencyValue.class, name = "CurrencyValue"),
        @JsonSubTypes.Type(value = EnumValue.class, name = "EnumValue")
})
public abstract class MessageValue<T> {
    private final T value;

    protected MessageValue(T value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public T get() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
