package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wraps a long timestamp (epoch millis). */
public class TimestampValue extends MessageValue<Long> {
    @JsonCreator
    public TimestampValue(@JsonProperty("value") Long value) { super(value); }
}
