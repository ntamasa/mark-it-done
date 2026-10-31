package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TodoOrderingTest {

  private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

  private static Todo todo(String name, LocalTime time, Priority priority, boolean done) {
    Todo todo = new Todo(null, name, null, DAY, time, priority, null);
    todo.setDone(done);
    return todo;
  }

  @Test
  @DisplayName("DAY sorts not-done before done, all-day before timed, time, priority, then name")
  void dayOrderingCoversEveryTieBreaker() {
    Todo doneAllDayUrgent = todo("Old", null, Priority.URGENT, true);
    Todo timedLunchUrgent = todo("Lunch", LocalTime.of(12, 0), Priority.URGENT, false);
    Todo allDayCall = todo("Call", null, Priority.LOW, false);
    Todo allDayWrite = todo("Write", null, Priority.URGENT, false);
    Todo timedReport = todo("Report", LocalTime.of(9, 0), Priority.LOW, false);
    Todo allDayBake = todo("bake", null, Priority.LOW, false);

    List<Todo> shuffled =
        List.of(
            doneAllDayUrgent, timedLunchUrgent, allDayCall, allDayWrite, timedReport, allDayBake);

    assertThat(shuffled.stream().sorted(TodoOrdering.DAY).toList())
        .containsExactly(
            allDayWrite, allDayBake, allDayCall, timedReport, timedLunchUrgent, doneAllDayUrgent);
  }
}
