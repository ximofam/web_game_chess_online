package com.ximofam.graduation_project.users.repositories;

import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastSeen = :lastSeen WHERE u.id = :userId")
    int updateLastSeen(@Param("userId") Long userId, @Param("lastSeen") Instant lastSeen);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM User u
        WHERE u.role = :role
          AND ((u.lastSeen IS NOT NULL AND u.lastSeen < :threshold) OR (u.lastSeen IS NULL AND u.createdAt < :threshold))
    """)
    int deleteExpiredGuests(@Param("role") UserRole role, @Param("threshold") Instant threshold);
}
