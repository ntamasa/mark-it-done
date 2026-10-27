package com.ntamasa.markitdone.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs HTTP access information for each request (excluding actuator endpoints). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestUri = request.getRequestURI();

    // Skip actuator endpoints
    if (requestUri.startsWith("/actuator")) {
      filterChain.doFilter(request, response);
      return;
    }

    long startTime = System.currentTimeMillis();

    try {
      filterChain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      int status = response.getStatus();
      String method = request.getMethod();

      logger.info("http {} {} -> {} in {}ms", method, requestUri, status, duration);
    }
  }
}
