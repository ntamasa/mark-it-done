package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.ThemePreference;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private AuditService auditService;
  private RefreshTokenService refreshTokenService;
  private RefreshTokenRepository refreshTokenRepository;
  private PasswordResetService passwordResetService;
  private AuthService service;
  private User user;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    auditService = mock(AuditService.class);
    refreshTokenService = mock(RefreshTokenService.class);
    refreshTokenRepository = mock(RefreshTokenRepository.class);
    passwordResetService = mock(PasswordResetService.class);
    when(passwordEncoder.encode(any())).thenReturn("DUMMY");
    service =
        new AuthService(
            userRepository,
            passwordEncoder,
            auditService,
            refreshTokenService,
            refreshTokenRepository,
            passwordResetService);
    user = new User("a@b.com", "A", "HASH", "UTC", ThemePreference.SYSTEM);
  }

  @Test
  void unknownEmailStillChecksPasswordAgainstDummyHashAndAuditsFailure() {
    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("pw", "DUMMY")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginRequest(" A@B.com ", "pw", false, null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    verify(passwordEncoder).matches("pw", "DUMMY");
    verify(auditService)
        .recordStandalone(eq(AuditAction.LOGIN_FAILED), any(), any(), any(), any(), any());
  }

  @Test
  void registerRejectsDuplicateEmailWhenInsertHitsUniqueConstraint() {
    when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("uq_users_email"));

    assertThatThrownBy(
            () ->
                service.register(
                    new RegisterRequest("  A  ", " A@B.com ", "Strong-Passw0rd!", null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void wrongPasswordFails() {
    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "HASH")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "pw", false, null)))
        .isInstanceOf(ApiException.class);
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void successUpdatesTimezoneAndIgnoresInvalidZone() {
    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "HASH")).thenReturn(true);

    service.login(new LoginRequest("a@b.com", "pw", true, "Europe/Budapest"));
    assertThat(user.getTimezone()).isEqualTo("Europe/Budapest");

    service.login(new LoginRequest("a@b.com", "pw", true, "Not/AZone"));
    assertThat(user.getTimezone()).isEqualTo("Europe/Budapest");
    verify(auditService, org.mockito.Mockito.times(2))
        .record(eq(AuditAction.LOGIN_SUCCEEDED), any(), any(), any(), any(), any());
  }

  @Test
  void logoutWithoutTokenDoesNothing() {
    service.logout(null);
    verify(refreshTokenService, never()).revokeFamilyOf(any());
  }

  @Test
  void resetPasswordConsumesChangesHashRevokesAllAndAudits() {
    java.util.UUID id = java.util.UUID.randomUUID();
    when(passwordResetService.consume("tok")).thenReturn(id);
    when(userRepository.findById(id)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("New-pass1!")).thenReturn("NEWHASH");

    service.resetPassword(new ResetPasswordRequest("tok", "New-pass1!"));

    assertThat(user.getPasswordHash()).isEqualTo("NEWHASH");
    verify(userRepository).save(user);
    verify(refreshTokenService).revokeAllForUser(id);
    verify(auditService)
        .record(eq(AuditAction.PASSWORD_RESET_COMPLETED), eq(id), any(), eq(id), any(), any());
  }

  @Test
  void resetPasswordWithInvalidTokenChangesNothing() {
    when(passwordResetService.consume("bad"))
        .thenThrow(new ApiException(ErrorCode.RESET_TOKEN_INVALID, "x"));

    assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("bad", "New-pass1!")))
        .isInstanceOf(ApiException.class);
    verify(refreshTokenService, never()).revokeAllForUser(any());
    verify(userRepository, never()).save(any());
  }
}
