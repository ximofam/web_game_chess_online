package com.ximofam.graduation_project.forums.controllers;

import com.ximofam.graduation_project.common.helpers.dtos.ApiResponse;
import com.ximofam.graduation_project.forums.dtos.request.CreateCommentRequest;
import com.ximofam.graduation_project.forums.dtos.response.CommentResponse;
import com.ximofam.graduation_project.forums.services.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class ApiCommentController {
    private final CommentService commentService;

    @GetMapping("/{id}/replies")
    public ResponseEntity<Page<CommentResponse>> getReplies(
            @PathVariable Long id,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(commentService.getReplyComments(id, sortBy, pageable));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(@RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(request));
    }

    @PostMapping("/{commentId}/likes")
    public ResponseEntity<ApiResponse> likeComment(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "true") boolean isLike) {

        commentService.likeComment(commentId, isLike);
        ApiResponse res = ApiResponse.builder()
                .message((isLike ? "Liked" : "Unliked") + " bình luận thành công")
                .build();

        return ResponseEntity.ok(res);
    }
}
