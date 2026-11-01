package com.ntamasa.markitdone.todo;

import com.ntamasa.markitdone.recurrence.RecurrenceRules;
import com.ntamasa.markitdone.recurrence.RecurrenceSeries;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

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
    return toDto(todo, zone, Map.of());
  }

  /**
   * Maps a todo to a TodoDto, filling the series fields of an occurrence.
   *
   * @param todo the todo
   * @param zone the user's time zone, used to evaluate {@code expired}
   * @param series the series by id, containing the todo's series if it has one
   * @return the DTO
   */
  public static TodoDto toDto(Todo todo, ZoneId zone, Map<UUID, RecurrenceSeries> series) {
    RecurrenceSeries own = todo.getSeriesId() == null ? null : series.get(todo.getSeriesId());
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
        todo.getSeriesId(),
        todo.getOccurrenceDate(),
        own == null ? null : RecurrenceRules.summary(own.rule()));
  }
}
