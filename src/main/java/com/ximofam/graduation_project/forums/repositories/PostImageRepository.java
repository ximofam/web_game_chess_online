package com.ximofam.graduation_project.forums.repositories;

import com.ximofam.graduation_project.forums.entities.PostImage;
import com.ximofam.graduation_project.forums.entities.enums.ImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    void deleteByPublicId(String publicId);

    List<PostImage> findByStatusAndCreatedAtBefore(ImageStatus status, Instant threshold);

    @Modifying
    @Query("UPDATE PostImage pi SET pi.status = 'ATTACHED', pi.postId = :postId " +
            "WHERE pi.publicId IN :publicIds AND pi.uploaderId = :uploaderId AND pi.status = 'ORPHAN'")
    int attachToPost(Long postId, Long uploaderId, List<String> publicIds);
}
