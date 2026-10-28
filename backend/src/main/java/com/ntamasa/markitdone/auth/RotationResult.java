package com.ntamasa.markitdone.auth;

import java.util.UUID;

/**
 * Outcome of presenting a refresh token for rotation.
 *
 * <p>Either {@link Rotated} (the presented token was valid and has been replaced) or {@link
 * Rejected} (with a reason such as {@code "missing"}, {@code "unknown"}, {@code "reuse"} or {@code
 * "expired"}).
 */
public sealed interface RotationResult permits RotationResult.Rotated, RotationResult.Rejected {

  /**
   * The presented token was valid; {@code next} is its successor in the same family.
   *
   * @param userId the token owner
   * @param rememberMe the remember-me flag carried over from the presented token
   * @param next the newly issued successor token
   */
  record Rotated(UUID userId, boolean rememberMe, RefreshTokenService.Issued next)
      implements RotationResult {}

  /**
   * The presented token was not accepted.
   *
   * @param reason why the token was rejected
   */
  record Rejected(String reason) implements RotationResult {}
}
