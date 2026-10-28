package com.ntamasa.markitdone.auth;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RegisterControllerTest extends AbstractPostgresIntegrationTest {

  private static final String STRONG_PASSWORD = "Strong-Passw0rd!";

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;
  @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
  @Autowired private TestUsers testUsers;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }

  private static String email() {
    return "u-" + UUID.randomUUID() + "@example.com";
  }

  private static String body(String displayName, String email, String password, String tz) {
    return "{\"displayName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"timezone\":\"%s\"}"
        .formatted(displayName, email, password, tz);
  }

  private List<AuditLogEntry> audits(User user) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId());
  }

  @Test
  void happyPathCreatesUserWithSessionCookies() throws Exception {
    String email = "Mixed-" + UUID.randomUUID() + "@Example.com";
    String response =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body("  Ada Lovelace  ", email, STRONG_PASSWORD, "Europe/Budapest")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is(email.toLowerCase(java.util.Locale.ROOT))))
            .andExpect(jsonPath("$.displayName", is("Ada Lovelace")))
            .andExpect(jsonPath("$.timezone", is("Europe/Budapest")))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(cookie().exists(AuthCookies.ACCESS))
            .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
            .andExpect(cookie().exists(AuthCookies.REFRESH))
            .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("passwordHash");

    User saved = userRepository.findByEmail(email.toLowerCase(java.util.Locale.ROOT)).orElseThrow();
    assertThat(saved.getPasswordHash()).startsWith("{argon2}");
    assertThat(audits(saved))
        .anyMatch(
            a -> a.getAction().equals("USER_REGISTERED") && a.getUserId().equals(saved.getId()));
  }

  @Test
  void duplicateEmailInDifferentCaseIsConflict() throws Exception {
    User existing = testUsers.create(email());
    mockMvc
        .perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    body(
                        "Someone",
                        existing.getEmail().toUpperCase(java.util.Locale.ROOT),
                        STRONG_PASSWORD,
                        "UTC")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", is("EMAIL_TAKEN")))
        .andExpect(jsonPath("$.detail", is("An account with this email already exists.")));
  }

  @Test
  void weakPasswordIsValidationFailureOnPasswordField() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("Ada", email(), "weakpassword", "UTC")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.errors[0].field", is("password")));
  }

  @Test
  void unknownTimezoneFallsBackToUtc() throws Exception {
    String email = email();
    mockMvc
        .perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("Ada", email, STRONG_PASSWORD, "Mars/Base")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.timezone", is("UTC")));
    assertThat(userRepository.findByEmail(email).orElseThrow().getTimezone()).isEqualTo("UTC");
  }

  @Test
  void missingCsrfTokenIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(body("Ada", email(), STRONG_PASSWORD, "UTC")))
        .andExpect(status().isForbidden());
  }

  @Test
  void responseBodyNeverContainsPasswordHash() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("Ada", email(), STRONG_PASSWORD, "UTC")))
        .andExpect(status().isCreated())
        .andExpect(content().string(not(containsString("passwordHash"))))
        .andExpect(content().string(not(containsString("{argon2}"))));
  }
}
