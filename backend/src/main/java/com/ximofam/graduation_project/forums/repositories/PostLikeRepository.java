package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    Optional<PostLike> findByUserIdAndPostId(UUID userId, UUID postId);

    @Query("""
            SELECT l.post.id
            FROM PostLike l
            WHERE l.user.id = :userId AND l.isActive = true AND l.post.id IN :postIds
            """)
    Set<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);
}
