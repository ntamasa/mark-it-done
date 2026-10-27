package com.ntamasa.markitdone.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void testNoHeaderGeneratesUUID() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    String responseHeader = response.getHeader("X-Request-Id");
    assertThat(responseHeader).isNotNull();
    assertThat(responseHeader).matches(uuidPattern());
  }

  @Test
  void testValidHeaderIsEchoed() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "abcd1234-ef");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    String responseHeader = response.getHeader("X-Request-Id");
    assertThat(responseHeader).isEqualTo("abcd1234-ef");
  }

  @Test
  void testInvalidHeaderIsReplaced() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "<script>");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    String responseHeader = response.getHeader("X-Request-Id");
    assertThat(responseHeader).isNotEqualTo("<script>");
    assertThat(responseHeader).matches(uuidPattern());
  }

  @Test
  void testCurrentReturnsIdInsideFilterChain() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "test1234-abcd");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ArgumentCaptor<FilterChain> chainCaptor = ArgumentCaptor.forClass(FilterChain.class);
    FilterChain filterChain =
        (req, res) -> {
          // Inside the filter chain, current() should return the request ID
          String currentId = RequestIdFilter.current();
          assertThat(currentId).isEqualTo("test1234-abcd");
        };

    filter.doFilter(request, response, filterChain);
  }

  @Test
  void testMDCIsEmptyAfterRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "test1234-abcd");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    // After the request, MDC should be empty (both requestId and userId)
    assertThat(MDC.get("requestId")).isNull();
    assertThat(MDC.get("userId")).isNull();
  }

  @Test
  void testMDCContainsRequestIdDuringRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "test1234-abcd");
    MockHttpServletResponse response = new MockHttpServletResponse();

    FilterChain filterChain =
        (req, res) -> {
          // During the request, MDC should contain the request ID
          String mdcRequestId = MDC.get("requestId");
          assertThat(mdcRequestId).isEqualTo("test1234-abcd");
        };

    filter.doFilter(request, response, filterChain);
  }

  @Test
  void testCurrentReturnsNullAfterRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "test1234-abcd");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    // After the request, current() should return null
    assertThat(RequestIdFilter.current()).isNull();
  }

  private String uuidPattern() {
    // Standard UUID pattern: 8-4-4-4-12 hexadecimal characters with dashes
    return "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  }
}
