package com.ximofam.graduation_project.common.exceptions.http;

import org.springframework.http.HttpStatus;

public class BadRequestException extends BaseHttpException {
    private final static String CODE = "BAD_REQUEST";

    protected BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, CODE, message);
    }

    public BadRequestException(String format, Object... args) {
        super(HttpStatus.BAD_REQUEST, CODE, format, args);
    }
}