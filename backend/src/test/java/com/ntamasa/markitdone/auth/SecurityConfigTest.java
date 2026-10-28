package com.ntamasa.markitdone.auth;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SecurityConfigTest extends AbstractPostgresIntegrationTest {

  // Built by hand: Boot's MockMvc auto-configuration adds a CSRF token to every request.
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;
  @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
  @Autowired private JwtService jwtService;
  @Autowired private JwtEncoder jwtEncoder;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }

  @Test
  void noCookieIsUnauthorizedProblemJson() throws Exception {
    mockMvc
        .perform(get("/api/test/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
        .andExpect(jsonPath("$.detail", is("Authentication required")))
        .andExpect(jsonPath("$.status", is(401)));
  }

  @Test
  void validCookieReturnsUserId() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(
            get("/api/test/me")
                .cookie(new Cookie(AuthCookies.ACCESS, jwtService.issueAccessToken(id))))
        .andExpect(status().isOk())
        .andExpect(content().string(id.toString()));
  }

  @Test
  void expiredCookieIsUnauthorized() throws Exception {
    Instant past = Instant.now().minusSeconds(3600);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .subject(UUID.randomUUID().toString())
            .issuedAt(past)
            .expiresAt(past.plusSeconds(60))
            .build();
    String expired =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mockMvc
        .perform(get("/api/test/me").cookie(new Cookie(AuthCookies.ACCESS, expired)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
  }

  @Test
  void garbageCookieIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/test/me").cookie(new Cookie(AuthCookies.ACCESS, "garbage")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
  }

  @Test
  void garbageCookieIsIgnoredOnPublicAuthEndpoint() throws Exception {
    // No controller exists yet for /api/auth/login: it must reach routing (404), not fail with 401.
    mockMvc
        .perform(
            post("/api/auth/login").with(csrf()).cookie(new Cookie(AuthCookies.ACCESS, "garbage")))
        .andExpect(status().is(not(401)))
        .andExpect(status().is(not(403)));
  }

  @Test
  void postWithoutCsrfTokenIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/test/echo")
                .cookie(
                    new Cookie(AuthCookies.ACCESS, jwtService.issueAccessToken(UUID.randomUUID()))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code", is("FORBIDDEN")))
        .andExpect(jsonPath("$.detail", is("Access denied")));
  }

  @Test
  void postWithCsrfTokenSucceeds() throws Exception {
    mockMvc
        .perform(
            post("/api/test/echo")
                .with(csrf())
                .cookie(
                    new Cookie(AuthCookies.ACCESS, jwtService.issueAccessToken(UUID.randomUUID()))))
        .andExpect(status().isOk());
  }

  @Test
  void actuatorHealthIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
