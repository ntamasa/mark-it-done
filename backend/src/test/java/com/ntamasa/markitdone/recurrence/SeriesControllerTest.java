package com.ntamasa.markitdone.recurrence;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import jakarta.servlet.Filter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SeriesControllerTest extends AbstractPostgresIntegrationTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;
  @Autowired private Filter springSecurityFilterChain;
  @Autowired private TestUsers testUsers;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }

  private static String body(String weekdays) {
    return "{\"name\":\"Gym\",\"description\":null,\"dueTime\":\"18:00\",\"priority\":\"HIGH\","
        + "\"categoryId\":null,\"rule\":{\"frequency\":\"WEEKLY\",\"intervalDays\":null,"
        + "\"weekdays\":"
        + weekdays
        + ",\"monthDays\":null,\"startDate\":\"2026-10-21\",\"endType\":\"AFTER_COUNT\","
        + "\"endCount\":20,\"endDate\":null}}";
  }

  @Test
  void postCreatesSeries() throws Exception {
    User user = testUsers.create("series-" + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/series")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content(body("[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.ruleSummary").value("Weekly: Mon, Wed, Fri · 20 times"))
        .andExpect(jsonPath("$.rule.weekdays[0]").value("MONDAY"));
  }

  @Test
  void postWithEmptyWeekdaysIsRejected() throws Exception {
    User user = testUsers.create("series-" + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/series")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content(body("[]")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postWithoutLoginIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/series").with(csrf()).contentType("application/json").content(body("[]")))
        .andExpect(status().isUnauthorized());
  }
}
