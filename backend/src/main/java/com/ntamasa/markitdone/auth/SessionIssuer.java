package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.user.User;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Starts, continues and ends browser sessions by writing the access and refresh cookies.
 *
 * <p>Each cookie is added as its own Set-Cookie header so that the second does not replace the
 * first.
 */
@Service
public class SessionIssuer {

  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final AuthCookies authCookies;

  public SessionIssuer(
      JwtService jwtService, RefreshTokenService refreshTokenService, AuthCookies authCookies) {
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.authCookies = authCookies;
  }

  /**
   * Starts a new session for a user: a new refresh-token family, an access token and a refresh
   * token written as cookies.
   *
   * @param user the user logging in
   * @param rememberMe whether the session should be remembered
   * @param res the response receiving the Set-Cookie headers
   */
  public void issue(User user, boolean rememberMe, HttpServletResponse res) {
    RefreshTokenService.Issued issued = refreshTokenService.issue(user.getId(), rememberMe, null);
    writeSession(user.getId(), issued.rawToken(), rememberMe, res);
  }

  /**
   * Continues a session after a successful refresh-token rotation, re-issuing both cookies and
   * keeping the remember-me choice of the presented token.
   *
   * @param rotated the successful rotation result
   * @param res the response receiving the Set-Cookie headers
   */
  public void issueRotated(RotationResult.Rotated rotated, HttpServletResponse res) {
    writeSession(rotated.userId(), rotated.next().rawToken(), rotated.rememberMe(), res);
  }

  /**
   * Ends a session by expiring both cookies.
   *
   * @param res the response receiving the Set-Cookie headers
   */
  public void clear(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearedAccessCookie().toString());
    res.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearedRefreshCookie().toString());
  }

  private void writeSession(
      UUID userId, String rawRefreshToken, boolean rememberMe, HttpServletResponse res) {
    String accessToken = jwtService.issueAccessToken(userId);
    res.addHeader(HttpHeaders.SET_COOKIE, authCookies.accessCookie(accessToken).toString());
    res.addHeader(
        HttpHeaders.SET_COOKIE, authCookies.refreshCookie(rawRefreshToken, rememberMe).toString());
  }
}
