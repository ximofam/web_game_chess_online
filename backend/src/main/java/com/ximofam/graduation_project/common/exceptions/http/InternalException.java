package com.ximofam.graduation_project.common.exceptions.http;

import org.springframework.http.HttpStatus;

public class InternalException extends BaseHttpException {
    private static final String CODE = "INTERNAL_SERVER_ERROR";

    protected InternalException(String code, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }

    public InternalException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, CODE, message);
    }

    public InternalException(String format, Object... args) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, CODE, format, args);
    }
}