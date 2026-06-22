package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
}
