package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import com.ntamasa.markitdone.user.User;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the password reset email. Failures are logged and never propagated. */
@Component
public class ResetMailer {

  static final String SUBJECT = "Reset your markitDONE password";

  private static final Logger logger = LoggerFactory.getLogger(ResetMailer.class);

  private final JavaMailSender mailSender;
  private final String from;
  private final String appBaseUrl;

  public ResetMailer(JavaMailSender mailSender, MarkitdoneProperties properties) {
    this.mailSender = mailSender;
    this.from = properties.mailFrom();
    this.appBaseUrl = properties.appBaseUrl();
  }

  /**
   * Sends the reset link to the user. A mail failure is logged as a warning.
   *
   * @param user the recipient
   * @param rawToken the raw reset token
   */
  public void send(User user, String rawToken) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(user.getEmail());
      message.setSubject(SUBJECT);
      message.setText(body(user.getDisplayName(), rawToken));
      mailSender.send(message);
    } catch (RuntimeException e) {
      logger.warn("Failed to send password reset email to user {}", user.getId(), e);
    }
  }

  String body(String displayName, String rawToken) {
    String link =
        appBaseUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    return "Hi "
        + displayName
        + ",\n\n"
        + "Use this link to reset your password:\n"
        + link
        + "\n\n"
        + "The link works once and expires in 30 minutes.\n\n"
        + "If you didn't ask for this, you can ignore this email.\n\n"
        + "— markitDONE\n";
  }
}
