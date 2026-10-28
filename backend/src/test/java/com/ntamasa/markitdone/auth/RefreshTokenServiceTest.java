package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.user.ThemePreference;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenServiceTest extends AbstractPostgresIntegrationTest {

  @Autowired private RefreshTokenService refreshTokenService;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID userId;

  @BeforeEach
  void createUser() {
    User user =
        new User(
            "user-" + UUID.randomUUID() + "@example.test",
            "Test User",
            "not-a-real-hash",
            "UTC",
            ThemePreference.SYSTEM);
    userId = userRepository.save(user).getId();
  }

  @Test
  @DisplayName("issuing stores a 64-char hash that differs from the raw token")
  void issuingStoresHashNotRawToken() {
    RefreshTokenService.Issued issued = refreshTokenService.issue(userId, false, null);

    RefreshToken stored = refreshTokenRepository.findById(issued.token().getId()).orElseThrow();
    assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.rawToken());
    assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.rawToken()));
  }

  @Test
  @DisplayName("rotating a valid token returns rotated; old row is revoked and points to successor")
  void rotatingValidTokenRotates() {
    RefreshTokenService.Issued first = refreshTokenService.issue(userId, true, null);

    RotationResult result = refreshTokenService.rotate(first.rawToken());

    assertThat(result).isInstanceOf(RotationResult.Rotated.class);
    RotationResult.Rotated rotated = (RotationResult.Rotated) result;
    assertThat(rotated.userId()).isEqualTo(userId);
    assertThat(rotated.rememberMe()).isTrue();
    assertThat(rotated.next().token().getFamilyId()).isEqualTo(first.token().getFamilyId());

    RefreshToken old = refreshTokenRepository.findById(first.token().getId()).orElseThrow();
    assertThat(old.getRevokedAt()).isNotNull();
    assertThat(old.getReplacedBy()).isEqualTo(rotated.next().token().getId());
    assertThat(rotated.next().token().isRememberMe()).isTrue();
  }

  @Test
  @DisplayName("the successor of a rotation can be rotated again")
  void successorRotatesFine() {
    RefreshTokenService.Issued first = refreshTokenService.issue(userId, false, null);
    RotationResult.Rotated second =
        (RotationResult.Rotated) refreshTokenService.rotate(first.rawToken());

    RotationResult third = refreshTokenService.rotate(second.next().rawToken());

    assertThat(third).isInstanceOf(RotationResult.Rotated.class);
  }

  @Test
  @DisplayName(
      "rotating a successor's predecessor again is rejected as reuse and revokes the family")
  void reuseRevokesFamilyAndAudits() {
    RefreshTokenService.Issued first = refreshTokenService.issue(userId, false, null);
    RotationResult.Rotated second =
        (RotationResult.Rotated) refreshTokenService.rotate(first.rawToken());

    RotationResult reuse = refreshTokenService.rotate(first.rawToken());

    assertThat(reuse).isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_REUSE));
    RefreshToken successor =
        refreshTokenRepository.findById(second.next().token().getId()).orElseThrow();
    assertThat(successor.getRevokedAt()).isNotNull();
    assertThat(refreshTokenService.rotate(second.next().rawToken()))
        .isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_REUSE));

    List<AuditLogEntry> audits =
        auditLogRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
            .filter(e -> e.getAction().equals(AuditAction.REFRESH_TOKEN_REUSE_DETECTED.toString()))
            .toList();
    assertThat(audits).isNotEmpty();
  }

  @Test
  @DisplayName("an expired token is rejected as expired")
  void expiredTokenIsRejected() {
    RefreshTokenService.Issued issued = refreshTokenService.issue(userId, false, null);
    jdbcTemplate.update(
        "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE id = ?",
        issued.token().getId());

    RotationResult result = refreshTokenService.rotate(issued.rawToken());

    assertThat(result).isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_EXPIRED));
  }

  @Test
  @DisplayName("an unknown token is rejected as unknown")
  void unknownTokenIsRejected() {
    RotationResult result = refreshTokenService.rotate("does-not-exist");

    assertThat(result).isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_UNKNOWN));
  }

  @Test
  @DisplayName("a missing token is rejected as missing")
  void missingTokenIsRejected() {
    assertThat(refreshTokenService.rotate(null))
        .isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_MISSING));
    assertThat(refreshTokenService.rotate(" "))
        .isEqualTo(new RotationResult.Rejected(RefreshTokenService.REASON_MISSING));
  }

  @Test
  @DisplayName("revoke all for user revokes tokens from two different families")
  void revokeAllForUserRevokesEveryFamily() {
    RefreshTokenService.Issued first = refreshTokenService.issue(userId, false, null);
    RefreshTokenService.Issued second = refreshTokenService.issue(userId, false, null);
    assertThat(first.token().getFamilyId()).isNotEqualTo(second.token().getFamilyId());

    refreshTokenService.revokeAllForUser(userId);

    assertThat(refreshTokenRepository.findById(first.token().getId()).orElseThrow().getRevokedAt())
        .isNotNull();
    assertThat(refreshTokenRepository.findById(second.token().getId()).orElseThrow().getRevokedAt())
        .isNotNull();
  }
}
