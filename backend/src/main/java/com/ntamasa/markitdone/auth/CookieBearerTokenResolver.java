package com.ntamasa.markitdone.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Resolves the bearer token from the {@link AuthCookies#ACCESS} cookie instead of the Authorization
 * header. Requests matching the public matcher never get a token, so a stale cookie cannot make a
 * public endpoint fail.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

  private final RequestMatcher publicEndpoints;

  public CookieBearerTokenResolver(RequestMatcher publicEndpoints) {
    this.publicEndpoints = publicEndpoints;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (publicEndpoints.matches(request)) {
      return null;
    }
    return AuthCookies.read(request, AuthCookies.ACCESS).orElse(null);
  }
}
