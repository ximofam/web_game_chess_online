package com.ximofam.graduation_project.forums.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.auth.services.UserCurrentService;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostSimpleResponse;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.entities.PostLike;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.events.PostModerationCompletedEvent;
import com.ximofam.graduation_project.forums.events.PostModerationEvent;
import com.ximofam.graduation_project.forums.mappers.PostMapper;
import com.ximofam.graduation_project.forums.repositories.PostImageRepository;
import com.ximofam.graduation_project.forums.repositories.PostLikeRepository;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.forums.repositories.projection.PostModerationProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostSimpleProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostViewProjection;
import com.ximofam.graduation_project.users.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostImageRepository postImageRepository;
    private final PostMapper postMapper;
    private final UserCurrentService userCurrentService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PostDetailResponse createPost(CreatePostRequest request) {
        User currentUser = userCurrentService.getReferenceUser();

        JsonNode doc = parseTiptapContent(request.getContent());

        Post post = postMapper.toPost(request);
        post.setAuthor(currentUser);
        post = postRepository.save(post);

        List<String> publicIds = extractImagePublicIds(doc);
        if (!publicIds.isEmpty()) {
            postImageRepository.attachToPost(post.getId(), currentUser.getId(), publicIds);
        }

        applicationEventPublisher.publishEvent(new PostModerationEvent(post.getId()));

        return postMapper.toPostDetailResponse(post);
    }

    @Transactional
    public void updateModerationPost(Long postId, PostStatus status, String reason) {
        PostModerationProjection post = postRepository.findModerationInfoById(postId)
                .orElseThrow(() -> new NotFoundException("PostId %d không tồn tại", postId));

        if (post.getStatus() != PostStatus.PENDING) {
            log.warn("PostId {} không còn ở trạng thái PENDING, bỏ qua.", postId);
            return;
        }

        postRepository.updateModerationStatus(postId, status, reason);

        applicationEventPublisher.publishEvent(new PostModerationCompletedEvent(
                post.getAuthorId(),
                post.getTitle(),
                status,
                reason
        ));
    }

    @Transactional
    public PostResponse viewPost(Long postId) {
        PostViewProjection projection = postRepository.findPostViewProjectionById(postId)
                .orElseThrow(() -> new NotFoundException("PostId %d không tồn tại hoặc chưa được duyệt", postId));

        postRepository.incrementViewCount(postId, 1L);

        PostResponse res = postMapper.toPostResponse(projection.getPost());
        res.setLikeCount(projection.getLikeCount());
        res.setCommentCount(projection.getCommentCount());

        Long currentUserId = userCurrentService.getCurrentUserIdOrNull();
        if (currentUserId != null) {
            boolean liked = postLikeRepository.findByUserIdAndPostId(currentUserId, postId)
                    .map(PostLike::isActive)
                    .orElse(false);
            res.setLiked(liked);
        }

        return res;
    }

    @Transactional
    public void likePost(Long postId, boolean isLike) {
        User currentUser = userCurrentService.getReferenceUser();

        if (!postRepository.existsByIdAndStatus(postId, PostStatus.APPROVED)) {
            throw new NotFoundException("PostId %d không tồn tại hoặc chưa được duyệt", postId);
        }

        postLikeRepository.findByUserIdAndPostId(currentUser.getId(), postId)
                .ifPresentOrElse(like -> {
                    like.setActive(isLike);
                }, () -> {
                    if (isLike) {
                        postLikeRepository.save(PostLike.of(currentUser, postRepository.getReferenceById(postId)));
                    }
                });
    }

    public Page<PostSimpleResponse> getPosts(Pageable pageable) {
        Page<PostSimpleProjection> projections = postRepository.findApprovedPosts(pageable);

        Long currentUserId = userCurrentService.getCurrentUserIdOrNull();
        Set<Long> likedPostIds;
        if (currentUserId != null) {
            List<Long> postIds = projections.stream().map(PostSimpleProjection::getId).toList();
            likedPostIds = postLikeRepository.findLikedPostIds(currentUserId, postIds);
        } else {
            likedPostIds = Set.of();
        }

        return projections.map(p -> postMapper.toPostSimpleResponse(p, likedPostIds.contains(p.getId())));
    }

    private JsonNode parseTiptapContent(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node == null || !"doc".equals(node.path("type").asText())) {
                throw new BadRequestException("Nội dung không đúng định dạng Tiptap");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Nội dung không phải JSON hợp lệ");
        }
    }

    // ponytail: recursive DFS over ProseMirror tree — O(n) nodes, good enough
    // unless posts contain thousands of nodes; upgrade path: iterative stack.
    private List<String> extractImagePublicIds(JsonNode node) {
        List<String> ids = new ArrayList<>();
        collectImagePublicIds(node, ids);
        return ids.stream().distinct().toList();
    }

    private void collectImagePublicIds(JsonNode node, List<String> ids) {
        if ("image".equals(node.path("type").asText())) {
            String publicId = node.path("attrs").path("data-public-id").asText(null);
            if (publicId != null && !publicId.isBlank()) {
                ids.add(publicId);
            }
        }
        JsonNode content = node.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode child : content) {
                collectImagePublicIds(child, ids);
            }
        }
    }
}

