package com.ximofam.graduation_project.common.exceptions.http;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BaseHttpException {
    private static final String CODE = "NOT_FOUND";

    protected NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, CODE, message);
    }

    public NotFoundException(String format, Object... args) {
        super(HttpStatus.NOT_FOUND, CODE, format, args);
    }
}