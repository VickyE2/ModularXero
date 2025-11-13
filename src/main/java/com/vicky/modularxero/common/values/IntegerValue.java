package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IntegerValue extends MessageValue<Integer> {
    @JsonCreator
    public IntegerValue(
            @JsonProperty("value")Integer value) { super(value); }
}