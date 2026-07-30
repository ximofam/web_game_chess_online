package com.ximofam.graduation_project.common.helpers.dtos.ws;

public record WsEvent<T>(String type, T data) {
}