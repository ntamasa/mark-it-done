package com.ntamasa.markitdone.support;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Adds a matching XSRF-TOKEN cookie and X-XSRF-TOKEN header to a request. Unlike Spring Security's
 * {@code csrf()} post-processor it does not replace the token repository of the shared, cached
 * security filter chain.
 */
public final class CsrfTestSupport {

  public static final String VALUE = "test-csrf-token";

  private CsrfTestSupport() {}

  /**
   * Returns a post-processor that makes the request pass CSRF validation.
   *
   * @return the post-processor
   */
  public static RequestPostProcessor csrf() {
    return request -> {
      Cookie[] existing = request.getCookies();
      List<Cookie> all = new ArrayList<>(existing == null ? List.of() : List.of(existing));
      all.add(new Cookie("XSRF-TOKEN", VALUE));
      request.setCookies(all.toArray(new Cookie[0]));
      request.addHeader("X-XSRF-TOKEN", VALUE);
      return request;
    };
  }
}
