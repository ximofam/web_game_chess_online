package com.ximofam.graduation_project.users.tasks;

import com.ximofam.graduation_project.users.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanRefreshTokenTask {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${users.scheduler.clean-refresh-token.batch-size:1000}")
    private int batchSize;

    @Scheduled(cron = "${users.scheduler.clean-refresh-token.cron:0 0 3 * * *}")
    public void execute() {
        log.info("Start cleaning expired refresh tokens");
        int totalDeleted = 0;

        int deleted;
        do {
            deleted = refreshTokenRepository.deleteExpiredOrRevokedTokensInBatch(Instant.now(), batchSize);
            totalDeleted += deleted;
            log.debug("Deleted batch of {} tokens", deleted);
        } while (deleted == batchSize);

        log.info("Finished. Total deleted: {}", totalDeleted);
    }
}