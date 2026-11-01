package com.ntamasa.markitdone.todo;

import static com.ntamasa.markitdone.support.CsrfTestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class TodoControllerTest extends AbstractPostgresIntegrationTest {

  private static final String BASE = "/api/todos";

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;
  @Autowired private Filter springSecurityFilterChain;
  @Autowired private TestUsers testUsers;
  @Autowired private TodoRepository todoRepository;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }

  private User newUser() {
    return testUsers.create("todo-" + UUID.randomUUID() + "@example.com");
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  private Todo seed(User user, String name, LocalDate due, Priority priority) {
    return todoRepository.save(new Todo(user.getId(), name, null, due, null, priority, null));
  }

  private static String createBody(String name, LocalDate due) {
    return "{\"name\":\"" + name + "\",\"dueDate\":\"" + due + "\",\"priority\":\"LOW\"}";
  }

  @Test
  void postValidAllDayFutureTodoIsCreated() throws Exception {
    User user = newUser();
    LocalDate future = today().plusDays(30);

    mockMvc
        .perform(
            post(BASE)
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content(createBody("Buy milk", future)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.name", is("Buy milk")))
        .andExpect(jsonPath("$.dueDate", is(future.toString())))
        .andExpect(jsonPath("$.dueTime", nullValue()))
        .andExpect(jsonPath("$.expired", is(false)))
        .andExpect(jsonPath("$.done", is(false)));
  }

  @Test
  void postBlankNameIsValidationFailure() throws Exception {
    User user = newUser();

    mockMvc
        .perform(
            post(BASE)
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content(createBody(" ", today().plusDays(1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.errors[0].field", is("name")));
  }

  @Test
  void getRangeReturnsOnlyTheWindowSorted() throws Exception {
    User user = newUser();
    LocalDate from = today();
    LocalDate to = from.plusDays(6);
    seed(user, "Before", from.minusDays(1), Priority.LOW);
    seed(user, "Last", to, Priority.LOW);
    seed(user, "Middle", from.plusDays(3), Priority.LOW);
    seed(user, "First", from, Priority.LOW);
    seed(user, "After", to.plusDays(1), Priority.LOW);

    mockMvc
        .perform(
            get(BASE)
                .param("from", from.toString())
                .param("to", to.toString())
                .cookie(testUsers.accessCookie(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].name", is("First")))
        .andExpect(jsonPath("$[0].dueDate", is(from.toString())))
        .andExpect(jsonPath("$[1].name", is("Middle")))
        .andExpect(jsonPath("$[2].name", is("Last")))
        .andExpect(jsonPath("$[2].dueDate", is(to.toString())));
  }

  @Test
  void getRangeWithoutFromIsBadRequest() throws Exception {
    User user = newUser();

    mockMvc
        .perform(get(BASE).param("to", today().toString()).cookie(testUsers.accessCookie(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
  }

  @Test
  void getRangeWithMalformedDateIsBadRequest() throws Exception {
    User user = newUser();

    mockMvc
        .perform(
            get(BASE)
                .param("from", "07.11.2026")
                .param("to", today().toString())
                .cookie(testUsers.accessCookie(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
  }

  @Test
  void getRangeLongerThanAllowedIsBadRequest() throws Exception {
    User user = newUser();
    LocalDate from = today();

    mockMvc
        .perform(
            get(BASE)
                .param("from", from.toString())
                .param("to", from.plusDays(63).toString())
                .cookie(testUsers.accessCookie(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
        .andExpect(jsonPath("$.detail", is("Invalid date range")));
  }

  @Test
  void getOneReturnsTodo() throws Exception {
    User user = newUser();
    Todo todo = seed(user, "Read", today().plusDays(2), Priority.HIGH);

    mockMvc
        .perform(get(BASE + "/" + todo.getId()).cookie(testUsers.accessCookie(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(todo.getId().toString())))
        .andExpect(jsonPath("$.name", is("Read")))
        .andExpect(jsonPath("$.priority", is("HIGH")));
  }

  @Test
  void putChangesFields() throws Exception {
    User user = newUser();
    Todo todo = seed(user, "Old", today().plusDays(1), Priority.LOW);
    LocalDate newDate = today().plusDays(5);

    mockMvc
        .perform(
            put(BASE + "/" + todo.getId())
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content(
                    "{\"name\":\"New\",\"description\":\"details\",\"dueDate\":\""
                        + newDate
                        + "\",\"dueTime\":\"09:30:00\",\"priority\":\"HIGH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name", is("New")))
        .andExpect(jsonPath("$.description", is("details")))
        .andExpect(jsonPath("$.dueDate", is(newDate.toString())))
        .andExpect(jsonPath("$.dueTime", is("09:30:00")))
        .andExpect(jsonPath("$.priority", is("HIGH")));
  }

  @Test
  void patchDoneSetsDone() throws Exception {
    User user = newUser();
    Todo todo = seed(user, "Finish", today().plusDays(1), Priority.MEDIUM);

    mockMvc
        .perform(
            patch(BASE + "/" + todo.getId() + "/done")
                .with(csrf())
                .cookie(testUsers.accessCookie(user))
                .contentType("application/json")
                .content("{\"done\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.done", is(true)))
        .andExpect(jsonPath("$.doneAt", notNullValue()));
  }

  @Test
  void deleteReturnsNoContentAndThenNotFound() throws Exception {
    User user = newUser();
    Todo todo = seed(user, "Gone", today().plusDays(1), Priority.LOW);

    mockMvc
        .perform(
            delete(BASE + "/" + todo.getId()).with(csrf()).cookie(testUsers.accessCookie(user)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    mockMvc
        .perform(get(BASE + "/" + todo.getId()).cookie(testUsers.accessCookie(user)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUsersTodoIsNotFoundForEveryWrite() throws Exception {
    User owner = newUser();
    User other = newUser();
    Todo todo = seed(owner, "Private", today().plusDays(1), Priority.LOW);
    String path = BASE + "/" + todo.getId();

    mockMvc
        .perform(get(path).cookie(testUsers.accessCookie(other)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put(path)
                .with(csrf())
                .cookie(testUsers.accessCookie(other))
                .contentType("application/json")
                .content(createBody("Hijack", today().plusDays(1))))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            patch(path + "/done")
                .with(csrf())
                .cookie(testUsers.accessCookie(other))
                .contentType("application/json")
                .content("{\"done\":true}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(delete(path).with(csrf()).cookie(testUsers.accessCookie(other)))
        .andExpect(status().isNotFound());

    assertOwnerTodoUnchanged(owner, todo.getId());
  }

  @Test
  void allDayTodoDatedYesterdayIsExpired() throws Exception {
    User user = newUser();
    Todo todo = seed(user, "Yesterday", today().minusDays(1), Priority.LOW);

    mockMvc
        .perform(get(BASE + "/" + todo.getId()).cookie(testUsers.accessCookie(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expired", is(true)));
  }

  @Test
  void unauthenticatedRequestIsUnauthorized() throws Exception {
    mockMvc
        .perform(get(BASE).param("from", today().toString()).param("to", today().toString()))
        .andExpect(status().isUnauthorized());
  }

  private void assertOwnerTodoUnchanged(User owner, UUID todoId) {
    Todo stored = todoRepository.findByIdAndUserId(todoId, owner.getId()).orElseThrow();
    assertThat(stored.getName()).isEqualTo("Private");
    assertThat(stored.isDone()).isFalse();
  }
}
