package com.ximofam.graduation_project.forums.services;

import com.cloudinary.utils.ObjectUtils;
import com.ximofam.graduation_project.auth.services.UserCurrentService;
import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.forums.entities.PostImage;
import com.ximofam.graduation_project.forums.entities.enums.ImageStatus;
import com.ximofam.graduation_project.forums.repositories.PostImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PostImageService {
    private final CloudinaryService cloudinaryService;
    private final PostImageRepository postImageRepository;
    private final UserCurrentService userCurrentService;

    @Transactional
    public CloudinaryUploadResult uploadPostImage(MultipartFile file) {
        Long userId = userCurrentService.getCurrentUserId();

        CloudinaryUploadResult res = cloudinaryService.upload(file, ObjectUtils.asMap(
                "folder", "posts/images",
                "resource_type", "image"
        ));

        PostImage postImage = new PostImage();
        postImage.setUploaderId(userId);
        postImage.setUrl(res.getSecureUrl());
        postImage.setPublicId(res.getPublicId());
        postImage.setStatus(ImageStatus.ORPHAN);
        postImageRepository.save(postImage);

        return res;
    }


    @Transactional
    public void deletePostImage(String publicId) {
        cloudinaryService.delete(publicId);
        postImageRepository.deleteByPublicId(publicId);
    }
}
