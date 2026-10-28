package com.ntamasa.markitdone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of the reset-password request.
 *
 * @param token the raw token from the reset link
 * @param password the new plain password, checked against the strong-password policy
 */
public record ResetPasswordRequest(
    @NotBlank @Size(max = 512) String token, @StrongPassword String password) {}
