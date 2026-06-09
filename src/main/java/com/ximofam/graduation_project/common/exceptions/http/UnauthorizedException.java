package com.ximofam.graduation_project.common.exceptions.http;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseHttpException {
    private static final String CODE = "UNAUTHORIZED";

    protected UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, CODE, message);
    }

    public UnauthorizedException(String format, Object... args) {
        super(HttpStatus.UNAUTHORIZED, CODE, format, args);
    }
}