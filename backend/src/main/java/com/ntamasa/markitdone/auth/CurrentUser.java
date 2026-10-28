package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Access to the authenticated user of the current request. */
public final class CurrentUser {

  private CurrentUser() {}

  /**
   * Returns the authenticated user's id.
   *
   * @return the user id from the access token subject
   * @throws ApiException UNAUTHORIZED when there is no authenticated user
   */
  public static UUID id() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
      try {
        return UUID.fromString(token.getName());
      } catch (IllegalArgumentException ex) {
        throw unauthorized();
      }
    }
    throw unauthorized();
  }

  private static ApiException unauthorized() {
    return new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
  }
}
