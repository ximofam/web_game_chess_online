package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.Post;
import com.ximofam.graduation_project.forums.enums.PostStatus;
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
                       (SELECT COUNT(c) FROM Comment c WHERE c.post.id = p.id) AS commentCount
                FROM Post p
                JOIN FETCH p.author
                WHERE p.id = :postId AND p.status = 'APPROVED'
            """)
    Optional<PostViewProjection> findPostViewProjectionById(@Param("postId") Long postId);

    @Query("""
                SELECT p AS post,
                       (SELECT COUNT(l) FROM PostLike l WHERE l.post.id = p.id AND l.isActive = true) AS likeCount,
                       (SELECT COUNT(c) FROM Comment c WHERE c.post.id = p.id) AS commentCount
                FROM Post p
                JOIN FETCH p.author
                WHERE p.id = :postId AND p.author.id = :authorId
            """)
    Optional<PostViewProjection> findMyPostById(@Param("postId") Long postId, @Param("authorId") Long authorId);

    @Query(value = """
            SELECT * FROM (
                SELECT p.id AS id,
                       u.id AS authorId,
                       u.username AS authorUsername,
                       u.avatar_url AS authorAvatarUrl,
                       p.title AS title,
                       p.view_count AS viewCount,
                       (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.is_active = true) AS likeCount,
                       (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS commentCount,
                       p.created_at AS createdAt
                FROM posts p
                JOIN users u ON u.id = p.author_id AND u.deleted_at IS NULL
                WHERE p.status = 'APPROVED' AND p.deleted_at IS NULL
            ) result
            ORDER BY
                CASE :sortBy
                    WHEN 'mostViewed' THEN viewCount
                    WHEN 'mostLiked' THEN likeCount
                    ELSE 0 END DESC,
                CASE :sortBy
                    WHEN 'mostLiked' THEN viewCount
                    ELSE 0 END DESC,
                createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM posts p
                    WHERE p.status = 'APPROVED' AND p.deleted_at IS NULL
                    """,
            nativeQuery = true)
    Page<PostSimpleProjection> findApprovedPosts(@Param("sortBy") String sortBy, Pageable pageable);

    @Query(value = """
            SELECT p.id AS id,
                   u.id AS authorId,
                   u.username AS authorUsername,
                   u.avatar_url AS authorAvatarUrl,
                   p.title AS title,
                   p.view_count AS viewCount,
                   (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.is_active = true) AS likeCount,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS commentCount,
                   p.created_at AS createdAt,
                   p.status AS status
            FROM posts p
            JOIN users u ON u.id = p.author_id AND u.deleted_at IS NULL
            WHERE p.author_id = :authorId
              AND p.deleted_at IS NULL
              AND (CAST(:status AS VARCHAR) IS NULL OR p.status = :status)
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM posts p
                    WHERE p.author_id = :authorId
                      AND p.deleted_at IS NULL
                      AND (CAST(:status AS VARCHAR) IS NULL OR p.status = :status)
                    """,
            nativeQuery = true)
    Page<PostSimpleProjection> findMyPosts(@Param("authorId") Long authorId, @Param("status") String status, Pageable pageable);

    // ponytail: FTS first, ILIKE fallback via UNION ALL + dedup; both hit GIN indexes.
    // Upgrade path: switch to websearch_to_tsquery for quoted-phrase support.
    @Query(value = """
            SELECT * FROM (
                SELECT DISTINCT ON (id) id, authorId, authorUsername, authorAvatarUrl,
                       title, viewCount, likeCount, commentCount, createdAt, searchPriority
                FROM (
                    SELECT p.id AS id,
                           u.id AS authorId,
                           u.username AS authorUsername,
                           u.avatar_url AS authorAvatarUrl,
                           p.title AS title,
                           p.view_count AS viewCount,
                           (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.is_active = true) AS likeCount,
                           (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS commentCount,
                           p.created_at AS createdAt,
                           1 AS searchPriority
                    FROM posts p
                    JOIN users u ON u.id = p.author_id AND u.deleted_at IS NULL
                    WHERE p.status = 'APPROVED' AND p.deleted_at IS NULL
                      AND p.title_tsv @@ plainto_tsquery('simple', immutable_unaccent(:keyword))
            
                    UNION ALL
            
                    SELECT p.id AS id,
                           u.id AS authorId,
                           u.username AS authorUsername,
                           u.avatar_url AS authorAvatarUrl,
                           p.title AS title,
                           p.view_count AS viewCount,
                           (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.is_active = true) AS likeCount,
                           (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS commentCount,
                           p.created_at AS createdAt,
                           2 AS searchPriority
                    FROM posts p
                    JOIN users u ON u.id = p.author_id AND u.deleted_at IS NULL
                    WHERE p.status = 'APPROVED' AND p.deleted_at IS NULL
                      AND p.title ILIKE '%' || immutable_unaccent(:keyword) || '%'
                ) combined
                ORDER BY id, searchPriority
            ) deduped
            ORDER BY searchPriority,
                     CASE :sortBy
                         WHEN 'mostViewed' THEN viewCount
                         WHEN 'mostLiked' THEN likeCount
                         ELSE 0 END DESC,
                     CASE :sortBy
                         WHEN 'mostLiked' THEN viewCount
                         ELSE 0 END DESC,
                     createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM posts p
                    WHERE p.status = 'APPROVED' AND p.deleted_at IS NULL
                      AND (p.title_tsv @@ plainto_tsquery('simple', :keyword)
                           OR p.title ILIKE '%' || :keyword || '%')
                    """,
            nativeQuery = true)
    Page<PostSimpleProjection> searchApprovedPosts(@Param("keyword") String keyword, @Param("sortBy") String sortBy, Pageable pageable);
}
