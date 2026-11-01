package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.recurrence.EndType;
import com.ntamasa.markitdone.recurrence.Frequency;
import com.ntamasa.markitdone.recurrence.RecurrenceRule;
import com.ntamasa.markitdone.recurrence.RecurrenceSeries;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TodoMapperSeriesTest {

  private static final ZoneId UTC = ZoneId.of("UTC");

  private static RecurrenceSeries series() {
    RecurrenceRule rule =
        new RecurrenceRule(
            Frequency.WEEKLY,
            null,
            Set.of(DayOfWeek.MONDAY),
            null,
            LocalDate.of(2026, 1, 5),
            EndType.NEVER,
            null,
            null);
    return new RecurrenceSeries(UUID.randomUUID(), "Gym", null, null, Priority.LOW, null, rule);
  }

  @Test
  void occurrenceCarriesSeriesFields() {
    RecurrenceSeries series = series();
    UUID id = UUID.randomUUID();
    ReflectionTestUtils.setField(series, "id", id);
    Todo todo = Todo.occurrenceOf(series, LocalDate.of(2026, 1, 12));

    TodoDto dto = TodoMapper.toDto(todo, UTC, Map.of(id, series));

    assertThat(dto.seriesId()).isEqualTo(id);
    assertThat(dto.occurrenceDate()).isEqualTo(LocalDate.of(2026, 1, 12));
    assertThat(dto.ruleSummary()).isEqualTo("Weekly: Mon");
  }

  @Test
  void oneOffTodoHasNoSeriesFields() {
    Todo todo =
        new Todo(UUID.randomUUID(), "x", null, LocalDate.of(2026, 1, 1), null, Priority.LOW, null);

    TodoDto dto = TodoMapper.toDto(todo, UTC, Map.of());

    assertThat(dto.seriesId()).isNull();
    assertThat(dto.occurrenceDate()).isNull();
    assertThat(dto.ruleSummary()).isNull();
  }
}
