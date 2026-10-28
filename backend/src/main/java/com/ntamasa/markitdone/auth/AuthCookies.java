package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and reads the two HttpOnly session cookies.
 *
 * <p>{@link #ACCESS} carries the access JWT and is sent to {@code /api}. {@link #REFRESH} carries
 * the raw refresh token and is sent only to {@code /api/auth}. Both are SameSite=Strict and Secure
 * unless {@code markitdone.cookies.secure} is false.
 */
@Component
public class AuthCookies {

  public static final String ACCESS = "md_access";
  public static final String REFRESH = "md_refresh";

  static final String ACCESS_PATH = "/api";
  static final String REFRESH_PATH = "/api/auth";
  static final String SAME_SITE = "Strict";

  private final boolean secure;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public AuthCookies(MarkitdoneProperties properties) {
    this.secure = properties.cookies().secure();
    this.accessTtl = properties.jwt().accessTtl();
    this.refreshTtl = properties.jwt().refreshTtl();
  }

  /**
   * Builds the access cookie carrying a JWT. Its lifetime is the access token TTL.
   *
   * @param jwt the access token value
   * @return the Set-Cookie value for {@link #ACCESS}
   */
  public ResponseCookie accessCookie(String jwt) {
    return base(ACCESS, jwt, ACCESS_PATH).maxAge(accessTtl).build();
  }

  /**
   * Builds the refresh cookie carrying a raw refresh token. With remember-me it lasts the refresh
   * TTL; otherwise it is a browser-session cookie with no Max-Age.
   *
   * @param rawToken the raw refresh token value
   * @param rememberMe whether the session is remembered
   * @return the Set-Cookie value for {@link #REFRESH}
   */
  public ResponseCookie refreshCookie(String rawToken, boolean rememberMe) {
    ResponseCookie.ResponseCookieBuilder builder = base(REFRESH, rawToken, REFRESH_PATH);
    if (rememberMe) {
      builder.maxAge(refreshTtl);
    }
    return builder.build();
  }

  /**
   * Builds the cookie that expires the access cookie.
   *
   * @return the Set-Cookie value for {@link #ACCESS} with Max-Age=0
   */
  public ResponseCookie clearedAccessCookie() {
    return base(ACCESS, "", ACCESS_PATH).maxAge(Duration.ZERO).build();
  }

  /**
   * Builds the cookie that expires the refresh cookie.
   *
   * @return the Set-Cookie value for {@link #REFRESH} with Max-Age=0
   */
  public ResponseCookie clearedRefreshCookie() {
    return base(REFRESH, "", REFRESH_PATH).maxAge(Duration.ZERO).build();
  }

  /**
   * Reads a cookie value from the request. Blank values count as missing.
   *
   * @param req the incoming request
   * @param name the cookie name
   * @return the value, or empty when the cookie is absent or blank
   */
  public static Optional<String> read(HttpServletRequest req, String name) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite(SAME_SITE)
        .path(path);
  }
}
