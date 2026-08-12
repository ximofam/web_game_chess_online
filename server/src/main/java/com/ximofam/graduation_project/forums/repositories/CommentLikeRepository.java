package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {
    Optional<CommentLike> findByUserIdAndCommentId(UUID userId, UUID commentId);

    @Query("""
            SELECT cl.comment.id
            FROM CommentLike cl
            WHERE cl.user.id = :userId AND cl.isActive = true AND cl.comment.id IN :commentIds
            """)
    Set<UUID> findLikedCommentIdsByUserId(@Param("userId") UUID userId, @Param("commentIds") List<UUID> commentIds);
}
