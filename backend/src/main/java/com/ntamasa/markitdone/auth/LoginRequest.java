package com.ntamasa.markitdone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the login request.
 *
 * @param email the account email
 * @param password the plain password
 * @param rememberMe whether the session should outlive the browser session, false when omitted
 * @param timezone the client's IANA time zone, optional
 */
public record LoginRequest(
    @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(max = 200) String password,
    Boolean rememberMe,
    @Size(max = 64) String timezone) {

  /** An omitted rememberMe means false. */
  public LoginRequest {
    if (rememberMe == null) {
      rememberMe = Boolean.FALSE;
    }
  }
}
