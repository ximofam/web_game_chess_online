package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.repositories.projection.PostContentProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostModerationProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostSimpleProjection;
import com.ximofam.graduation_project.forums.repositories.projection.PostViewProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p.title AS title, p.content AS content FROM Post p WHERE p.id = :postId AND p.status = 'PENDING'")
    Optional<PostContentProjection> findTitleAndContentById(@Param("postId") Long postId);

    @Query("""
                SELECT p.id AS id,
                       p.title AS title,
                       p.author.id AS authorId,
                       p.status AS status
                FROM Post p
                WHERE p.id = :postId
            """)
    Optional<PostModerationProjection> findModerationInfoById(@Param("postId") Long postId);

    @Modifying
    @Query("""
                UPDATE Post p
                SET p.status = :status,
                    p.approvalInfo.approvalNote = :reason,
                    p.approvalInfo.approvedAt = CURRENT_TIMESTAMP
                WHERE p.id = :postId
            """)
    int updateModerationStatus(
            @Param("postId") Long postId,
            @Param("status") PostStatus status,
            @Param("reason") String reason
    );


    Optional<Post> findByIdAndStatus(Long postId, PostStatus status);

    boolean existsByIdAndStatus(Long id, PostStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :postId")
    int incrementViewCount(@Param("postId") Long postId, @Param("delta") long delta);


    @Query("""
                SELECT p AS post,
                       (SELECT COUNT(l) FROM PostLike l WHERE l.post.id = p.id AND l.isActive = true) AS likeCount,
                       (SELECT COUNT(c) FROM Comment c WHERE c.post.id = p.id) AS commentCount,
                       (CASE WHEN :currentUserId IS NOT NULL AND EXISTS (
                            SELECT 1 FROM PostLike l2
                            WHERE l2.post.id = p.id AND l2.user.id = :currentUserId
                       ) THEN true ELSE false END) AS liked
                FROM Post p
                JOIN FETCH p.author
                WHERE p.id = :postId AND p.status = 'APPROVED'
            """)
    Optional<PostViewProjection> findPostViewProjectionById(@Param("postId") Long postId, @Param("currentUserId") Long currentUserId);

    @Query(value = """
            SELECT p.id AS id,
                   u.id AS authorId,
                   u.username AS authorUsername,
                   u.avatar_url AS authorAvatarUrl,
                   p.title AS title,
                   p.view_count AS viewCount,
                   (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.is_active = true) AS likeCount,
                   p.created_at AS createdAt
            FROM posts p
            JOIN users u ON u.id = p.author_id
            WHERE p.status = 'APPROVED'
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM posts p
                    WHERE p.status = 'APPROVED'
                    """,
            nativeQuery = true)
    Page<PostSimpleProjection> findApprovedPosts(Pageable pageable);
}
