package com.ximofam.graduation_project.admin.controllers;

import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.notifications.dtos.response.NotificationResponse;
import com.ximofam.graduation_project.notifications.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {
    private final NotificationService notificationService;

    @ResponseBody
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "id",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(notificationService.getNotifications(principal.getUserId(), pageable));
    }

    @ResponseBody
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(principal.getUserId())));
    }

    @ResponseBody
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        notificationService.markAsRead(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @ResponseBody
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails principal) {

        notificationService.markAllAsRead(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        notificationService.delete(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @ResponseBody
    @DeleteMapping
    public ResponseEntity<Void> deleteAll(
            @AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.deleteAll(principal.getUserId());

        return ResponseEntity.noContent().build();
    }
}
