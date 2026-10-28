package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.logging.UserMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the authenticated user id in the log context. Registered only inside the security chain
 * (deliberately not a bean); the MDC entry is cleared by the request id filter.
 */
public class UserMdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken) {
      try {
        UserMdc.set(UUID.fromString(authentication.getName()));
      } catch (IllegalArgumentException ignored) {
        // subject is not a UUID; leave the log context untouched
      }
    }
    filterChain.doFilter(request, response);
  }
}
