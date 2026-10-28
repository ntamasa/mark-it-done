package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import com.ntamasa.markitdone.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the password reset link rules: creating a single-use link valid for 30 minutes and consuming
 * it. Raw tokens use the refresh token scheme: only the SHA-256 hash is stored.
 */
@Service
public class PasswordResetService {

  static final Duration TTL = Duration.ofMinutes(30);
  static final String INVALID_MESSAGE = "This reset link is invalid or has expired.";
  private static final String ENTITY_TYPE = "PasswordResetToken";

  private final PasswordResetTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final ResetMailer resetMailer;

  public PasswordResetService(
      PasswordResetTokenRepository tokenRepository,
      UserRepository userRepository,
      AuditService auditService,
      ResetMailer resetMailer) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.resetMailer = resetMailer;
  }

  /**
   * Requests a password reset. Nothing visible happens for an unknown email. For a known one, any
   * earlier open link is invalidated, a new link is created and the email is sent after the
   * transaction commits.
   *
   * @param rawEmail the email as entered by the user
   */
  @Transactional
  public void requestReset(String rawEmail) {
    String email = UserService.normalizeEmail(rawEmail);
    Optional<User> found = userRepository.findByEmail(email);
    if (found.isEmpty()) {
      auditService.record(
          AuditAction.PASSWORD_RESET_REQUESTED,
          null,
          ENTITY_TYPE,
          null,
          null,
          Map.of("email", email, "known", false));
      return;
    }
    User user = found.get();
    Instant now = Instant.now();
    tokenRepository.invalidateOpenTokens(user.getId(), now);
    String rawToken = RefreshTokenService.newRawToken();
    PasswordResetToken token =
        tokenRepository.save(
            new PasswordResetToken(
                user.getId(), RefreshTokenService.hash(rawToken), now, now.plus(TTL)));
    auditService.record(
        AuditAction.PASSWORD_RESET_REQUESTED,
        user.getId(),
        ENTITY_TYPE,
        token.getId(),
        null,
        Map.of("email", email, "known", true));
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            resetMailer.send(user, rawToken);
          }
        });
  }

  /**
   * Consumes a reset link. It succeeds once.
   *
   * @param rawToken the raw token from the link, may be null
   * @return the id of the user the link belongs to
   * @throws ApiException RESET_TOKEN_INVALID when the link is missing, used or expired
   */
  @Transactional
  public UUID consume(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalid();
    }
    PasswordResetToken token =
        tokenRepository
            .findByTokenHash(RefreshTokenService.hash(rawToken))
            .orElseThrow(PasswordResetService::invalid);
    UUID userId = token.getUserId();
    if (tokenRepository.markUsed(token.getId(), Instant.now()) == 0) {
      throw invalid();
    }
    return userId;
  }

  private static ApiException invalid() {
    return new ApiException(ErrorCode.RESET_TOKEN_INVALID, INVALID_MESSAGE);
  }
}
