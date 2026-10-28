package com.ntamasa.markitdone.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for password reset tokens. */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  /**
   * Finds a token by the SHA-256 hash of its raw value.
   *
   * @param tokenHash the hex-encoded SHA-256 hash
   * @return the token if found
   */
  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Invalidates every open token of a user.
   *
   * @param userId the user
   * @param usedAt the invalidation timestamp
   * @return the number of tokens invalidated
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :usedAt"
          + " WHERE t.userId = :userId AND t.usedAt IS NULL")
  int invalidateOpenTokens(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);

  /**
   * Atomically marks an open, unexpired token as used.
   *
   * @param id the token id
   * @param now the current time
   * @return 1 if the token was consumed, 0 if it was already used or expired
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now"
          + " WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
  int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
