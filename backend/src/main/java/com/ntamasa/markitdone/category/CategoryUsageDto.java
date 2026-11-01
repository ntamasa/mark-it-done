package com.ntamasa.markitdone.category;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The items in a category.
 *
 * @param todos the one-off todos in the category
 * @param series the recurring series in the category
 */
public record CategoryUsageDto(List<TodoItem> todos, List<SeriesItem> series) {

  /**
   * A one-off todo in the category.
   *
   * @param id the todo id
   * @param name the todo name
   * @param dueDate the due date
   */
  public record TodoItem(UUID id, String name, LocalDate dueDate) {}

  /**
   * A recurring series in the category.
   *
   * @param id the series id
   * @param name the series name
   * @param ruleSummary the human summary of the recurrence rule
   */
  public record SeriesItem(UUID id, String name, String ruleSummary) {}
}
