package com.ximofam.graduation_project.forums.controllers;

import java.util.UUID;

import com.ximofam.graduation_project.common.helpers.dtos.ApiResponse;
import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.CommentResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostSimpleResponse;
import com.ximofam.graduation_project.forums.services.CommentService;
import com.ximofam.graduation_project.forums.services.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class ApiPostController {
    private final PostService postService;
    private final CommentService commentService;

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.viewPost(postId));
    }

    @PreAuthorize("!hasRole('GUEST')")
    @GetMapping("/{postId}/my")
    public ResponseEntity<PostDetailResponse> getMyPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.getMyPost(postId));
    }

    @PreAuthorize("!hasRole('GUEST')")
    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(@RequestBody @Valid CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(commentService.getComments(postId, sortBy, pageable));
    }

    @PreAuthorize("!hasRole('GUEST')")
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse> likePost(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "true") boolean isLike) {

        postService.likePost(postId, isLike);
        ApiResponse res = ApiResponse.builder()
                .message((isLike ? "Liked" : "Unliked") + " bai viết thành công")
                .build();

        return ResponseEntity.ok(res);
    }

    @GetMapping
    public ResponseEntity<Page<PostSimpleResponse>> getPosts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "newest") String sortBy,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(postService.getPosts(search, sortBy, mine, status, pageable));
    }

    @PreAuthorize("!hasRole('GUEST')")
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse> deletePost(@PathVariable UUID postId) {
        postService.deleteMyPost(postId);
        return ResponseEntity.ok(ApiResponse.builder().message("Xóa bài viết thành công").build());
    }
}
