package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wraps a Boolean. */
public class BooleanValue extends MessageValue<Boolean> {
    @JsonCreator
    public BooleanValue(
            @JsonProperty("value")Boolean value) { super(value); }
}

