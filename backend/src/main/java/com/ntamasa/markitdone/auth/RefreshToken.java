package com.ntamasa.markitdone.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a stored refresh token.
 *
 * <p>Only the SHA-256 hash of the raw token is persisted. Tokens that belong to the same login
 * share a family ID; rotation links a revoked token to its successor via {@code replacedBy}.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "remember_me", nullable = false)
  private boolean rememberMe;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  // Constructors
  public RefreshToken() {}

  public RefreshToken(
      UUID userId,
      UUID familyId,
      String tokenHash,
      boolean rememberMe,
      Instant createdAt,
      Instant expiresAt) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.rememberMe = rememberMe;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public boolean isRememberMe() {
    return rememberMe;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public UUID getReplacedBy() {
    return replacedBy;
  }

  // Setters for mutable fields
  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public void setReplacedBy(UUID replacedBy) {
    this.replacedBy = replacedBy;
  }
}
