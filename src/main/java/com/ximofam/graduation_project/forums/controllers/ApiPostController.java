package com.ximofam.graduation_project.forums.controllers;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.services.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class ApiPostController {
    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(@RequestBody @Valid CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }
}
