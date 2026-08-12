package com.ximofam.graduation_project.common.helpers.dtos;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CloudinaryUploadResult {
    private String publicId;
    private String secureUrl;
    private String format;
    private Integer width;
    private Integer height;
}