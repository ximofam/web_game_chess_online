package com.ximofam.graduation_project.auth.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TokenType {
    REFRESH,
    ACCESS,
    GUEST;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
