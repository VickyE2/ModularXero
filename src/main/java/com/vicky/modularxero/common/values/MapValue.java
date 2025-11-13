package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Wraps any Map of MessageValue (or raw) entries.
 */
public class MapValue<V> extends MessageValue<Map<String, V>> {
    @JsonCreator
    public MapValue(
            @JsonProperty("value") Map<String, V> map) {
        super(map);
    }
}
