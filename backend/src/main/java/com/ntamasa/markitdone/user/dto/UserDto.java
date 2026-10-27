package com.ntamasa.markitdone.user.dto;

import com.ntamasa.markitdone.user.ThemePreference;
import com.ntamasa.markitdone.user.User;
import java.util.UUID;

/**
 * Data Transfer Object for user information.
 *
 * <p>Exposes user public information: id, email, display name, time zone, and theme preference.
 * Never exposes the password hash.
 *
 * @param id the user ID
 * @param email the user's email address
 * @param displayName the user's display name
 * @param timezone the user's time zone (IANA identifier)
 * @param themePreference the user's theme preference
 */
public record UserDto(
    UUID id, String email, String displayName, String timezone, ThemePreference themePreference) {

  /**
   * Create a UserDto from a User entity.
   *
   * @param user the user entity
   * @return a UserDto with the user's public information
   */
  public static UserDto of(User user) {
    return new UserDto(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getTimezone(),
        user.getThemePreference());
  }
}
