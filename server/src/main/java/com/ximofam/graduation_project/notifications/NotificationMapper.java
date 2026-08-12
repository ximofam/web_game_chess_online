package com.ximofam.graduation_project.notifications;


import com.ximofam.graduation_project.notifications.dtos.request.NotificationRequest;
import com.ximofam.graduation_project.notifications.dtos.response.NotificationResponse;
import com.ximofam.graduation_project.notifications.entities.Notification;
import com.ximofam.graduation_project.users.UserMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface NotificationMapper {
    Notification toNotification(NotificationRequest request);

    NotificationResponse toNotificationResponse(Notification notification);
}
