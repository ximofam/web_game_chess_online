package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
}
