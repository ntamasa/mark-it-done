package com.ntamasa.markitdone.common;

import com.ntamasa.markitdone.user.ThemePreference;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller for testing error handling in @WebMvcTest. */
@RestController
@RequestMapping("/api/test")
public class TestErrorController {

  /** Endpoint that throws an ApiException with EMAIL_TAKEN error code. */
  @GetMapping("/api-exception")
  public void throwApiException() {
    throw new ApiException(ErrorCode.EMAIL_TAKEN, "taken");
  }

  /** Endpoint that throws an unexpected exception. */
  @GetMapping("/unexpected-exception")
  public void throwUnexpectedException() {
    throw new RuntimeException("Something went wrong");
  }

  /** Endpoint that validates a request body. */
  @PostMapping("/validate")
  public void validateRequest(@Valid @RequestBody TestRequest request) {
    // Do nothing, just validate
  }

  /** Endpoint that reads a request body with an enum field. */
  @PostMapping("/theme")
  public void readTheme(@RequestBody TestThemeRequest request) {
    // Do nothing, the body must only be readable
  }

  /** Test request DTO with an enum field. */
  public record TestThemeRequest(ThemePreference themePreference) {}

  /** Test request DTO for validation testing. */
  public record TestRequest(
      @jakarta.validation.constraints.NotBlank(message = "must not be blank") String name) {}
}
