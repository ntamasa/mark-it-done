package com.ntamasa.markitdone.todo;

import java.time.LocalDate;
import java.util.UUID;

/** A todo row of a series, as needed to find the next occurrence of the series. */
public interface NextDueRow {

  /**
   * The series of the row.
   *
   * @return the series id
   */
  UUID getSeriesId();

  /**
   * The row id.
   *
   * @return the todo id
   */
  UUID getId();

  /**
   * The due date of the row.
   *
   * @return the due date
   */
  LocalDate getDueDate();
}
