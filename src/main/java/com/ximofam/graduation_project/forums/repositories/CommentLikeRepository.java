package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByUserIdAndCommentId(Long userId, Long commentId);

    @Query("""
            SELECT cl.comment.id
            FROM CommentLike cl
            WHERE cl.user.id = :userId AND cl.isActive = true AND cl.comment.id IN :commentIds
            """)
    Set<Long> findLikedCommentIdsByUserId(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
}
