package com.ximofam.graduation_project.users.repositories;

import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.enums.UserRole;
import com.ximofam.graduation_project.users.repositories.projections.UserSimpleProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query("SELECT u.id AS id, u.username AS username, u.profile.avatarUrl AS avatarUrl FROM User u WHERE u.id = :id")
    Optional<UserSimpleProjection> findSimpleById(@Param("id") UUID id);

    @Query("SELECT u.id AS id, u.username AS username, u.profile.avatarUrl AS avatarUrl FROM User u WHERE u.id IN :ids")
    List<UserSimpleProjection> findSimpleByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastSeen = :lastSeen WHERE u.id = :userId")
    int updateLastSeen(@Param("userId") UUID userId, @Param("lastSeen") Instant lastSeen);

    @Modifying
    @Transactional
    @Query("""
                DELETE FROM User u
                WHERE u.role = :role
                  AND ((u.lastSeen IS NOT NULL AND u.lastSeen < :threshold) OR (u.lastSeen IS NULL AND u.createdAt < :threshold))
            """)
    int deleteExpiredGuests(@Param("role") UserRole role, @Param("threshold") Instant threshold);
}
