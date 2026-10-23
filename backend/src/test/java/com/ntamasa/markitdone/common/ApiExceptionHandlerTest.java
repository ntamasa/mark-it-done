package com.ntamasa.markitdone.common;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for ApiExceptionHandler using @WebMvcTest slice with a test-only controller. */
@WebMvcTest(TestErrorController.class)
@Import(ApiExceptionHandler.class)
class ApiExceptionHandlerTest {
  @Autowired private MockMvc mockMvc;

  @Test
  @WithMockUser
  void testApiException() throws Exception {
    mockMvc
        .perform(get("/api/test/api-exception"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", is("EMAIL_TAKEN")))
        .andExpect(jsonPath("$.detail", is("taken")))
        .andExpect(jsonPath("$.status", is(409)));
  }

  @Test
  @WithMockUser
  void testValidationFailure() throws Exception {
    mockMvc
        .perform(
            post("/api/test/validate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.detail", is("Validation failed")))
        .andExpect(jsonPath("$.status", is(400)))
        .andExpect(jsonPath("$.errors[0].field", is("name")))
        .andExpect(jsonPath("$.errors[0].message", is("must not be blank")));
  }

  @Test
  @WithMockUser
  void testUnexpectedException() throws Exception {
    mockMvc
        .perform(get("/api/test/unexpected-exception"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code", is("INTERNAL_ERROR")))
        .andExpect(jsonPath("$.detail", is("Unexpected error")))
        .andExpect(jsonPath("$.status", is(500)));
  }
}
