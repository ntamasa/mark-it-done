package com.ntamasa.markitdone.auth;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ResetPasswordControllerTest extends AbstractPostgresIntegrationTest {

  private static final String NEW_PASSWORD = "Brand-new-pass1!";

  private MockMvc mockMvc;

  @MockitoBean private JavaMailSender mailSender;

  @Autowired private WebApplicationContext context;
  @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
  @Autowired private TestUsers testUsers;
  @Autowired private AuditLogRepository auditLogRepository;

  private User user;

  @BeforeEach
  void setUp() {
    reset(mailSender);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    user = testUsers.create("fp-" + UUID.randomUUID() + "@example.com");
  }

  private String forgot(String email) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"%s\"}".formatted(email)))
        .andExpect(status().isAccepted())
        .andExpect(
            jsonPath(
                "$.status", is("If an account exists for this email, a reset link has been sent.")))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String requestTokenFromMail() throws Exception {
    forgot(user.getEmail());
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, atLeastOnce()).send(captor.capture());
    Matcher m =
        Pattern.compile("/reset-password\\?token=(\\S+)")
            .matcher(captor.getAllValues().getLast().getText());
    assertThat(m.find()).isTrue();
    return java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
  }

  private org.springframework.test.web.servlet.ResultActions resetWith(
      String token, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/reset-password")
            .with(csrf())
            .contentType("application/json")
            .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
  }

  private org.springframework.test.web.servlet.ResultActions login(String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .with(csrf())
            .contentType("application/json")
            .content(
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), password)));
  }

  @Test
  void forgotGivesIdenticalBodyForKnownAndUnknownEmail() throws Exception {
    String known = forgot(user.getEmail());
    String unknown = forgot("nobody-" + UUID.randomUUID() + "@example.com");
    assertThat(unknown).isEqualTo(known);
  }

  @Test
  void resetChangesPasswordClearsCookiesAndAudits() throws Exception {
    String token = requestTokenFromMail();

    resetWith(token, NEW_PASSWORD)
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
        .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));

    login(NEW_PASSWORD).andExpect(status().isOk());
    login(TestUsers.PASSWORD).andExpect(status().isUnauthorized());
    assertThat(auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId()))
        .anyMatch(a -> a.getAction().equals("PASSWORD_RESET_COMPLETED"));
  }

  @Test
  void refreshCookieIssuedBeforeResetIsRejectedAfterwards() throws Exception {
    String refresh =
        login(TestUsers.PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(AuthCookies.REFRESH)
            .getValue();
    String token = requestTokenFromMail();
    resetWith(token, NEW_PASSWORD).andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookies.REFRESH, refresh)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void weakPasswordIsRejectedAndTokenStaysUsable() throws Exception {
    String token = requestTokenFromMail();

    resetWith(token, "weak").andExpect(status().isBadRequest());
    resetWith(token, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void usedTokenIsRejected() throws Exception {
    String token = requestTokenFromMail();
    resetWith(token, NEW_PASSWORD).andExpect(status().isNoContent());

    resetWith(token, "Another-pass1!")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("RESET_TOKEN_INVALID")));
  }

  @Test
  void resetRequiresCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/reset-password")
                .contentType("application/json")
                .content("{\"token\":\"x\",\"password\":\"%s\"}".formatted(NEW_PASSWORD)))
        .andExpect(status().isForbidden());
  }
}
