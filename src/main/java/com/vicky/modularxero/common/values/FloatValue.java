package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FloatValue extends MessageValue<Float> {
    @JsonCreator
    public FloatValue(
            @JsonProperty("value")Float value) { super(value); }
}
