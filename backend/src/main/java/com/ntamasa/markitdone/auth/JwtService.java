package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtEncoder jwtEncoder;
  private final Duration accessTtl;

  public JwtService(JwtEncoder jwtEncoder, MarkitdoneProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.accessTtl = properties.jwt().accessTtl();
  }

  public String issueAccessToken(UUID userId) {
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(accessTtl))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public Duration accessTtl() {
    return accessTtl;
  }
}
