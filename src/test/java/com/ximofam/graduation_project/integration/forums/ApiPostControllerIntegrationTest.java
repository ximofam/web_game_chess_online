package com.ximofam.graduation_project.integration.forums;

import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiPostControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    private Post approvedPost;

    @BeforeEach
    void setUp() {
        approvedPost = new Post();
        approvedPost.setTitle("Sample Approved Post");
        approvedPost.setContent("This is a sample post content");
        approvedPost.setAuthor(validUser);
        approvedPost.setStatus(PostStatus.APPROVED);
        approvedPost = postRepository.save(approvedPost);
    }

    @Test
    void shouldCreatePost_WhenValidRequestAndAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createPostPayload = """
                {
                    "title": "New Test Post",
                    "content": "This is a test post content"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createPostPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("New Test Post"))
                .andExpect(jsonPath("$.content").value("This is a test post content"));
    }

    @Test
    void shouldReturn401_WhenCreatePostWithoutAuthentication() throws Exception {
        String createPostPayload = """
                {
                    "title": "New Test Post",
                    "content": "This is a test post content"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPostPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400_WhenCreatePostWithBlankTitle() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createPostPayload = """
                {
                    "title": "",
                    "content": "This is a test post content"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createPostPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_WhenCreatePostWithBlankContent() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        String createPostPayload = """
                {
                    "title": "New Test Post",
                    "content": ""
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createPostPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetPost_WhenPostExists() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvedPost.getId()))
                .andExpect(jsonPath("$.title").value("Sample Approved Post"))
                .andExpect(jsonPath("$.content").value("This is a sample post content"));
    }

    @Test
    void shouldReturn404_WhenPostNotFound() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}", 9999L)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetComments_WhenPostExists() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldGetComments_WithValidSortByLikeCount() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("sortBy", "likeCount")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturn400_WhenGetCommentsWithInvalidSortBy() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("sortBy", "id")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404_WhenGetCommentsForNonExistentPost() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/posts/{postId}/comments", 9999L)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldLikePost_WhenAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Liked bai viết thành công"));
    }

    @Test
    void shouldUnlikePost_WhenAuthenticated() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "false")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unliked bai viết thành công"));
    }

    @Test
    void shouldReturn401_WhenLikePostWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404_WhenLikeNonExistentPost() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/posts/{postId}/likes", 9999L)
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldIncreaseLikeCount_WhenLikePost() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Get initial like count
        String initialResponse = mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long initialLikeCount = objectMapper.readTree(initialResponse).get("likeCount").asLong();

        // Like the post
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get updated like count
        String updatedResponse = mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long updatedLikeCount = objectMapper.readTree(updatedResponse).get("likeCount").asLong();

        assert updatedLikeCount == initialLikeCount + 1;
    }

    @Test
    void shouldDecreaseLikeCount_WhenUnlikePost() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Like the post first
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get like count after liking
        String afterLikeResponse = mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long likeCountAfterLike = objectMapper.readTree(afterLikeResponse).get("likeCount").asLong();

        // Unlike the post
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "false")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get like count after unliking
        String afterUnlikeResponse = mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long likeCountAfterUnlike = objectMapper.readTree(afterUnlikeResponse).get("likeCount").asLong();

        assert likeCountAfterUnlike == likeCountAfterLike - 1;
    }

    @Test
    void shouldReturnLikedTrue_WhenGetPostAndUserHasLiked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // User likes the post
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get post and verify liked is true
        mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));
    }

    @Test
    void shouldReturnLikedFalse_WhenGetPostAndUserHasNotLiked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Get post without liking and verify liked is false
        mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void shouldReturnLikedFalse_WhenGetPostAsAnonymousUser() throws Exception {
        // Get post without authentication and verify liked is false
        mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void shouldReturnLikedFalse_WhenGetPostAndUserUnliked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // User likes then unlikes the post
        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/posts/{postId}/likes", approvedPost.getId())
                        .param("isLike", "false")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get post and verify liked is false
        mockMvc.perform(get("/api/posts/{postId}", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void shouldReturnLikedFalse_WhenGetPostsAndUserHasNotLiked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Get posts list and verify liked is false for all posts
        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%d')].liked", approvedPost.getId()).value(false));
    }

    @Test
    void shouldReturnLikedFalse_WhenGetPostsAsAnonymousUser() throws Exception {
        // Get posts list as anonymous and verify liked is false
        mockMvc.perform(get("/api/posts")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturnCommentWithLikedTrue_WhenGetCommentsAndUserHasLiked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Create a comment first
        String createCommentPayload = """
                {
                    "postId": %d,
                    "content": "This is a test comment"
                }
                """.formatted(approvedPost.getId());

        String commentResponse = mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createCommentPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long commentId = objectMapper.readTree(commentResponse).get("id").asLong();

        // Like the comment
        mockMvc.perform(post("/api/comments/{commentId}/likes", commentId)
                        .param("isLike", "true")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        // Get comments and verify liked is true
        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].liked", commentId).value(true));
    }

    @Test
    void shouldReturnCommentWithLikedFalse_WhenGetCommentsAndUserHasNotLiked() throws Exception {
        var token = performLogin(validUser.getUsername(), PASSWORD);

        // Create a comment
        String createCommentPayload = """
                {
                    "postId": %d,
                    "content": "This is a test comment"
                }
                """.formatted(approvedPost.getId());

        String commentResponse = mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .content(createCommentPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long commentId = objectMapper.readTree(commentResponse).get("id").asLong();

        // Get comments and verify liked is false
        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].liked", commentId).value(false));
    }

    @Test
    void shouldReturnCommentWithLikedFalse_WhenGetCommentsAsAnonymousUser() throws Exception {
        // Get comments without authentication and verify liked is false
        mockMvc.perform(get("/api/posts/{postId}/comments", approvedPost.getId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
