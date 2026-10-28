package com.ntamasa.markitdone.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    PasswordConfig config = new PasswordConfig();
    passwordEncoder = config.passwordEncoder();
  }

  @Test
  @DisplayName("encoded password starts with {argon2}$argon2id$")
  void testEncodedPasswordHasCorrectPrefix() {
    String password = "TestPassword123!";
    String encoded = passwordEncoder.encode(password);

    assertThat(encoded).startsWith("{argon2}$argon2id$");
  }

  @Test
  @DisplayName("matches succeeds for the same password")
  void testMatchesSucceedsForSamePassword() {
    String password = "TestPassword123!";
    String encoded = passwordEncoder.encode(password);

    boolean matches = passwordEncoder.matches(password, encoded);

    assertThat(matches).isTrue();
  }

  @Test
  @DisplayName("matches fails for different password")
  void testMatchesFailsForDifferentPassword() {
    String password = "TestPassword123!";
    String encoded = passwordEncoder.encode(password);

    boolean matches = passwordEncoder.matches("DifferentPassword456@", encoded);

    assertThat(matches).isFalse();
  }

  @Test
  @DisplayName("two hashes of the same password differ due to salt")
  void testTwoHashesOfSamePasswordDiffer() {
    String password = "TestPassword123!";
    String hash1 = passwordEncoder.encode(password);
    String hash2 = passwordEncoder.encode(password);

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  @DisplayName("matches works with both hashes of same password")
  void testMatchesWorksWithBothHashes() {
    String password = "TestPassword123!";
    String hash1 = passwordEncoder.encode(password);
    String hash2 = passwordEncoder.encode(password);

    assertThat(passwordEncoder.matches(password, hash1)).isTrue();
    assertThat(passwordEncoder.matches(password, hash2)).isTrue();
  }
}
