package com.ximofam.graduation_project.common.ws;

public record WsEvent<T>(String type, T data) {
}