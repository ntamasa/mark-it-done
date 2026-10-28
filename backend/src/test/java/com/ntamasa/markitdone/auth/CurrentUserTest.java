package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsSubjectOfJwtAuthentication() {
    UUID id = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject(id.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

    assertThat(CurrentUser.id()).isEqualTo(id);
  }

  @Test
  void throwsUnauthorizedWithoutAuthentication() {
    assertThatThrownBy(CurrentUser::id)
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  }
}
