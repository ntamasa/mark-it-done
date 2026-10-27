package com.ntamasa.markitdone.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assigns a request ID to each request and makes it available via MDC and response header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String MDC_REQUEST_ID_KEY = "requestId";
  private static final String MDC_USER_ID_KEY = "userId";
  private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

  private static final ThreadLocal<String> currentRequestId = new ThreadLocal<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = extractOrGenerateRequestId(request);
    currentRequestId.set(requestId);
    MDC.put(MDC_REQUEST_ID_KEY, requestId);

    response.addHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID_KEY);
      MDC.remove(MDC_USER_ID_KEY);
      currentRequestId.remove();
    }
  }

  private String extractOrGenerateRequestId(HttpServletRequest request) {
    String headerValue = request.getHeader(REQUEST_ID_HEADER);

    if (headerValue != null && !headerValue.isEmpty()) {
      if (VALID_REQUEST_ID.matcher(headerValue).matches()) {
        return headerValue;
      }
    }

    return UUID.randomUUID().toString();
  }

  /** Returns the current request ID for this thread. */
  public static String current() {
    return currentRequestId.get();
  }
}
