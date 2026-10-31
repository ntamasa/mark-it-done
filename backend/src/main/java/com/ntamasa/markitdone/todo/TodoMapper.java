package com.ntamasa.markitdone.todo;

import com.ntamasa.markitdone.todo.dto.TodoDto;
import java.time.ZoneId;

/** Maps todo entities to their API representation. */
public final class TodoMapper {

  private TodoMapper() {}

  /**
   * Maps a todo to a TodoDto. The series fields are null for a one-off todo.
   *
   * @param todo the todo
   * @param zone the user's time zone, used to evaluate {@code expired}
   * @return the DTO
   */
  public static TodoDto toDto(Todo todo, ZoneId zone) {
    return new TodoDto(
        todo.getId(),
        todo.getName(),
        todo.getDescription(),
        todo.getDueDate(),
        todo.getDueTime(),
        todo.getPriority(),
        todo.getCategoryId(),
        todo.isDone(),
        todo.getDoneAt(),
        todo.getCreatedAt(),
        todo.getUpdatedAt(),
        todo.isExpired(zone),
        null,
        null,
        null);
  }
}
