package com.ximofam.graduation_project.users.repositories;

import com.ximofam.graduation_project.users.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.id = :id")
    int revokeTokenById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM refresh_tokens
            WHERE id IN (
                SELECT id FROM refresh_tokens
                WHERE expires_at < :now OR is_revoked = true
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredOrRevokedTokensInBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
