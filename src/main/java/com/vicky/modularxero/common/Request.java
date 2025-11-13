package com.vicky.modularxero.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="type")
public class Request<T> {
    public MessageType type;
    public String moduleName;
    public T payload;
    public String id;
    public String moduleAddress;
    public Request() {}
    public Request(MessageType type, T payload) {
        this.type = type;
        this.payload = payload;
    }

    public void setId(String id) {
        this.id = id;
    }
    public void setModuleAddress(String moduleAddress) {
        this.moduleAddress = moduleAddress;
    }
}