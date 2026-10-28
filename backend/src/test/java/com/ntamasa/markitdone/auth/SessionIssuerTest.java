package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import com.ntamasa.markitdone.user.User;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionIssuerTest {

  private static final String JWT = "header.payload.signature";

  private JwtService jwtService;
  private RefreshTokenService refreshTokenService;
  private AuthCookies authCookies;
  private SessionIssuer sessionIssuer;
  private MockHttpServletResponse response;
  private UUID userId;

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    refreshTokenService = mock(RefreshTokenService.class);
    authCookies =
        new AuthCookies(
            new MarkitdoneProperties(
                "http://localhost:4200",
                "no-reply@markitdone.local",
                new MarkitdoneProperties.Jwt(
                    "test-secret-key-must-be-at-least-32-bytes-long-123456",
                    Duration.ofMinutes(15),
                    Duration.ofDays(14)),
                new MarkitdoneProperties.Cookies(true),
                new MarkitdoneProperties.Recurrence(3)));
    sessionIssuer = new SessionIssuer(jwtService, refreshTokenService, authCookies);
    response = new MockHttpServletResponse();
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName(
      "issue starts a new family and writes access and refresh cookies as separate headers")
  void issueWritesBothCookies() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(jwtService.issueAccessToken(userId)).thenReturn(JWT);
    when(refreshTokenService.issue(userId, false, null))
        .thenReturn(new RefreshTokenService.Issued("raw-1", null));

    sessionIssuer.issue(user, false, response);

    verify(refreshTokenService).issue(eq(userId), eq(false), eq((UUID) null));
    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers).hasSize(2);
    assertThat(headers.get(0)).startsWith("md_access=" + JWT + ";").contains("Max-Age=900");
    assertThat(headers.get(1)).startsWith("md_refresh=raw-1;").doesNotContain("Max-Age");
  }

  @Test
  @DisplayName("issue with remember-me gives the refresh cookie a 14-day Max-Age")
  void issueWithRememberMeSetsMaxAge() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(jwtService.issueAccessToken(userId)).thenReturn(JWT);
    when(refreshTokenService.issue(eq(userId), eq(true), any()))
        .thenReturn(new RefreshTokenService.Issued("raw-2", null));

    sessionIssuer.issue(user, true, response);

    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers.get(1)).contains("md_refresh=raw-2;").contains("Max-Age=1209600");
  }

  @Test
  @DisplayName("issueRotated re-issues both cookies keeping the remember-me choice of the token")
  void issueRotatedKeepsRememberMe() {
    RefreshTokenService.Issued next = new RefreshTokenService.Issued("raw-next", null);
    when(jwtService.issueAccessToken(userId)).thenReturn(JWT);

    sessionIssuer.issueRotated(new RotationResult.Rotated(userId, true, next), response);

    verify(jwtService).issueAccessToken(userId);
    verifyNoMoreInteractions(refreshTokenService);
    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers).hasSize(2);
    assertThat(headers.get(0)).startsWith("md_access=" + JWT + ";");
    assertThat(headers.get(1))
        .startsWith("md_refresh=raw-next;")
        .contains("Max-Age=1209600")
        .contains("Path=/api/auth");
  }

  @Test
  @DisplayName("issueRotated without remember-me keeps the refresh cookie a browser-session cookie")
  void issueRotatedWithoutRememberMeHasNoMaxAge() {
    RefreshTokenService.Issued next = new RefreshTokenService.Issued("raw-next", null);
    when(jwtService.issueAccessToken(userId)).thenReturn(JWT);

    sessionIssuer.issueRotated(new RotationResult.Rotated(userId, false, next), response);

    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers.get(1)).startsWith("md_refresh=raw-next;").doesNotContain("Max-Age");
  }

  @Test
  @DisplayName("clear expires both cookies with Max-Age=0 on their paths")
  void clearExpiresBothCookies() {
    sessionIssuer.clear(response);

    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers).hasSize(2);
    assertThat(headers.get(0))
        .startsWith("md_access=;")
        .contains("Max-Age=0")
        .contains("Path=/api");
    assertThat(headers.get(1))
        .startsWith("md_refresh=;")
        .contains("Max-Age=0")
        .contains("Path=/api/auth");
  }
}
