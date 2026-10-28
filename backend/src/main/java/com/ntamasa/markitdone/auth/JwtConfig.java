package com.ntamasa.markitdone.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ntamasa.markitdone.config.MarkitdoneProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

  public static final String ISSUER = "markitdone";

  static final int MIN_SECRET_BYTES = 32;

  private static final String HMAC_SHA256 = "HmacSHA256";

  @Bean
  public SecretKey jwtSecretKey(MarkitdoneProperties properties) {
    String secret = properties.jwt().secret();
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "markitdone.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    return decoder;
  }
}
