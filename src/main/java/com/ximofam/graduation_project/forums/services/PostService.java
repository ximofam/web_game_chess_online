package com.ximofam.graduation_project.forums.services;

import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.dtos.response.PostResponse;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.events.PostModerationCompletedEvent;
import com.ximofam.graduation_project.forums.events.PostModerationEvent;
import com.ximofam.graduation_project.forums.mappers.PostMapper;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.forums.repositories.projection.PostModerationProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostViewProjection;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.services.UserCurrentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final UserCurrentService userCurrentService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public PostDetailResponse createPost(CreatePostRequest request) {
        User currentUser = userCurrentService.getCurrentUser();

        Post post = postMapper.toPost(request);
        post.setAuthor(currentUser);
        post = postRepository.save(post);

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

        return res;
    }
}
