package com.ximofam.graduation_project.notifications.services;

import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.notifications.NotificationMapper;
import com.ximofam.graduation_project.notifications.dtos.request.NotificationRequest;
import com.ximofam.graduation_project.notifications.dtos.response.NotificationResponse;
import com.ximofam.graduation_project.notifications.entities.Notification;
import com.ximofam.graduation_project.notifications.repositories.NotificationRepository;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;

    @Async
    public void send(NotificationRequest request) {
        Notification notification = notificationMapper.toNotification(request);
        notification.setRecipient(userRepository.getReferenceById(request.getRecipientId()));
        notification.setSender(
                request.getSenderId() != null
                        ? userRepository.getReferenceById(request.getSenderId())
                        : null
        );

        notificationRepository.save(notification);

        simpMessagingTemplate.convertAndSendToUser(
                request.getRecipientId().toString(),
                "/queue/notifications",
                notificationMapper.toNotificationResponse(notification)
        );
    }

    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository
                .findByRecipientId(userId, pageable)
                .map(notificationMapper::toNotificationResponse);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    public void delete(Long notificationId, Long userId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        notificationRepository.delete(notification);
    }

    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteAllByRecipientId(userId);
    }

}
