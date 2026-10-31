package com.ntamasa.markitdone.todo;

import java.util.Comparator;

/** Orders the todos of one day. */
public final class TodoOrdering {

  /**
   * Orders todos within a day: not-done before done, all-day before timed (timed by time), then
   * priority from URGENT to LOW, then name case-insensitively.
   */
  public static final Comparator<Todo> DAY =
      Comparator.comparing(Todo::isDone)
          .thenComparing(Todo::getDueTime, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(Todo::getPriority)
          .thenComparing(Todo::getName, String.CASE_INSENSITIVE_ORDER);

  private TodoOrdering() {}
}
