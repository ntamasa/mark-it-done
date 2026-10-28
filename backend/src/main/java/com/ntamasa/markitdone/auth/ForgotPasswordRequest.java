package com.ntamasa.markitdone.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the forgot-password request.
 *
 * @param email the account email
 */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}
