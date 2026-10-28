package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.config.MarkitdoneProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the refresh token rules: issuing, rotation with reuse detection, and revocation.
 *
 * <p>Raw tokens are 32 random bytes encoded as base64url. Only their SHA-256 hash is stored. Every
 * login starts a new family; each rotation revokes the presented token and issues a successor in
 * the same family. Presenting an already-revoked token is treated as theft and revokes the whole
 * family.
 */
@Service
public class RefreshTokenService {

  public static final String REASON_MISSING = "missing";
  public static final String REASON_UNKNOWN = "unknown";
  public static final String REASON_REUSE = "reuse";
  public static final String REASON_EXPIRED = "expired";

  private static final String ENTITY_TYPE = "RefreshToken";
  private static final int TOKEN_BYTES = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final AuditService auditService;
  private final Duration refreshTtl;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      AuditService auditService,
      MarkitdoneProperties properties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.auditService = auditService;
    this.refreshTtl = properties.jwt().refreshTtl();
  }

  /**
   * A newly issued refresh token: the raw value (returned to the client only) and the stored
   * entity.
   *
   * @param rawToken the raw token value
   * @param token the saved entity
   */
  public record Issued(String rawToken, RefreshToken token) {}

  /**
   * Issues a new refresh token.
   *
   * @param userId the token owner
   * @param rememberMe whether the session should be remembered
   * @param familyId the family to join, or null to start a new family
   * @return the raw token and the saved entity
   */
  @Transactional
  public Issued issue(UUID userId, boolean rememberMe, UUID familyId) {
    String rawToken = newRawToken();
    Instant now = Instant.now();
    UUID effectiveFamilyId = familyId != null ? familyId : UUID.randomUUID();
    RefreshToken token =
        new RefreshToken(
            userId, effectiveFamilyId, hash(rawToken), rememberMe, now, now.plus(refreshTtl));
    return new Issued(rawToken, refreshTokenRepository.save(token));
  }

  /**
   * Rotates a refresh token.
   *
   * <p>A valid token is revoked and replaced by a successor in the same family. A revoked token
   * presented again is treated as stolen: its whole family is revoked and an audit event is
   * recorded. The method returns normally for rejections so that the revocation commits.
   *
   * @param rawToken the raw token presented by the client, may be null
   * @return {@link RotationResult.Rotated} or {@link RotationResult.Rejected}
   */
  @Transactional
  public RotationResult rotate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return new RotationResult.Rejected(REASON_MISSING);
    }
    Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawToken));
    if (found.isEmpty()) {
      return new RotationResult.Rejected(REASON_UNKNOWN);
    }
    RefreshToken presented = found.get();

    if (presented.getRevokedAt() != null) {
      UUID userId = presented.getUserId();
      UUID tokenId = presented.getId();
      refreshTokenRepository.revokeFamily(presented.getFamilyId(), Instant.now());
      auditService.record(
          AuditAction.REFRESH_TOKEN_REUSE_DETECTED, userId, ENTITY_TYPE, tokenId, null, null);
      return new RotationResult.Rejected(REASON_REUSE);
    }

    Instant now = Instant.now();
    if (!presented.getExpiresAt().isAfter(now)) {
      return new RotationResult.Rejected(REASON_EXPIRED);
    }

    presented.setRevokedAt(now);
    String successorRaw = newRawToken();
    RefreshToken successor =
        new RefreshToken(
            presented.getUserId(),
            presented.getFamilyId(),
            hash(successorRaw),
            presented.isRememberMe(),
            now,
            now.plus(refreshTtl));
    successor = refreshTokenRepository.save(successor);
    presented.setReplacedBy(successor.getId());

    auditService.record(
        AuditAction.TOKEN_REFRESHED,
        presented.getUserId(),
        ENTITY_TYPE,
        presented.getId(),
        null,
        null);
    return new RotationResult.Rotated(
        presented.getUserId(), presented.isRememberMe(), new Issued(successorRaw, successor));
  }

  /**
   * Revokes every still-valid token in the family of the given raw token. Unknown or missing tokens
   * are ignored.
   *
   * @param rawToken the raw token whose family is revoked, may be null
   */
  @Transactional
  public void revokeFamilyOf(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokenRepository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
  }

  /**
   * Revokes every still-valid refresh token of a user, across all families.
   *
   * @param userId the user whose tokens are revoked
   */
  @Transactional
  public void revokeAllForUser(UUID userId) {
    refreshTokenRepository.revokeAllForUser(userId, Instant.now());
  }

  /**
   * Computes the storage hash of a raw refresh token: lower-case hex SHA-256 (64 characters).
   *
   * @param rawToken the raw token value
   * @return the hex-encoded SHA-256 digest
   */
  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String newRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
