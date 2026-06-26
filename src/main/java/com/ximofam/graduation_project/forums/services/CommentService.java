package com.ximofam.graduation_project.forums.services;

import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.forums.dtos.request.CreateCommentRequest;
import com.ximofam.graduation_project.forums.dtos.response.CommentResponse;
import com.ximofam.graduation_project.forums.entities.Comment;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.mappers.CommentMapper;
import com.ximofam.graduation_project.forums.repositories.CommentRepository;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.forums.repositories.projection.CommentReplyCountProjection;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.services.UserCurrentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserCurrentService userCurrentService;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentResponse createComment(CreateCommentRequest request) {
        User currentUser = userCurrentService.getCurrentUser();

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new NotFoundException("PostId %d không tồn tại", request.getPostId()));

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setAuthor(currentUser);
        comment.setPost(post);

        if (request.getCommentParentId() != null) {
            Comment parent = commentRepository.findById(request.getCommentParentId())
                    .orElseThrow(() -> new NotFoundException(
                            "CommentParentId %d không tồn tại",
                            request.getCommentParentId()
                    ));

            if (parent.isReply()) {
                throw new BadRequestException("Chỉ được reply tối đa 2 cấp");
            }

            if (!parent.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("Comment cha không thuộc bài viết này");
            }

            comment.setParent(parent);
        }

        comment = commentRepository.save(comment);
        return commentMapper.toCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new NotFoundException("PostId %d không tồn tại", postId);
        }

        Page<Comment> comments = commentRepository.findByPostIdAndParentIsNull(postId, pageable);
        return toCommentResponsePage(comments);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getReplyComments(Long commentId, Pageable pageable) {
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("CommentId %d không tồn tại", commentId);
        }

        Page<Comment> comments = commentRepository.findByParentId(commentId, pageable);
        return toCommentResponsePage(comments);
    }

    private Page<CommentResponse> toCommentResponsePage(Page<Comment> comments) {
        Map<Long, Integer> replyCounts = getReplyCounts(comments);

        return comments.map(comment -> {
            CommentResponse response = commentMapper.toCommentResponse(comment);
            response.setReplyCount(replyCounts.getOrDefault(comment.getId(), 0));
            return response;
        });
    }

    private Map<Long, Integer> getReplyCounts(Page<Comment> comments) {
        if (comments.isEmpty()) {
            return Map.of();
        }

        return commentRepository.countRepliesByCommentIdIn(
                        comments.stream().map(Comment::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        CommentReplyCountProjection::getCommentId,
                        projection -> Math.toIntExact(projection.getReplyCount())
                ));
    }
}
