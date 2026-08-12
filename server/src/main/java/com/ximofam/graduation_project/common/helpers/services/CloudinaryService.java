package com.ximofam.graduation_project.common.helpers.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult upload(MultipartFile file, Map<?, ?> options) {
        validateImageFile(file);

        Map<?, ?> result = null;
        try {
            result = cloudinary.uploader().upload(file.getBytes(), options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return CloudinaryUploadResult.builder()
                .publicId((String) result.get("public_id"))
                .secureUrl((String) result.get("secure_url"))
                .format((String) result.get("format"))
                .width((Integer) result.get("width"))
                .height((Integer) result.get("height"))
                .build();
    }

    public void delete(String publicId) {
        Map<?, ?> result = null;
        try {
            result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!"ok".equals(result.get("result"))) {
            throw new RuntimeException("Xóa ảnh thất bại, publicId: " + publicId);
        }
    }


    public void deleteAll(List<String> publicIds) {
        if (publicIds.isEmpty()) {
            return;
        }

        ApiResponse response = null;
        try {
            response = cloudinary.api().deleteResources(
                    publicIds,
                    ObjectUtils.emptyMap()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        @SuppressWarnings("unchecked")
        var deleted = (Map<String, String>) response.get("deleted");

        for (String publicId : publicIds) {
            if (!"deleted".equals(deleted.get(publicId))) {
                throw new RuntimeException(
                        "Delete image failed: " + publicId
                );
            }
        }
    }

    @Async
    public void deleteAsync(String publicId) {
        try {
            delete(publicId);
            log.info("Đã xoá thành công ảnh {}", publicId);
        } catch (Exception ex) {
            log.error("Không thể xóa ảnh {}", publicId, ex);
        }
    }

    public String getUrl(String publicId) {
        return cloudinary.url()
                .secure(true)
                .generate(publicId);
    }


    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File không được rỗng");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Chỉ chấp nhận file ảnh");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("File không được vượt quá 5MB");
        }
    }
}