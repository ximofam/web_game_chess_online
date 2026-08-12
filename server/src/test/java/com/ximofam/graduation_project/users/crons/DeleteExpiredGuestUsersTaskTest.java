package com.ximofam.graduation_project.users.crons;

import com.ximofam.graduation_project.users.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteExpiredGuestUsersTaskTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeleteExpiredGuestUsersTask task;

    @Test
    void execute_ShouldCallDeleteExpiredGuestsWithCalculatedThreshold() {
        ReflectionTestUtils.setField(task, "guestMaxAgeDays", 30);
        when(userRepository.deleteExpiredGuests(eq(UserRole.GUEST), any(Instant.class))).thenReturn(5);

        task.execute();

        verify(userRepository).deleteExpiredGuests(eq(UserRole.GUEST), any(Instant.class));
    }
}
