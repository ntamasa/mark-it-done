package com.ntamasa.markitdone.support;

import com.ntamasa.markitdone.auth.AuthCookies;
import com.ntamasa.markitdone.auth.JwtService;
import com.ntamasa.markitdone.user.ThemePreference;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Test helper that creates users and builds valid access cookies for them. */
@Component
public class TestUsers {

  public static final String PASSWORD = "Summer-2026!x";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public TestUsers(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  /**
   * Creates and saves a user with the password {@link #PASSWORD}.
   *
   * @param email the user's email
   * @return the saved user
   */
  public User create(String email) {
    return userRepository.save(
        new User(
            email, "Test User", passwordEncoder.encode(PASSWORD), "UTC", ThemePreference.SYSTEM));
  }

  /**
   * Builds a valid access cookie for a user.
   *
   * @param user the user
   * @return the md_access cookie
   */
  public Cookie accessCookie(User user) {
    return new Cookie(AuthCookies.ACCESS, jwtService.issueAccessToken(user.getId()));
  }
}
