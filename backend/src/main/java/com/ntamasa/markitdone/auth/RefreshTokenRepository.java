package com.ntamasa.markitdone.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for accessing refresh token entities. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /**
   * Finds a refresh token by the SHA-256 hash of its raw value.
   *
   * @param tokenHash the hex-encoded SHA-256 hash
   * @return an Optional containing the token if found
   */
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Revokes every still-valid token in a family.
   *
   * @param familyId the family to revoke
   * @param revokedAt the revocation timestamp
   * @return the number of tokens revoked
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :revokedAt"
          + " WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

  /**
   * Revokes every still-valid token of a user, across all families.
   *
   * @param userId the user whose tokens are revoked
   * @param revokedAt the revocation timestamp
   * @return the number of tokens revoked
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :revokedAt"
          + " WHERE t.userId = :userId AND t.revokedAt IS NULL")
  int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
