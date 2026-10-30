package com.ntamasa.markitdone.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.user.dto.UserDto;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("normalizeEmail trims and lowercases email")
  void testNormalizeEmailTrimsAndLowercases() {
    String result = UserService.normalizeEmail("  Foo@Example.COM ");
    assertThat(result).isEqualTo("foo@example.com");
  }

  @Test
  @DisplayName("normalizeEmail uses root locale for case conversion")
  void testNormalizeEmailUsesRootLocale() {
    // In Turkish locale, uppercase "I" becomes "ı" (without dot), not "i"
    // Using root locale should convert to "i" (with dot)
    String result = UserService.normalizeEmail("  INFO@EXAMPLE.COM  ");
    assertThat(result).isEqualTo("info@example.com");
  }

  @Test
  @DisplayName("normalizeEmail handles already normalized email")
  void testNormalizeEmailHandlesAlreadyNormalized() {
    String result = UserService.normalizeEmail("user@example.com");
    assertThat(result).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("validZoneOrUtc returns a valid IANA zone unchanged")
  void testValidZoneOrUtcKeepsValidZone() {
    assertThat(UserService.validZoneOrUtc("Europe/Budapest")).isEqualTo("Europe/Budapest");
    assertThat(UserService.validZoneOrUtc("UTC")).isEqualTo("UTC");
  }

  @Test
  @DisplayName("validZoneOrUtc falls back to UTC for unknown zones")
  void testValidZoneOrUtcFallsBackForUnknownZone() {
    assertThat(UserService.validZoneOrUtc("Mars/Base")).isEqualTo("UTC");
  }

  @Test
  @DisplayName("validZoneOrUtc falls back to UTC for null or blank input")
  void testValidZoneOrUtcFallsBackForMissingZone() {
    assertThat(UserService.validZoneOrUtc(null)).isEqualTo("UTC");
    assertThat(UserService.validZoneOrUtc("")).isEqualTo("UTC");
    assertThat(UserService.validZoneOrUtc("   ")).isEqualTo("UTC");
  }

  @Test
  @DisplayName("getById returns user when found")
  void testGetByIdReturnsUserWhenFound() {
    UUID userId = UUID.randomUUID();
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.SYSTEM);
    user.getId(); // Trigger getter to ensure field is accessible
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    User result = userService.getById(userId);

    assertThat(result).isEqualTo(user);
  }

  @Test
  @DisplayName("getById throws NOT_FOUND when user not found")
  void testGetByIdThrowsNotFoundWhenMissing() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getById(userId))
        .isInstanceOf(ApiException.class)
        .hasMessage("User not found")
        .extracting("code")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("updatePreferences changes both fields and audits the before and after values")
  void testUpdatePreferencesChangesBothFieldsAndAudits() {
    UUID userId = UUID.randomUUID();
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.SYSTEM);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserDto result =
        userService.updatePreferences(
            userId, new UpdatePreferencesRequest("America/New_York", ThemePreference.DARK));

    assertThat(result.timezone()).isEqualTo("America/New_York");
    assertThat(result.themePreference()).isEqualTo(ThemePreference.DARK);
    verify(auditService)
        .record(
            eq(AuditAction.USER_PREFERENCES_UPDATED),
            eq(userId),
            eq("User"),
            eq(userId),
            eq(Map.of("timezone", "UTC", "themePreference", "SYSTEM")),
            eq(Map.of("timezone", "America/New_York", "themePreference", "DARK")));
  }

  @Test
  @DisplayName("updatePreferences leaves null fields unchanged")
  void testUpdatePreferencesKeepsNullFields() {
    UUID userId = UUID.randomUUID();
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.LIGHT);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserDto result =
        userService.updatePreferences(
            userId, new UpdatePreferencesRequest(null, ThemePreference.DARK));

    assertThat(result.timezone()).isEqualTo("UTC");
    assertThat(result.themePreference()).isEqualTo(ThemePreference.DARK);
  }

  @Test
  @DisplayName("updatePreferences with a null request changes nothing and does not audit")
  void testUpdatePreferencesNullRequestIsNoOp() {
    UUID userId = UUID.randomUUID();
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.SYSTEM);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserDto result = userService.updatePreferences(userId, null);

    assertThat(result.timezone()).isEqualTo("UTC");
    assertThat(result.themePreference()).isEqualTo(ThemePreference.SYSTEM);
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("updatePreferences does not audit when the values are the same")
  void testUpdatePreferencesSameValuesDoesNotAudit() {
    UUID userId = UUID.randomUUID();
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.SYSTEM);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    userService.updatePreferences(
        userId, new UpdatePreferencesRequest("UTC", ThemePreference.SYSTEM));

    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("updatePreferences throws NOT_FOUND when user not found")
  void testUpdatePreferencesThrowsNotFoundWhenMissing() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userService.updatePreferences(
                    userId, new UpdatePreferencesRequest("UTC", ThemePreference.DARK)))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }
}
