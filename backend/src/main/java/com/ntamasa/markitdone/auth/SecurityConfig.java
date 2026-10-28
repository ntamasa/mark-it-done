package com.ntamasa.markitdone.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless resource-server security. The access JWT is read from the md_access cookie, CSRF uses
 * the SPA setup (XSRF-TOKEN cookie plus X-XSRF-TOKEN header), and errors are ProblemDetail JSON.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ProblemSecurityHandlers handlers, JwtDecoder jwtDecoder) throws Exception {
    RequestMatcher publicAuth = publicAuthEndpoints();
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();

    // The bearer filter is wired by hand instead of http.oauth2ResourceServer(): that configurer
    // exempts every request carrying a bearer token from CSRF, which would switch CSRF protection
    // off for cookie-authenticated requests.
    BearerTokenAuthenticationFilter bearerTokenFilter =
        new BearerTokenAuthenticationFilter(
            new ProviderManager(new JwtAuthenticationProvider(jwtDecoder)));
    bearerTokenFilter.setBearerTokenResolver(new CookieBearerTokenResolver(publicAuth));
    bearerTokenFilter.setAuthenticationEntryPoint(handlers.authenticationEntryPoint());

    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(c -> c.disable())
        .formLogin(c -> c.disable())
        .logout(c -> c.disable())
        .requestCache(c -> c.disable())
        .csrf(csrf -> csrf.spa())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(publicAuth)
                    .permitAll()
                    .requestMatchers(
                        paths.matcher("/actuator/health/**"), paths.matcher("/actuator/health"))
                    .permitAll()
                    .requestMatchers(paths.matcher("/actuator/info"))
                    .permitAll()
                    .requestMatchers(paths.matcher("/api/**"))
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(handlers.authenticationEntryPoint())
                    .accessDeniedHandler(handlers.accessDeniedHandler()))
        .addFilterBefore(bearerTokenFilter, AuthorizationFilter.class)
        .addFilterAfter(new UserMdcFilter(), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  private static RequestMatcher publicAuthEndpoints() {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    return new OrRequestMatcher(
        paths.matcher(HttpMethod.GET, "/api/auth/csrf"),
        paths.matcher(HttpMethod.POST, "/api/auth/register"),
        paths.matcher(HttpMethod.POST, "/api/auth/login"),
        paths.matcher(HttpMethod.POST, "/api/auth/refresh"),
        paths.matcher(HttpMethod.POST, "/api/auth/logout"),
        paths.matcher(HttpMethod.POST, "/api/auth/forgot-password"),
        paths.matcher(HttpMethod.POST, "/api/auth/reset-password"));
  }
}
