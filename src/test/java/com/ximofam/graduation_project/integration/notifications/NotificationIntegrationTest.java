package com.ximofam.graduation_project.integration.notifications;

import com.ximofam.graduation_project.integration.base.BaseIntegrationTest;
import com.ximofam.graduation_project.notifications.entities.Notification;
import com.ximofam.graduation_project.notifications.entities.enums.NotificationType;
import com.ximofam.graduation_project.notifications.repositories.NotificationRepository;
import com.ximofam.graduation_project.notifications.services.NotificationService;
import com.ximofam.graduation_project.users.dtos.response.TokenResponse;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Notification API Integration Tests")
public class NotificationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    private TokenResponse token;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        token = performLogin("valid_user", PASSWORD);
        authHeader = "Bearer " + token.getAccessToken();
    }


    private Notification createNotification(boolean isRead) {
        Notification n = new Notification();
        n.setRecipient(validUser);
        n.setSender(null);
        n.setType(NotificationType.SYSTEM_MESSAGE);
        n.setTitle("Test notification");
        n.setRead(isRead);
        return notificationRepository.save(n);
    }

    private Notification createNotification(boolean isRead, User user) {
        Notification n = new Notification();
        n.setRecipient(user);
        n.setSender(null);
        n.setType(NotificationType.SYSTEM_MESSAGE);
        n.setTitle("Test notification");
        n.setRead(isRead);
        return notificationRepository.save(n);
    }

    private List<Notification> createNotifications(int count, boolean isRead) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createNotification(isRead))
                .toList();
    }


    @Nested
    @DisplayName("GET /api/notifications")
    class GetNotifications {

        @Test
        @DisplayName("200 - trả về danh sách notification của user")
        void shouldReturnNotificationsForUser() throws Exception {
            createNotifications(3, false);

            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements", is(3)));
        }

        @Test
        @DisplayName("200 - trả về page rỗng khi không có notification")
        void shouldReturnEmptyPageWhenNoNotifications() throws Exception {
            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("200 - phân trang hoạt động đúng")
        void shouldRespectPagination() throws Exception {
            createNotifications(5, false);

            mockMvc.perform(get("/api/notifications")
                            .param("page", "0")
                            .param("size", "2")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements", is(5)))
                    .andExpect(jsonPath("$.totalPages", is(3)));
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("GET /api/notifications/unread-count")
    class GetUnreadCount {

        @Test
        @DisplayName("200 - đếm đúng số notification chưa đọc")
        void shouldReturnCorrectUnreadCount() throws Exception {
            createNotifications(3, false); // unread
            createNotifications(2, true);  // read

            mockMvc.perform(get("/api/notifications/unread-count")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(3)));
        }

        @Test
        @DisplayName("200 - trả về 0 khi không có notification chưa đọc")
        void shouldReturnZeroWhenAllRead() throws Exception {
            createNotifications(2, true);

            mockMvc.perform(get("/api/notifications/unread-count")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(0)));
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("PATCH /api/notifications/{id}/read")
    class MarkAsRead {

        @Test
        @DisplayName("204 - đánh dấu notification là đã đọc thành công")
        void shouldMarkNotificationAsRead() throws Exception {
            Notification notification = createNotification(false);

            mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assert updated.isRead();
        }

        @Test
        @DisplayName("204 - idempotent khi notification đã được đọc rồi")
        void shouldBeIdempotentWhenAlreadyRead() throws Exception {
            Notification notification = createNotification(true);

            mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("404 - notification không tồn tại")
        void shouldReturn404WhenNotificationNotFound() throws Exception {
            mockMvc.perform(patch("/api/notifications/{id}/read", 999999L)
                            .header("Authorization", authHeader))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 - notification của user khác")
        void shouldReturn404WhenNotificationBelongsToAnotherUser() throws Exception {
            // Tạo user khác và notification của họ
            var otherUser = userRepository.save(
                    TestUtils.buildUser("other_user", "other@example.com", PASSWORD, true, passwordEncoder)
            );
            Notification otherNotification = createNotification(false, otherUser);

            mockMvc.perform(patch("/api/notifications/{id}/read", otherNotification.getId())
                            .header("Authorization", authHeader))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(patch("/api/notifications/{id}/read", 1L))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("PATCH /api/notifications/read-all")
    class MarkAllAsRead {

        @Test
        @DisplayName("204 - đánh dấu tất cả notification là đã đọc")
        void shouldMarkAllNotificationsAsRead() throws Exception {
            createNotifications(5, false);

            mockMvc.perform(patch("/api/notifications/read-all")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(validUser.getId());
            assert unreadCount == 0;
        }

        @Test
        @DisplayName("204 - không lỗi khi không có notification nào")
        void shouldNotFailWhenNoNotifications() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("204 - chỉ đánh dấu notification của đúng user")
        void shouldOnlyMarkReadForCurrentUser() throws Exception {
            createNotifications(3, false); // của validUser

            var otherUser = userRepository.save(
                    TestUtils.buildUser("other_user2", "other2@example.com", PASSWORD, true, passwordEncoder)
            );
            Notification otherNotification = createNotification(false, otherUser);

            mockMvc.perform(patch("/api/notifications/read-all")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            // Notification của otherUser vẫn chưa đọc
            long otherUnread = notificationRepository.countByRecipientIdAndIsReadFalse(otherUser.getId());
            assert otherUnread == 1;
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all"))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("DELETE /api/notifications/{id}")
    class DeleteNotification {

        @Test
        @DisplayName("204 - xóa notification thành công")
        void shouldDeleteNotification() throws Exception {
            Notification notification = createNotification(false);

            mockMvc.perform(delete("/api/notifications/{id}", notification.getId())
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            assert notificationRepository.findById(notification.getId()).isEmpty();
        }

        @Test
        @DisplayName("404 - notification không tồn tại")
        void shouldReturn404WhenNotificationNotFound() throws Exception {
            mockMvc.perform(delete("/api/notifications/{id}", 999999L)
                            .header("Authorization", authHeader))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 - không thể xóa notification của user khác")
        void shouldReturn404WhenDeletingOtherUsersNotification() throws Exception {
            var otherUser = userRepository.save(
                    TestUtils.buildUser("other_user3", "other3@example.com", PASSWORD, true, passwordEncoder)
            );
            Notification otherNotification = createNotification(false, otherUser);

            mockMvc.perform(delete("/api/notifications/{id}", otherNotification.getId())
                            .header("Authorization", authHeader))
                    .andExpect(status().isNotFound());


            assert notificationRepository.findById(otherNotification.getId()).isPresent();
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(delete("/api/notifications/{id}", 1L))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("DELETE /api/notifications")
    class DeleteAllNotifications {

        @Test
        @DisplayName("204 - xóa tất cả notification của user")
        void shouldDeleteAllNotifications() throws Exception {
            createNotifications(5, false);

            mockMvc.perform(delete("/api/notifications")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            long remaining = notificationRepository.countByRecipientIdAndIsReadFalse(validUser.getId());
            assert remaining == 0;
        }

        @Test
        @DisplayName("204 - không lỗi khi không có notification nào")
        void shouldNotFailWhenNoNotifications() throws Exception {
            mockMvc.perform(delete("/api/notifications")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("204 - chỉ xóa notification của đúng user")
        void shouldOnlyDeleteCurrentUserNotifications() throws Exception {
            createNotifications(3, false); // validUser

            var otherUser = userRepository.save(
                    TestUtils.buildUser("other_user4", "other4@example.com", PASSWORD, true, passwordEncoder)
            );
            Notification otherNotification = createNotification(false, otherUser);

            mockMvc.perform(delete("/api/notifications")
                            .header("Authorization", authHeader))
                    .andExpect(status().isNoContent());

            // Notification của otherUser vẫn còn
            assert notificationRepository.findById(otherNotification.getId()).isPresent();
        }

        @Test
        @DisplayName("401 - không có token")
        void shouldReturn401WhenNoToken() throws Exception {
            mockMvc.perform(delete("/api/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }
}