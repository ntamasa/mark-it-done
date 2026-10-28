package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

  private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long-123456";
  private static final String OTHER_SECRET = "0123456789abcdef0123456789abcdef";

  private JwtConfig config;
  private SecretKey key;
  private MarkitdoneProperties properties;
  private JwtService jwtService;
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    config = new JwtConfig();
    properties = propertiesWithTtl(Duration.ofMinutes(15));
    key = config.jwtSecretKey(properties);
    jwtService = new JwtService(config.jwtEncoder(key), properties);
    jwtDecoder = config.jwtDecoder(key);
  }

  @Test
  @DisplayName("issued access token decodes with the app decoder and its subject is the user id")
  void issuedTokenHasUserIdAsSubject() {
    UUID userId = UUID.randomUUID();

    Jwt jwt = jwtDecoder.decode(jwtService.issueAccessToken(userId));

    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
  }

  @Test
  @DisplayName("issued access token has issuer markitdone")
  void issuedTokenHasMarkitdoneIssuer() {
    Jwt jwt = jwtDecoder.decode(jwtService.issueAccessToken(UUID.randomUUID()));

    assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("markitdone");
    assertThat(JwtConfig.ISSUER).isEqualTo("markitdone");
  }

  @Test
  @DisplayName("expiry minus issued-at is 15 minutes")
  void issuedTokenLifetimeIsAccessTtl() {
    Jwt jwt = jwtDecoder.decode(jwtService.issueAccessToken(UUID.randomUUID()));

    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(jwtService.accessTtl()).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  @DisplayName("issued-at is current and every token gets a unique token id")
  void issuedTokensHaveCurrentIssuedAtAndUniqueIds() {
    Instant before = Instant.now().minusSeconds(1);

    Jwt first = jwtDecoder.decode(jwtService.issueAccessToken(UUID.randomUUID()));
    Jwt second = jwtDecoder.decode(jwtService.issueAccessToken(UUID.randomUUID()));

    assertThat(first.getIssuedAt()).isAfterOrEqualTo(before);
    assertThat(first.getId()).isNotBlank();
    assertThat(second.getId()).isNotEqualTo(first.getId());
  }

  @Test
  @DisplayName("token signed with a different 32-byte key is rejected by the app decoder")
  void tokenSignedWithOtherKeyIsRejected() {
    SecretKey otherKey = new SecretKeySpec(OTHER_SECRET.getBytes(), "HmacSHA256");
    JwtService otherService = new JwtService(config.jwtEncoder(otherKey), properties);

    assertThatThrownBy(() -> jwtDecoder.decode(otherService.issueAccessToken(UUID.randomUUID())))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("token with issuer other is rejected by the app decoder")
  void tokenWithOtherIssuerIsRejected() {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("other")
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();

    assertThatThrownBy(() -> jwtDecoder.decode(encode(claims))).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("expired token is rejected by the app decoder")
  void expiredTokenIsRejected() {
    Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .subject(UUID.randomUUID().toString())
            .issuedAt(twoHoursAgo)
            .expiresAt(twoHoursAgo.plus(Duration.ofMinutes(15)))
            .build();

    assertThatThrownBy(() -> jwtDecoder.decode(encode(claims))).isInstanceOf(JwtException.class);
  }

  private String encode(JwtClaimsSet claims) {
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return config.jwtEncoder(key).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static MarkitdoneProperties propertiesWithTtl(Duration accessTtl) {
    return new MarkitdoneProperties(
        "http://localhost:4200",
        "no-reply@markitdone.local",
        new MarkitdoneProperties.Jwt(SECRET, accessTtl, Duration.ofDays(14)),
        new MarkitdoneProperties.Cookies(false),
        new MarkitdoneProperties.Recurrence(3));
  }
}
