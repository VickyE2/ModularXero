package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DoubleValue extends MessageValue<Double> {
    @JsonCreator
    public DoubleValue(
            @JsonProperty("value")Double value) { super(value); }
}
