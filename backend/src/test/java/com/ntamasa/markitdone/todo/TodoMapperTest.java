package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.todo.dto.TodoDto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TodoMapperTest {

  private static Todo todo(LocalDate date, LocalTime time, UUID categoryId) {
    return new Todo(UUID.randomUUID(), "Pay rent", "desc", date, time, Priority.HIGH, categoryId);
  }

  @Test
  @DisplayName("toDto copies the todo fields and leaves the series fields null")
  void copiesFields() {
    UUID categoryId = UUID.randomUUID();
    Todo todo = todo(LocalDate.of(2099, 1, 2), LocalTime.of(14, 30), categoryId);

    TodoDto dto = TodoMapper.toDto(todo, ZoneId.of("UTC"));

    assertThat(dto.name()).isEqualTo("Pay rent");
    assertThat(dto.description()).isEqualTo("desc");
    assertThat(dto.dueDate()).isEqualTo(LocalDate.of(2099, 1, 2));
    assertThat(dto.dueTime()).isEqualTo(LocalTime.of(14, 30));
    assertThat(dto.priority()).isEqualTo(Priority.HIGH);
    assertThat(dto.categoryId()).isEqualTo(categoryId);
    assertThat(dto.done()).isFalse();
    assertThat(dto.doneAt()).isNull();
    assertThat(dto.expired()).isFalse();
    assertThat(dto.seriesId()).isNull();
    assertThat(dto.occurrenceDate()).isNull();
    assertThat(dto.ruleSummary()).isNull();
  }

  @Test
  @DisplayName("expired is true for a todo due in the past")
  void pastTodoIsExpired() {
    Todo todo = todo(LocalDate.of(2000, 1, 1), null, null);

    assertThat(TodoMapper.toDto(todo, ZoneId.of("Europe/Budapest")).expired()).isTrue();
  }

  @Test
  @DisplayName("done state is mapped with its doneAt")
  void mapsDone() {
    Todo todo = todo(LocalDate.of(2099, 1, 2), null, null);
    todo.setDone(true);

    TodoDto dto = TodoMapper.toDto(todo, ZoneId.of("UTC"));

    assertThat(dto.done()).isTrue();
    assertThat(dto.doneAt()).isNotNull();
  }
}
