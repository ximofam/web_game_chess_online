package com.ximofam.graduation_project.common.exceptions.http;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseHttpException {
    private static final String CODE = "FORBIDDEN";

    protected ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, CODE, message);
    }

    public ForbiddenException(String format, Object... args) {
        super(HttpStatus.FORBIDDEN, CODE, format, args);
    }
}