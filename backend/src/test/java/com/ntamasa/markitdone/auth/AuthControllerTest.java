package com.ntamasa.markitdone.auth;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerTest extends AbstractPostgresIntegrationTest {

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

  private static String body(String email, String password, String tz) {
    return "{\"email\":\"%s\",\"password\":\"%s\",\"rememberMe\":true,\"timezone\":\"%s\"}"
        .formatted(email, password, tz);
  }

  private MvcResultHolder login(User user) throws Exception {
    MvcResult r =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(user.getEmail(), TestUsers.PASSWORD, "UTC")))
            .andExpect(status().isOk())
            .andReturn();
    return new MvcResultHolder(r);
  }

  private record MvcResultHolder(MvcResult result) {
    String refresh() {
      return result.getResponse().getCookie(AuthCookies.REFRESH).getValue();
    }
  }

  private List<AuditLogEntry> audits(User user) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId());
  }

  @Test
  void loginSucceeds() throws Exception {
    User user = testUsers.create(email());
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(body(user.getEmail(), TestUsers.PASSWORD, "Europe/Budapest")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", is(user.getEmail())))
        .andExpect(jsonPath("$.timezone", is("Europe/Budapest")))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(cookie().exists(AuthCookies.ACCESS))
        .andExpect(cookie().exists(AuthCookies.REFRESH));
    assertThat(audits(user)).anyMatch(a -> a.getAction().equals("LOGIN_SUCCEEDED"));
    assertThat(userRepository.findById(user.getId()).orElseThrow().getTimezone())
        .isEqualTo("Europe/Budapest");
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveIdenticalResponses() throws Exception {
    User user = testUsers.create(email());
    String wrong =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(user.getEmail(), "Wrong-pass1!", "UTC")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
            .andExpect(jsonPath("$.detail", is("Email or password is incorrect.")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknown =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(body(email(), "Wrong-pass1!", "UTC")))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(unknown).isEqualTo(wrong);
    assertThat(audits(user)).anyMatch(a -> a.getAction().equals("LOGIN_FAILED"));
  }

  @Test
  void loginBodyWithoutRememberMeAndTimezoneIsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\"nobody-"
                        + UUID.randomUUID()
                        + "@example.test\","
                        + "\"password\":\"irrelevant-Passw0rd!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
  }

  @Test
  void refreshRotatesAndOldCookieIsRejected() throws Exception {
    User user = testUsers.create(email());
    String old = login(user).refresh();

    MvcResult rotated =
        mockMvc
            .perform(
                post("/api/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookies.REFRESH, old)))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(rotated.getResponse().getCookie(AuthCookies.REFRESH).getValue()).isNotEqualTo(old);

    MvcResult rejected =
        mockMvc
            .perform(
                post("/api/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookies.REFRESH, old)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
            .andExpect(jsonPath("$.detail", is("Session expired. Please log in again.")))
            .andReturn();
    List<String> setCookies = rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).hasSize(2).allMatch(c -> c.contains("Max-Age=0"));
  }

  @Test
  void logoutRevokesSession() throws Exception {
    User user = testUsers.create(email());
    String refresh = login(user).refresh();
    mockMvc
        .perform(
            post("/api/auth/logout").with(csrf()).cookie(new Cookie(AuthCookies.REFRESH, refresh)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookies.REFRESH, refresh)))
        .andExpect(status().isUnauthorized());
    assertThat(audits(user)).anyMatch(a -> a.getAction().equals("LOGOUT"));
  }

  @Test
  void logoutWithoutCookieIsNoContent() throws Exception {
    mockMvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void meReturnsUserWithCookieAndUnauthorizedWithout() throws Exception {
    User user = testUsers.create(email());
    mockMvc
        .perform(get("/api/auth/me").cookie(testUsers.accessCookie(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(user.getId().toString())));
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void csrfEndpointSetsXsrfCookie() throws Exception {
    mockMvc
        .perform(get("/api/auth/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(content().string(not(containsString("token"))));
  }
}
