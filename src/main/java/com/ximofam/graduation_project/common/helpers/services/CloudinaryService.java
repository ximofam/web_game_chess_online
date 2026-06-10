package com.ximofam.graduation_project.common.helpers.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult upload(MultipartFile file, Map<?, ?> options) throws IOException {
        validateImageFile(file);

        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);

        return CloudinaryUploadResult.builder()
                .publicId((String) result.get("public_id"))
                .secureUrl((String) result.get("secure_url"))
                .format((String) result.get("format"))
                .width((Integer) result.get("width"))
                .height((Integer) result.get("height"))
                .build();
    }

    public void delete(String publicId) throws IOException {
        Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

        if (!"ok".equals(result.get("result"))) {
            throw new RuntimeException("Xóa ảnh thất bại, publicId: " + publicId);
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