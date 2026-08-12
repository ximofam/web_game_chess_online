package com.ximofam.graduation_project.forums.controllers;

import com.ximofam.graduation_project.common.helpers.dtos.CloudinaryUploadResult;
import com.ximofam.graduation_project.forums.services.PostImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/post-images")
@RequiredArgsConstructor
public class ApiPostImageController {
    private final PostImageService postImageService;

    @PreAuthorize("!hasRole('GUEST')")
    @PostMapping
    public ResponseEntity<Map<String, String>> uploadPostImage(@RequestParam("file") MultipartFile file) {
        CloudinaryUploadResult res = postImageService.uploadPostImage(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "url", res.getSecureUrl(),
                        "publicId", res.getPublicId()));
    }

    @PreAuthorize("!hasRole('GUEST')")
    @DeleteMapping
    public ResponseEntity<Void> deletePostImage(@RequestParam("publicId") String publicId) {
        postImageService.deletePostImage(publicId);
        return ResponseEntity.noContent().build();
    }
}
