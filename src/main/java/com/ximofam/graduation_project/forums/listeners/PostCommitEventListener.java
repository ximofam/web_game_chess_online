package com.ximofam.graduation_project.forums.listeners;

import com.ximofam.graduation_project.common.helpers.services.EventPublisher;
import com.ximofam.graduation_project.common.helpers.utils.RoutingKeys;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.forums.events.PostModerationCompletedEvent;
import com.ximofam.graduation_project.forums.events.PostModerationEvent;
import com.ximofam.graduation_project.notifications.dtos.request.NotificationRequest;
import com.ximofam.graduation_project.notifications.entities.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostCommitEventListener {

    private final EventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostCreate(PostModerationEvent event) {
        eventPublisher.publish(RoutingKeys.POST_MODERATION, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostModeration(PostModerationCompletedEvent event) {
        boolean isApproved = event.getStatus() == PostStatus.APPROVED;

        eventPublisher.publish(RoutingKeys.NOTIF_PUSH, NotificationRequest.builder()
                .recipientId(event.getRecipientId())
                .type(NotificationType.SYSTEM_MESSAGE)
                .title(String.format("Bài viết \"%s\" của bạn đã %s",
                        event.getPostTitle(),
                        isApproved ? "được duyệt" : "bị từ chối"))
                .message(isApproved
                        ? "Bài viết đã được kiểm duyệt và hiển thị công khai."
                        : String.format("Lý do từ chối: %s", event.getReason()))
                .build());
    }
}