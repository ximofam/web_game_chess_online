package com.ximofam.graduation_project.forums.services;

import com.ximofam.graduation_project.forums.dtos.request.CreatePostRequest;
import com.ximofam.graduation_project.forums.dtos.response.PostDetailResponse;
import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.mappers.PostMapper;
import com.ximofam.graduation_project.forums.repositories.PostRepository;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.services.UserCurrentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final UserCurrentService userCurrentService;

    @Transactional
    public PostDetailResponse createPost(CreatePostRequest request) {
        User currentUser = userCurrentService.getCurrentUser();

        Post post = postMapper.toPost(request);
        post.setAuthor(currentUser);
        post = postRepository.save(post);

        return postMapper.toPostDetailResponse(post);
    }
}
