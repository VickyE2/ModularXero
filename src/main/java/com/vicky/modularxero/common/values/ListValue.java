package com.vicky.modularxero.common.values;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wraps any List of MessageValue (or raw) elements.
 */
public class ListValue<E> extends MessageValue<List<E>> {
    @JsonCreator
    public ListValue(
            @JsonProperty("value") List<E> list) {
        super(list);
    }
}
