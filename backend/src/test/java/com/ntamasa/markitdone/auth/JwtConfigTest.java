package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtConfigTest {

  @Test
  @DisplayName("secret of exactly 32 bytes is accepted and builds an HMAC-SHA256 key")
  void thirtyTwoByteSecretIsAccepted() {
    SecretKey key =
        new JwtConfig().jwtSecretKey(propertiesWithSecret("0123456789abcdef0123456789abcdef"));

    assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(key.getEncoded()).hasSize(32);
  }

  @Test
  @DisplayName("secret shorter than 32 bytes is rejected")
  void shortSecretIsRejected() {
    JwtConfig config = new JwtConfig();
    MarkitdoneProperties properties = propertiesWithSecret("0123456789abcdef0123456789abcde");

    assertThatThrownBy(() -> config.jwtSecretKey(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  @DisplayName("secret length is measured in UTF-8 bytes, not characters")
  void multiByteSecretShorterThanThirtyTwoBytesIsRejected() {
    // 15 characters of 2 bytes each = 30 bytes
    JwtConfig config = new JwtConfig();
    MarkitdoneProperties properties = propertiesWithSecret("ééééééééééééééé");

    assertThatThrownBy(() -> config.jwtSecretKey(properties))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("missing secret is rejected")
  void missingSecretIsRejected() {
    JwtConfig config = new JwtConfig();

    assertThatThrownBy(() -> config.jwtSecretKey(propertiesWithSecret(null)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static MarkitdoneProperties propertiesWithSecret(String secret) {
    return new MarkitdoneProperties(
        "http://localhost:4200",
        "no-reply@markitdone.local",
        new MarkitdoneProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(14)),
        new MarkitdoneProperties.Cookies(false),
        new MarkitdoneProperties.Recurrence(3));
  }
}
