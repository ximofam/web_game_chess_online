package com.ximofam.graduation_project.users.crons;

import com.ximofam.graduation_project.users.entities.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteExpiredGuestUsersTask {

    private final UserRepository userRepository;

    @Value("${app.user.guest-max-age-days:30}")
    private int guestMaxAgeDays;

    @Scheduled(cron = "${app.user.clean-guest-cron:0 0 3 * * ?}")
    public void execute() {
        Instant threshold = Instant.now().minus(guestMaxAgeDays, ChronoUnit.DAYS);
        int deletedCount = userRepository.deleteExpiredGuests(UserRole.GUEST, threshold);
        if (deletedCount > 0) {
            log.info("Deleted {} expired guest users (inactive for > {} days)", deletedCount, guestMaxAgeDays);
        }
    }
}
