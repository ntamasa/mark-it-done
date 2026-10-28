package com.ntamasa.markitdone.auth;

import com.ntamasa.markitdone.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses from the security chain as ProblemDetail JSON with the same shape as
 * the global API exception handler.
 */
@Component
public class ProblemSecurityHandlers {

  private final JsonMapper jsonMapper;

  public ProblemSecurityHandlers(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * Entry point answering 401 UNAUTHORIZED.
   *
   * @return the authentication entry point
   */
  public AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, ex) ->
        write(
            request,
            response,
            HttpStatus.UNAUTHORIZED,
            "Authentication required",
            ErrorCode.UNAUTHORIZED);
  }

  /**
   * Handler answering 403 FORBIDDEN, also used for CSRF failures.
   *
   * @return the access denied handler
   */
  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, ex) ->
        write(request, response, HttpStatus.FORBIDDEN, "Access denied", ErrorCode.FORBIDDEN);
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String detail,
      ErrorCode code)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", String.valueOf(problem.getType()));
    body.put("title", problem.getTitle());
    body.put("status", status.value());
    body.put("detail", detail);
    body.put("instance", request.getRequestURI());
    body.put("code", code.name());
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(jsonMapper.writeValueAsString(body));
  }
}
