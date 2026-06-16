package com.ximofam.graduation_project.notifications.repositories;

import com.ximofam.graduation_project.notifications.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("""
            Update Notification n SET n.isRead = true
            WHERE n.recipient.id = :userId AND n.isRead = false
            """)
    void markAllAsRead(Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :recipientId")
    void deleteAllByRecipientId(Long recipientId);
}
