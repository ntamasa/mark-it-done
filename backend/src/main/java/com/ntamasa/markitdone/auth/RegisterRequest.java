package com.ntamasa.markitdone.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the registration request.
 *
 * @param displayName the name shown in the app, trimmed before saving
 * @param email the account email, normalised before saving
 * @param password the plain password, checked against the strong-password policy
 * @param timezone the client's IANA time zone, optional; an unknown zone falls back to UTC
 */
public record RegisterRequest(
    @NotBlank @Size(max = 80) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @StrongPassword String password,
    @Size(max = 64) String timezone) {}
