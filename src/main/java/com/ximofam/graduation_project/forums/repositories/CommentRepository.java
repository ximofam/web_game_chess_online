package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.Comment;
import com.ximofam.graduation_project.forums.repositories.projection.CommentReplyCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Comment> findByParentId(Long parentId, Pageable pageable);

    @Query("""
                SELECT c.parent.id AS commentId, COUNT(c.id) AS replyCount
                FROM Comment c
                WHERE c.parent.id IN :commentIds
                GROUP BY c.parent.id
            """)
    List<CommentReplyCountProjection> countRepliesByCommentIdIn(Collection<Long> commentIds);
}
