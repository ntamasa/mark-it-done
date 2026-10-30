package com.ntamasa.markitdone.user;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.user.dto.UserDto;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user accounts.
 *
 * <p>Provides operations for user account management, including email normalization, user lookup,
 * and account information retrieval.
 */
@Service
public class UserService {

  private static final String ENTITY_TYPE = "User";

  private final UserRepository userRepository;
  private final AuditService auditService;

  /**
   * Create a UserService.
   *
   * @param userRepository the user repository
   * @param auditService the audit service
   */
  public UserService(UserRepository userRepository, AuditService auditService) {
    this.userRepository = userRepository;
    this.auditService = auditService;
  }

  /**
   * Normalize an email address by trimming and converting to lower case.
   *
   * <p>Uses the root locale for case conversion to ensure consistent behavior across different
   * locales.
   *
   * @param raw the raw email address
   * @return the normalized email address
   */
  public static String normalizeEmail(String raw) {
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Return the given IANA time zone, or UTC when it is missing or not a valid zone.
   *
   * @param zone the time zone identifier, may be null
   * @return the zone identifier, or "UTC" when the input is null, blank or unknown
   */
  public static String validZoneOrUtc(String zone) {
    if (zone == null || zone.isBlank()) {
      return "UTC";
    }
    try {
      ZoneId.of(zone);
      return zone;
    } catch (DateTimeException e) {
      return "UTC";
    }
  }

  /**
   * Get a user by ID.
   *
   * <p>Throws a NOT_FOUND error if the user does not exist.
   *
   * @param id the user ID
   * @return the user
   * @throws com.ntamasa.markitdone.common.ApiException if the user is not found
   */
  public User getById(UUID id) {
    return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
  }

  /**
   * Updates the time zone and theme preference of a user in one transaction.
   *
   * <p>A null request or a null field leaves that value unchanged. When a value actually changes, a
   * USER_PREFERENCES_UPDATED audit entry records the values before and after the change.
   *
   * @param userId the ID of the user to update
   * @param req the fields to change, may be null
   * @return the updated user
   * @throws com.ntamasa.markitdone.common.ApiException if the user is not found
   */
  @Transactional
  public UserDto updatePreferences(UUID userId, UpdatePreferencesRequest req) {
    User user = getById(userId);
    Map<String, Object> before = preferences(user);
    if (req != null) {
      if (req.timezone() != null) {
        user.setTimezone(req.timezone());
      }
      if (req.themePreference() != null) {
        user.setThemePreference(req.themePreference());
      }
    }
    Map<String, Object> after = preferences(user);
    if (!before.equals(after)) {
      auditService.record(
          AuditAction.USER_PREFERENCES_UPDATED, userId, ENTITY_TYPE, userId, before, after);
    }
    return UserDto.of(user);
  }

  private static Map<String, Object> preferences(User user) {
    return Map.of(
        "timezone", user.getTimezone(), "themePreference", user.getThemePreference().name());
  }
}
