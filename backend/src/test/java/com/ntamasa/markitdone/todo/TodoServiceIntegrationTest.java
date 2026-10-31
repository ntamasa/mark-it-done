package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.category.Category;
import com.ntamasa.markitdone.category.CategoryRepository;
import com.ntamasa.markitdone.category.Swatch;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TodoServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TodoService todoService;
  @Autowired private TestUsers testUsers;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  private User newUser() {
    return testUsers.create("todos-" + UUID.randomUUID() + "@example.com");
  }

  private static TodoRequest req(String name, LocalDate date, LocalTime time, Priority priority) {
    return new TodoRequest(name, null, date, time, priority, null);
  }

  private List<String> auditActions(UUID userId) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
        .map(AuditLogEntry::getAction)
        .toList();
  }

  @Test
  @DisplayName("create trims the name, turns a blank description into null and audits")
  void createTrimsAndAudits() {
    User user = newUser();

    TodoDto dto =
        todoService.create(
            user.getId(),
            new TodoRequest(
                "  Buy milk  ", "   ", LocalDate.of(2099, 1, 1), null, Priority.LOW, null));

    assertThat(dto.name()).isEqualTo("Buy milk");
    assertThat(dto.description()).isNull();
    assertThat(dto.done()).isFalse();
    assertThat(dto.expired()).isFalse();
    assertThat(auditActions(user.getId())).contains("TODO_CREATED");
  }

  @Test
  @DisplayName("creating with another user's category is rejected as Unknown category")
  void foreignCategoryRejected() {
    User owner = newUser();
    User other = newUser();
    Category category = categoryRepository.save(new Category(other.getId(), "Work", Swatch.TEAL));

    TodoRequest request =
        new TodoRequest("x", null, LocalDate.of(2099, 1, 1), null, Priority.LOW, category.getId());

    assertThatThrownBy(() -> todoService.create(owner.getId(), request))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(ex.getMessage()).isEqualTo("Unknown category");
            });
  }

  @Test
  @DisplayName("creating with the user's own category works")
  void ownCategoryAccepted() {
    User user = newUser();
    Category category = categoryRepository.save(new Category(user.getId(), "Home", Swatch.SKY));

    TodoDto dto =
        todoService.create(
            user.getId(),
            new TodoRequest(
                "x", null, LocalDate.of(2099, 1, 1), null, Priority.LOW, category.getId()));

    assertThat(dto.categoryId()).isEqualTo(category.getId());
  }

  @Test
  @DisplayName("a todo dated last month can be updated and is audited")
  void updatePastTodo() {
    User user = newUser();
    LocalDate lastMonth = LocalDate.now().minusMonths(1);
    TodoDto created = todoService.create(user.getId(), req("Old", lastMonth, null, Priority.LOW));

    TodoDto updated =
        todoService.update(
            user.getId(),
            created.id(),
            req("Renamed", lastMonth, LocalTime.of(9, 0), Priority.HIGH));

    assertThat(updated.name()).isEqualTo("Renamed");
    assertThat(updated.dueTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(updated.priority()).isEqualTo(Priority.HIGH);
    assertThat(updated.expired()).isTrue();
    assertThat(auditActions(user.getId())).contains("TODO_UPDATED");
  }

  @Test
  @DisplayName("done true sets doneAt and false clears it")
  void doneToggle() {
    User user = newUser();
    TodoDto created =
        todoService.create(user.getId(), req("T", LocalDate.of(2099, 1, 1), null, Priority.LOW));

    TodoDto done = todoService.setDone(user.getId(), created.id(), true);
    assertThat(done.done()).isTrue();
    assertThat(done.doneAt()).isNotNull();

    TodoDto undone = todoService.setDone(user.getId(), created.id(), false);
    assertThat(undone.done()).isFalse();
    assertThat(undone.doneAt()).isNull();
    assertThat(auditActions(user.getId())).contains("TODO_DONE_CHANGED");
  }

  @Test
  @DisplayName("the range is sorted by date and then by the day ordering")
  void rangeOrder() {
    User user = newUser();
    LocalDate day = LocalDate.of(2099, 5, 10);
    LocalDate next = day.plusDays(1);
    todoService.create(user.getId(), req("next", next, null, Priority.URGENT));
    todoService.create(user.getId(), req("timed", day, LocalTime.of(8, 0), Priority.URGENT));
    todoService.create(user.getId(), req("allday low", day, null, Priority.LOW));
    todoService.create(user.getId(), req("allday urgent", day, null, Priority.URGENT));
    TodoDto finished =
        todoService.create(user.getId(), req("finished", day, null, Priority.URGENT));
    todoService.setDone(user.getId(), finished.id(), true);

    List<TodoDto> result = todoService.range(user.getId(), day, next);

    assertThat(result)
        .extracting(TodoDto::name)
        .containsExactly("allday urgent", "allday low", "timed", "finished", "next");
  }

  @Test
  @DisplayName("the range excludes other users' todos")
  void rangeIsPerUser() {
    User user = newUser();
    User other = newUser();
    LocalDate day = LocalDate.of(2099, 5, 10);
    todoService.create(other.getId(), req("theirs", day, null, Priority.LOW));

    assertThat(todoService.range(user.getId(), day, day)).isEmpty();
  }

  @Test
  @DisplayName("a range of 62 days apart is accepted, 63 days apart and reversed ranges are not")
  void rangeLimits() {
    User user = newUser();
    LocalDate from = LocalDate.of(2099, 1, 1);

    assertThat(todoService.range(user.getId(), from, from.plusDays(62))).isEmpty();
    assertThatThrownBy(() -> todoService.range(user.getId(), from, from.plusDays(63)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST);
              assertThat(ex.getMessage()).isEqualTo("Invalid date range");
            });
    assertThatThrownBy(() -> todoService.range(user.getId(), from, from.minusDays(1)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("delete removes the todo and writes an audit row")
  void deleteAudits() {
    User user = newUser();
    TodoDto created =
        todoService.create(user.getId(), req("T", LocalDate.of(2099, 1, 1), null, Priority.LOW));

    todoService.delete(user.getId(), created.id());

    assertThatThrownBy(() -> todoService.get(user.getId(), created.id()))
        .isInstanceOf(ApiException.class);
    assertThat(auditActions(user.getId())).contains("TODO_DELETED");
  }

  @Test
  @DisplayName("another user's todo behaves as not found for every operation")
  void foreignTodoNotFound() {
    User owner = newUser();
    User other = newUser();
    TodoDto created =
        todoService.create(owner.getId(), req("T", LocalDate.of(2099, 1, 1), null, Priority.LOW));
    UUID id = created.id();
    UUID otherId = other.getId();
    TodoRequest request = req("X", LocalDate.of(2099, 1, 1), null, Priority.LOW);

    List<Runnable> calls =
        List.of(
            () -> todoService.get(otherId, id),
            () -> todoService.update(otherId, id, request),
            () -> todoService.setDone(otherId, id, true),
            () -> todoService.delete(otherId, id));
    for (Runnable call : calls) {
      assertThatThrownBy(call::run)
          .isInstanceOfSatisfying(
              ApiException.class,
              ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
                assertThat(ex.getMessage()).isEqualTo("Todo not found");
              });
    }
  }
}
