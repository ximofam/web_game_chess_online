package com.ximofam.graduation_project.integration.forums;

import com.ximofam.graduation_project.forums.entities.Comment;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.repositories.CommentRepository;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiCommentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    private Post approvedPost;
    private Comment rootComment;

    @BeforeEach
    void setUp() {
        approvedPost = new Post();
        approvedPost.setTitle("Sample Post for Comments");
        approvedPost.setContent("This is a sample post content");
        approvedPost.setAuthor(validUser);
        approvedPost.setStatus(PostStatus.APPROVED);
        approvedPost = postRepository.save(approvedPost);

        rootComment = new Comment();
        rootComment.setPost(approvedPost);
        rootComment.setAuthor(validUser);
        rootComment.setContent("This is a root comment");
        rootComment = commentRepository.save(rootComment);
    }

    @Test
    void shouldCreateComment_WhenValidRequestAndAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createCommentPayload = """
                {
                    "postId": %d,
                    "content": "This is a test comment"
                }
                """.formatted(approvedPost.getId());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createCommentPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("This is a test comment"))
                .andExpect(jsonPath("$.author.id").value(validUser.getId()));
    }

    @Test
    void shouldCreateReply_WhenValidRequestAndAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createReplyPayload = """
                {
                    "postId": %d,
                    "content": "This is a reply comment",
                    "commentParentId": %d
                }
                """.formatted(approvedPost.getId(), rootComment.getId());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createReplyPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("This is a reply comment"));
    }

    @Test
    void shouldReturn401_WhenCreateCommentWithoutAuthentication() throws Exception {
        String createCommentPayload = """
                {
                    "postId": %d,
                    "content": "This is a test comment"
                }
                """.formatted(approvedPost.getId());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateCommentWithBlankContent() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createCommentPayload = """
                {
                    "postId": %d,
                    "content": ""
                }
                """.formatted(approvedPost.getId());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createCommentPayload))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn404_WhenCreateCommentWithoutPostId() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createCommentPayload = """
                {
                    "content": "This is a test comment"
                }
                """;

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createCommentPayload))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetReplies_WhenCommentExists() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/comments/{id}/replies", rootComment.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldGetReplies_WithValidSortByCreatedAt() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/comments/{id}/replies", rootComment.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("sortBy", "createdAt")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturn400_WhenGetRepliesWithInvalidSortBy() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/comments/{id}/replies", rootComment.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("sortBy", "id")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404_WhenGetRepliesForNonExistentComment() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/comments/{id}/replies", 9999L)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldLikeComment_WhenAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/comments/{commentId}/likes", rootComment.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Liked bình luận thành công"));
    }

    @Test
    void shouldUnlikeComment_WhenAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/comments/{commentId}/likes", rootComment.getId())
                        .param("isLike", "false")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unliked bình luận thành công"));
    }

    @Test
    void shouldReturn401_WhenLikeCommentWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/comments/{commentId}/likes", rootComment.getId())
                        .param("isLike", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404_WhenLikeNonExistentComment() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/comments/{commentId}/likes", 9999L)
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isNotFound());
    }
}
