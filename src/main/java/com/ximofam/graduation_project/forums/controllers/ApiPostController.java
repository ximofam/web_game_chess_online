package com.ximofam.graduation_project.forums.controllers;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.ModerationResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.services.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class ApiPostController {
    private final PostService postService;
    private final ChatClient chatClient;
    @Value("classpath:prompts/check_post.st")
    private Resource checkPostPrompt;

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(@RequestBody @Valid CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @GetMapping("/test")
    public ResponseEntity<ModerationResponse> testChat(@RequestBody String userPost) {
        return ResponseEntity.ok(chatClient.prompt()
                .system(checkPostPrompt)
                .user(u -> u.text("Bài đăng cần kiểm duyệt: '''{content}'''")
                        .param("content", userPost))
                .call()
                .entity(ModerationResponse.class));
    }
}
