package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserService;
import com.ntamasa.markitdone.user.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login, refresh, logout, current user and CSRF bootstrap endpoints. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final SessionIssuer sessionIssuer;
  private final RefreshTokenService refreshTokenService;
  private final UserService userService;

  public AuthController(
      AuthService authService,
      SessionIssuer sessionIssuer,
      RefreshTokenService refreshTokenService,
      UserService userService) {
    this.authService = authService;
    this.sessionIssuer = sessionIssuer;
    this.refreshTokenService = refreshTokenService;
    this.userService = userService;
  }

  /** Makes sure the XSRF-TOKEN cookie is set. */
  @GetMapping("/csrf")
  public ResponseEntity<Void> csrf(CsrfToken token) {
    // The token is deferred: reading its value makes the filter write the cookie.
    token.getToken();
    return ResponseEntity.noContent().build();
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

  /** Returns the current user. */
  @GetMapping("/me")
  public UserDto me() {
    return UserDto.of(userService.getById(CurrentUser.id()));
  }
}
