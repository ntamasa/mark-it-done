package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.category.Category;
import com.ntamasa.markitdone.category.CategoryRepository;
import com.ntamasa.markitdone.category.Swatch;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TodoRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TodoRepository todoRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TestUsers testUsers;

  private User newUser() {
    return testUsers.create("todos-" + UUID.randomUUID() + "@example.com");
  }

  @Test
  @DisplayName("a saved todo is found by id and owner and by due date range")
  void saveAndFind() {
    User user = newUser();
    LocalDate day = LocalDate.of(2026, 5, 4);
    Todo saved =
        todoRepository.saveAndFlush(
            new Todo(
                user.getId(),
                "Write report",
                "Quarterly",
                day,
                LocalTime.of(9, 30),
                Priority.HIGH,
                null));

    Todo found = todoRepository.findByIdAndUserId(saved.getId(), user.getId()).orElseThrow();

    assertThat(found.getName()).isEqualTo("Write report");
    assertThat(found.getDescription()).isEqualTo("Quarterly");
    assertThat(found.getDueDate()).isEqualTo(day);
    assertThat(found.getDueTime()).isEqualTo(LocalTime.of(9, 30));
    assertThat(found.getPriority()).isEqualTo(Priority.HIGH);
    assertThat(found.isDone()).isFalse();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(todoRepository.findByIdAndUserId(saved.getId(), UUID.randomUUID())).isEmpty();
    assertThat(todoRepository.findByUserIdAndDueDateBetween(user.getId(), day, day))
        .extracting(Todo::getId)
        .containsExactly(saved.getId());
  }

  @Test
  @DisplayName("deleting the todo's category sets the todo's category to null")
  void deletingCategoryClearsTodoCategory() {
    User user = newUser();
    Category category =
        categoryRepository.saveAndFlush(new Category(user.getId(), "Work", Swatch.TEAL));
    Todo saved =
        todoRepository.saveAndFlush(
            new Todo(
                user.getId(),
                "File expenses",
                null,
                LocalDate.of(2026, 5, 5),
                null,
                Priority.LOW,
                category.getId()));
    assertThat(saved.getCategoryId()).isEqualTo(category.getId());

    categoryRepository.delete(category);
    categoryRepository.flush();

    Todo reloaded = todoRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getCategoryId()).isNull();
    assertThat(List.of(reloaded)).hasSize(1);
  }
}
