package com.vicky.modularxero.common;

import com.vicky.modularxero.common.values.MapValue;
import com.vicky.modularxero.common.values.MessageValue;
import com.vicky.modularxero.common.values.StringValue;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="type")
public class Response<T> {
    public MessageType type;
    public T payload;
    public String id;
    public ResponseStatus status;

    public Response(MessageType type, T payload, ResponseStatus status) {
        this.type = type;
        this.payload = payload;
        this.status = status;
    }

    public Response() { }

    public static @NotNull Response<MapValue<MessageValue<?>>> error(@NotNull String string) {
        var resp = new Response<MapValue<MessageValue<?>>>();
        resp.status = ResponseStatus.FAILED;
        resp.payload = new MapValue<>(Map.of(
                "reason", new StringValue(string)
        ));
        return resp;
    }

    public enum ResponseStatus {
        OK,
        FAILED,
        PENDING
    }
}
