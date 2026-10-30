package com.ntamasa.markitdone.user;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
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
class UserControllerTest extends AbstractPostgresIntegrationTest {

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

  private List<AuditLogEntry> audits(User user) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId());
  }

  private List<AuditLogEntry> preferenceAudits(User user) {
    return audits(user).stream()
        .filter(a -> a.getAction().equals("USER_PREFERENCES_UPDATED"))
        .toList();
  }

  @Test
  void themeDarkIsSavedAndAudited() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"themePreference\":\"DARK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.themePreference", is("DARK")))
        .andExpect(jsonPath("$.timezone", is("UTC")));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getThemePreference())
        .isEqualTo(ThemePreference.DARK);
    List<AuditLogEntry> entries = preferenceAudits(user);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getBeforeState()).contains("SYSTEM");
    assertThat(entries.get(0).getAfterState()).contains("DARK");
  }

  @Test
  void timezoneNewYorkIsSaved() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"timezone\":\"America/New_York\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone", is("America/New_York")))
        .andExpect(jsonPath("$.themePreference", is("SYSTEM")));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getTimezone())
        .isEqualTo("America/New_York");
  }

  @Test
  void nullFieldKeepsStoredValue() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"timezone\":\"Europe/Berlin\",\"themePreference\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone", is("Europe/Berlin")))
        .andExpect(jsonPath("$.themePreference", is("SYSTEM")));
  }

  @Test
  void unknownTimezoneIsValidationFailure() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"timezone\":\"Mars/Base\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.detail", is("Validation failed")))
        .andExpect(jsonPath("$.errors[0].field", is("timezone")))
        .andExpect(jsonPath("$.errors[0].message", is("Unknown timezone")));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getTimezone()).isEqualTo("UTC");
  }

  @Test
  void unknownThemeIsValidationFailure() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"themePreference\":\"PURPLE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

    assertThat(userRepository.findById(user.getId()).orElseThrow().getThemePreference())
        .isEqualTo(ThemePreference.SYSTEM);
  }

  @Test
  void unauthenticatedIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .contentType("application/json")
                .content("{\"themePreference\":\"DARK\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void emptyBodyIsNoOp() throws Exception {
    User user = testUsers.create(email());

    mockMvc
        .perform(
            patch("/api/users/me")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone", is("UTC")))
        .andExpect(jsonPath("$.themePreference", is("SYSTEM")));

    assertThat(preferenceAudits(user)).isEmpty();
  }
}
