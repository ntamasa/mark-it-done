package com.ntamasa.markitdone.user;

import com.ntamasa.markitdone.common.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service for managing user accounts.
 *
 * <p>Provides operations for user account management, including email normalization, user lookup,
 * and account information retrieval.
 */
@Service
public class UserService {

  private final UserRepository userRepository;

  /**
   * Create a UserService.
   *
   * @param userRepository the user repository
   */
  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
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
}
