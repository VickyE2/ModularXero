package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EnumValue<T extends Enum<T>> extends MessageValue<Enum<T>> {
    @JsonProperty
    public final String enumClassName;

    @JsonCreator
    public EnumValue(@JsonProperty("value") Enum<T> value) {
        super(value);
        this.enumClassName = value.getDeclaringClass().getName();
    }
}