package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PasswordResetServiceTest extends AbstractPostgresIntegrationTest {

  @MockitoBean private JavaMailSender mailSender;

  @Autowired private PasswordResetService service;
  @Autowired private PasswordResetTokenRepository tokenRepository;
  @Autowired private TestUsers testUsers;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User user;

  @BeforeEach
  void setUp() {
    reset(mailSender);
    user = testUsers.create("reset-" + UUID.randomUUID() + "@example.test");
  }

  private String sendRequestAndGetToken() {
    service.requestReset(user.getEmail());
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
    SimpleMailMessage last = captor.getAllValues().getLast();
    Matcher m = Pattern.compile("/reset-password\\?token=(\\S+)").matcher(last.getText());
    assertThat(m.find()).isTrue();
    return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("a known email gets exactly one mail with the link, and the token is stored hashed")
  void knownEmailSendsOneMail() {
    service.requestReset(user.getEmail());

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage message = captor.getValue();
    assertThat(message.getTo()).containsExactly(user.getEmail());
    assertThat(message.getSubject()).isEqualTo("Reset your markitDONE password");
    assertThat(message.getText())
        .contains("/reset-password?token=")
        .contains("Hi Test User")
        .contains("If you didn't ask for this, you can ignore this email.");
    Matcher m = Pattern.compile("token=(\\S+)").matcher(message.getText());
    assertThat(m.find()).isTrue();
    String raw = m.group(1);
    assertThat(tokenRepository.findByTokenHash(raw)).isEmpty();
    assertThat(tokenRepository.findByTokenHash(RefreshTokenService.hash(raw))).isPresent();
  }

  @Test
  @DisplayName("an unknown email sends no mail and throws nothing")
  void unknownEmailDoesNothing() {
    assertThatCode(() -> service.requestReset("nobody-" + UUID.randomUUID() + "@example.test"))
        .doesNotThrowAnyException();

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  @DisplayName("a mail failure does not fail the request")
  void mailFailureIsSwallowed() {
    org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("boom"))
        .when(mailSender)
        .send(any(SimpleMailMessage.class));

    assertThatCode(() -> service.requestReset(user.getEmail())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a second request invalidates the first link")
  void secondRequestInvalidatesFirst() {
    String first = sendRequestAndGetToken();
    String second = sendRequestAndGetToken();

    assertThatThrownBy(() -> service.consume(first))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID));
    assertThat(service.consume(second)).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("consume works once, then fails with RESET_TOKEN_INVALID")
  void consumeWorksOnce() {
    String raw = sendRequestAndGetToken();

    assertThat(service.consume(raw)).isEqualTo(user.getId());
    assertThatThrownBy(() -> service.consume(raw))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID));
  }

  @Test
  @DisplayName("an unknown or missing token is invalid")
  void unknownTokenInvalid() {
    for (String bad : new String[] {"nope", "", null}) {
      assertThatThrownBy(() -> service.consume(bad))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID));
    }
  }

  @Test
  @DisplayName("a link older than 30 minutes is invalid")
  void expiredLinkInvalid() {
    String raw = sendRequestAndGetToken();
    jdbcTemplate.update(
        "UPDATE password_reset_tokens SET created_at = created_at - interval '31 minutes',"
            + " expires_at = expires_at - interval '31 minutes' WHERE user_id = ?",
        user.getId());

    assertThatThrownBy(() -> service.consume(raw))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID));
    List<PasswordResetToken> all = tokenRepository.findAll();
    assertThat(all).isNotEmpty();
  }
}
