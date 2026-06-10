package com.ximofam.graduation_project.common.helpers.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void deleteMany(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return;

        try {
            Map<?, ?> result = cloudinary.api().deleteResources(publicIds, ObjectUtils.emptyMap());

            Map<?, ?> deleted = (Map<?, ?>) result.get("deleted");
            List<String> failed = deleted.entrySet().stream()
                    .filter(e -> !"deleted".equals(e.getValue()))
                    .map(e -> (String) e.getKey())
                    .toList();

            if (!failed.isEmpty()) {
                throw new RuntimeException("Xóa thất bại các ảnh: " + failed);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getUrl(String publicId) {
        return cloudinary.url()
                .secure(true)
                .generate(publicId);
    }


    public String getUrl(String publicId, Transformation transformation) {
        return cloudinary.url()
                .secure(true)
                .transformation(transformation)
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