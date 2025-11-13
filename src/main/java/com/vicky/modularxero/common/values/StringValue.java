package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StringValue extends MessageValue<String> {
    @JsonCreator
    public StringValue(@JsonProperty("value") String value) { super(value); }
}
