package com.ximofam.graduation_project.common.helpers.dtos;

public record WsEvent<T>(
        String type,
        T data
) {
    public static <T> WsEvent<T> of(String type, T payload) {
        return new WsEvent<>(type, payload);
    }
}