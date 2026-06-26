package com.ximofam.graduation_project.forums.controllers;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.CommentResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.services.CommentService;
import com.ximofam.graduation_project.forums.services.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class ApiPostController {
    private final PostService postService;
    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(@RequestBody @Valid CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable Long id,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(commentService.getComments(id, pageable));
    }
}
