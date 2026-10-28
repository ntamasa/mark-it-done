package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthCookiesTest {

  private static final String JWT = "header.payload.signature";
  private static final String RAW = "raw-refresh-token";

  @Test
  @DisplayName("access cookie is HttpOnly, SameSite=Strict, Path=/api, Max-Age=900 and Secure")
  void accessCookieAttributes() {
    String header = cookies(true).accessCookie(JWT).toString();

    assertThat(header)
        .startsWith("md_access=" + JWT + ";")
        .contains("Max-Age=900")
        .contains("Path=/api")
        .contains("SameSite=Strict")
        .contains("HttpOnly")
        .contains("Secure");
  }

  @Test
  @DisplayName("access cookie has no Secure attribute when cookies.secure is false")
  void accessCookieNotSecureWhenDisabled() {
    String header = cookies(false).accessCookie(JWT).toString();

    assertThat(header).doesNotContain("Secure").contains("HttpOnly").contains("SameSite=Strict");
  }

  @Test
  @DisplayName("refresh cookie has Path=/api/auth and no Max-Age without remember-me")
  void refreshCookieWithoutRememberMeHasNoMaxAge() {
    String header = cookies(true).refreshCookie(RAW, false).toString();

    assertThat(header)
        .startsWith("md_refresh=" + RAW + ";")
        .doesNotContain("Max-Age")
        .doesNotContain("Expires")
        .contains("Path=/api/auth")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Secure");
  }

  @Test
  @DisplayName("refresh cookie has Max-Age=1209600 with remember-me")
  void refreshCookieWithRememberMeHasFourteenDayMaxAge() {
    String header = cookies(true).refreshCookie(RAW, true).toString();

    assertThat(header).contains("Max-Age=1209600").contains("Path=/api/auth");
  }

  @Test
  @DisplayName("cleared access cookie has Max-Age=0 and the access path")
  void clearedAccessCookie() {
    String header = cookies(true).clearedAccessCookie().toString();

    assertThat(header)
        .startsWith("md_access=;")
        .contains("Max-Age=0")
        .contains("Path=/api")
        .contains("HttpOnly")
        .contains("Secure");
  }

  @Test
  @DisplayName("cleared refresh cookie has Max-Age=0 and the refresh path")
  void clearedRefreshCookie() {
    String header = cookies(true).clearedRefreshCookie().toString();

    assertThat(header).startsWith("md_refresh=;").contains("Max-Age=0").contains("Path=/api/auth");
  }

  @Test
  @DisplayName("read returns the cookie value when present")
  void readReturnsValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AuthCookies.REFRESH, RAW));

    assertThat(AuthCookies.read(request, AuthCookies.REFRESH)).contains(RAW);
  }

  @Test
  @DisplayName("read returns empty when the cookie is absent or no cookies were sent")
  void readReturnsEmptyWhenAbsent() {
    MockHttpServletRequest withoutCookies = new MockHttpServletRequest();
    MockHttpServletRequest otherCookie = new MockHttpServletRequest();
    otherCookie.setCookies(new Cookie("other", "value"));

    assertThat(AuthCookies.read(withoutCookies, AuthCookies.ACCESS)).isEmpty();
    assertThat(AuthCookies.read(otherCookie, AuthCookies.ACCESS)).isEmpty();
  }

  @Test
  @DisplayName("read treats blank values as missing")
  void readTreatsBlankAsMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AuthCookies.ACCESS, "   "));

    Optional<String> value = AuthCookies.read(request, AuthCookies.ACCESS);

    assertThat(value).isEmpty();
  }

  @Test
  @DisplayName("constants have the cookie names from the design")
  void constants() {
    assertThat(AuthCookies.ACCESS).isEqualTo("md_access");
    assertThat(AuthCookies.REFRESH).isEqualTo("md_refresh");
  }

  private static AuthCookies cookies(boolean secure) {
    return new AuthCookies(
        new MarkitdoneProperties(
            "http://localhost:4200",
            "no-reply@markitdone.local",
            new MarkitdoneProperties.Jwt(
                "test-secret-key-must-be-at-least-32-bytes-long-123456",
                Duration.ofMinutes(15),
                Duration.ofDays(14)),
            new MarkitdoneProperties.Cookies(secure),
            new MarkitdoneProperties.Recurrence(3)));
  }
}
