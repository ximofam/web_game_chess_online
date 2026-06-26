package com.ximofam.graduation_project.notifications;

import com.ximofam.graduation_project.common.helpers.utils.RoutingKeys;
import com.ximofam.graduation_project.notifications.dtos.request.NotificationRequest;
import com.ximofam.graduation_project.notifications.entities.Notification;
import com.ximofam.graduation_project.notifications.repositories.NotificationRepository;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.notification.name}",
            concurrency = "2-5",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handle(NotificationRequest event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        switch (routingKey) {
            case RoutingKeys.NOTIF_PUSH -> handlePush(event);
            case RoutingKeys.NOTIF_EMAIL -> handleEmail(event);
            default -> throw new IllegalArgumentException("Không hỗ trợ Routing Key này: " + routingKey);
        }
    }

    private void handlePush(NotificationRequest event) {
        Notification notification = notificationMapper.toNotification(event);
        notification.setRecipient(userRepository.getReferenceById(event.getRecipientId()));
        notification.setSender(
                event.getSenderId() != null
                        ? userRepository.getReferenceById(event.getSenderId())
                        : null
        );

        notificationRepository.save(notification);

        simpMessagingTemplate.convertAndSendToUser(
                event.getRecipientId().toString(),
                "/queue/notifications",
                notificationMapper.toNotificationResponse(notification)
        );
    }

    private void handleEmail(NotificationRequest event) {

    }
}
