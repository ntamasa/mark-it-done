package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import com.ntamasa.markitdone.user.UserService;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login and logout rules. */
@Service
public class AuthService {

  private static final String ENTITY_TYPE = "User";
  private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect.";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final RefreshTokenService refreshTokenService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final String dummyHash;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      RefreshTokenService refreshTokenService,
      RefreshTokenRepository refreshTokenRepository) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.refreshTokenService = refreshTokenService;
    this.refreshTokenRepository = refreshTokenRepository;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalisation");
  }

  /**
   * Verifies credentials. The password check always runs, against a fixed dummy hash when the user
   * does not exist, so unknown email and wrong password are indistinguishable.
   *
   * @param req the login request
   * @return the authenticated user
   * @throws ApiException INVALID_CREDENTIALS when the credentials are wrong
   */
  @Transactional
  public User login(LoginRequest req) {
    String email = UserService.normalizeEmail(req.email());
    Optional<User> found = userRepository.findByEmail(email);
    String hash = found.map(User::getPasswordHash).orElse(dummyHash);
    boolean matches = passwordEncoder.matches(req.password(), hash);
    if (found.isEmpty() || !matches) {
      auditService.recordStandalone(
          AuditAction.LOGIN_FAILED,
          found.map(User::getId).orElse(null),
          ENTITY_TYPE,
          found.map(User::getId).orElse(null),
          null,
          Map.of("email", email));
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }
    User user = found.get();
    if (isValidZone(req.timezone()) && !req.timezone().equals(user.getTimezone())) {
      user.setTimezone(req.timezone());
      userRepository.save(user);
    }
    auditService.record(
        AuditAction.LOGIN_SUCCEEDED,
        user.getId(),
        ENTITY_TYPE,
        user.getId(),
        null,
        Map.of("rememberMe", req.rememberMe()));
    return user;
  }

  /**
   * Ends a session: revokes the token family and audits LOGOUT when the token is known. Missing or
   * unknown tokens are ignored.
   *
   * @param rawRefreshToken the raw refresh token, may be null
   */
  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    refreshTokenRepository
        .findByTokenHash(RefreshTokenService.hash(rawRefreshToken))
        .ifPresent(
            token ->
                auditService.record(
                    AuditAction.LOGOUT,
                    token.getUserId(),
                    ENTITY_TYPE,
                    token.getUserId(),
                    null,
                    null));
    refreshTokenService.revokeFamilyOf(rawRefreshToken);
  }

  private static boolean isValidZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return false;
    }
    try {
      ZoneId.of(timezone);
      return true;
    } catch (DateTimeException e) {
      return false;
    }
  }
}
