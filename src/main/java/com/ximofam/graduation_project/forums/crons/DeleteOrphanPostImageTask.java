package com.ximofam.graduation_project.forums.crons;

import com.ximofam.graduation_project.common.helpers.services.CloudinaryService;
import com.ximofam.graduation_project.forums.entities.PostImage;
import com.ximofam.graduation_project.forums.entities.enums.ImageStatus;
import com.ximofam.graduation_project.forums.repositories.PostImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeleteOrphanPostImageTask {
    private final PostImageRepository postImageRepository;
    private final CloudinaryService cloudinaryService;

    @Scheduled(fixedRateString = "1h")
    void execute() {
        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
        List<PostImage> orphans = postImageRepository
                .findByStatusAndCreatedAtBefore(ImageStatus.ORPHAN, threshold);

        if (orphans.isEmpty()) {
            return;
        }

        List<String> orphanPublicIds = orphans.stream().map(PostImage::getPublicId).toList();

        cloudinaryService.deleteAll(orphanPublicIds);
        postImageRepository.deleteAll(orphans);
    }
}
