package com.ximofam.graduation_project.common.helpers.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpErrorResponse {
    private String code;
    private String message;
    private Map<String, Object> errors;

    public HttpErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}