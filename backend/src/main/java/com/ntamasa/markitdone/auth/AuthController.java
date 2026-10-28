package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserService;
import com.ntamasa.markitdone.user.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Register, login, refresh, logout, current user and CSRF bootstrap endpoints. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String FORGOT_MESSAGE =
      "If an account exists for this email, a reset link has been sent.";

  private final AuthService authService;
  private final SessionIssuer sessionIssuer;
  private final RefreshTokenService refreshTokenService;
  private final UserService userService;
  private final PasswordResetService passwordResetService;

  public AuthController(
      AuthService authService,
      SessionIssuer sessionIssuer,
      RefreshTokenService refreshTokenService,
      UserService userService,
      PasswordResetService passwordResetService) {
    this.authService = authService;
    this.sessionIssuer = sessionIssuer;
    this.refreshTokenService = refreshTokenService;
    this.userService = userService;
    this.passwordResetService = passwordResetService;
  }

  /** Makes sure the XSRF-TOKEN cookie is set. */
  @GetMapping("/csrf")
  public ResponseEntity<Void> csrf(CsrfToken token) {
    // The token is deferred: reading its value makes the filter write the cookie.
    token.getToken();
    return ResponseEntity.noContent().build();
  }

  /** Creates an account and starts a session without remember-me. */
  @PostMapping("/register")
  public ResponseEntity<UserDto> register(
      @Valid @RequestBody RegisterRequest req, HttpServletResponse res) {
    User user = authService.register(req);
    sessionIssuer.issue(user, false, res);
    return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.of(user));
  }

  /** Logs in and starts a session. */
  @PostMapping("/login")
  public UserDto login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
    User user = authService.login(req);
    sessionIssuer.issue(user, req.rememberMe(), res);
    return UserDto.of(user);
  }

  /** Rotates the refresh cookie. */
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse res) {
    String raw = AuthCookies.read(req, AuthCookies.REFRESH).orElse(null);
    RotationResult result = refreshTokenService.rotate(raw);
    if (result instanceof RotationResult.Rotated rotated) {
      sessionIssuer.issueRotated(rotated, res);
      return ResponseEntity.noContent().build();
    }
    sessionIssuer.clear(res);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
    detail.setProperty("code", ErrorCode.UNAUTHORIZED.name());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail);
  }

  /** Ends the session. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
    authService.logout(AuthCookies.read(req, AuthCookies.REFRESH).orElse(null));
    sessionIssuer.clear(res);
    return ResponseEntity.noContent().build();
  }

  /** Starts a password reset. The answer is the same for known and unknown emails. */
  @PostMapping("/forgot-password")
  public ResponseEntity<Map<String, String>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest req) {
    passwordResetService.requestReset(req.email());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", FORGOT_MESSAGE));
  }

  /** Sets a new password from a reset link and signs the user out everywhere. */
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(
      @Valid @RequestBody ResetPasswordRequest req, HttpServletResponse res) {
    authService.resetPassword(req);
    sessionIssuer.clear(res);
    return ResponseEntity.noContent().build();
  }

  /** Returns the current user. */
  @GetMapping("/me")
  public UserDto me() {
    return UserDto.of(userService.getById(CurrentUser.id()));
  }
}
