package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.Comment;
import com.ximofam.graduation_project.forums.repositories.projection.CommentReplyCountProjection;
import com.ximofam.graduation_project.forums.repositories.projection.CommentWithLikeCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query(value = """
                SELECT c AS comment, COUNT(cl.id) AS likeCount
                FROM Comment c
                JOIN FETCH c.author
                LEFT JOIN c.likes cl ON cl.isActive = true
                WHERE c.post.id = :postId AND c.parent IS NULL
                GROUP BY c, c.author
                ORDER BY CASE WHEN :sortBy = 'likeCount' THEN COUNT(cl.id) ELSE 0 END DESC, c.createdAt DESC
            """,
            countQuery = """
                        SELECT COUNT(c)
                        FROM Comment c
                        WHERE c.post.id = :postId AND c.parent IS NULL
                    """)
    Page<CommentWithLikeCountProjection> findRootCommentsWithLikeCount(
            @Param("postId") Long postId,
            @Param("sortBy") String sortBy,
            Pageable pageable
    );

    @Query(value = """
                SELECT c AS comment, COUNT(cl.id) AS likeCount
                FROM Comment c
                JOIN FETCH c.author
                LEFT JOIN c.likes cl ON cl.isActive = true
                WHERE c.parent.id = :parentId
                GROUP BY c, c.author
                ORDER BY CASE WHEN :sortBy = 'likeCount' THEN COUNT(cl.id) ELSE 0 END DESC, c.createdAt DESC
            """,
            countQuery = """
                        SELECT COUNT(c)
                        FROM Comment c
                        WHERE c.parent.id = :parentId
                    """)
    Page<CommentWithLikeCountProjection> findRepliesWithLikeCount(
            @Param("parentId") Long parentId,
            @Param("sortBy") String sortBy,
            Pageable pageable
    );

    @Query("""
                SELECT c.parent.id AS commentId, COUNT(c.id) AS replyCount
                FROM Comment c
                WHERE c.parent.id IN :commentIds
                GROUP BY c.parent.id
            """)
    List<CommentReplyCountProjection> countRepliesByCommentIdIn(@Param("commentIds") Collection<Long> commentIds);
}
