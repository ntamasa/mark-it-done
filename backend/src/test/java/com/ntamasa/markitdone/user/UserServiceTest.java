package com.ntamasa.markitdone.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
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
}
