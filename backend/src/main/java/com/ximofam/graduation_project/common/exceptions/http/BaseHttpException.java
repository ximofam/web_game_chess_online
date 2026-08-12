package com.ximofam.graduation_project.common.exceptions.http;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseHttpException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    protected BaseHttpException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected BaseHttpException(HttpStatus status, String code, String format, Object... args) {
        super(String.format(format, args));
        this.status = status;
        this.code = code;
    }
}