package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Generates the occurrences of a series as real todo rows up to a rolling horizon. */
@Service
public class MaterializationService {

  private final TodoRepository todoRepository;
  private final SeriesExclusionRepository exclusionRepository;
  private final RecurrenceSeriesRepository seriesRepository;
  private final MarkitdoneProperties properties;

  /**
   * Create a MaterializationService.
   *
   * @param todoRepository the todo repository
   * @param exclusionRepository the series exclusion repository
   * @param seriesRepository the series repository
   * @param properties the application properties
   */
  public MaterializationService(
      TodoRepository todoRepository,
      SeriesExclusionRepository exclusionRepository,
      RecurrenceSeriesRepository seriesRepository,
      MarkitdoneProperties properties) {
    this.todoRepository = todoRepository;
    this.exclusionRepository = exclusionRepository;
    this.seriesRepository = seriesRepository;
    this.properties = properties;
  }

  /**
   * The last date to materialise: today in the zone plus the configured number of months.
   *
   * @param zone the user's time zone
   * @return the horizon, inclusive
   */
  public LocalDate horizon(ZoneId zone) {
    return LocalDate.now(zone).plusMonths(properties.recurrence().horizonMonths());
  }

  /**
   * Creates the missing occurrence rows of a series from the day after "materialised until" (or the
   * start date) up to a date, skipping existing rows and excluded dates, and then advances
   * "materialised until". Does nothing for a cancelled series. Safe to repeat.
   *
   * @param series the saved series
   * @param until the last date to materialise, inclusive
   * @return the number of rows created
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public int materialize(RecurrenceSeries series, LocalDate until) {
    if (series.getStatus() == SeriesStatus.CANCELLED) {
      return 0;
    }
    RecurrenceRule rule = series.rule();
    LocalDate done = series.getMaterializedUntil();
    LocalDate from = done == null ? rule.startDate() : done.plusDays(1);
    int created = 0;
    if (!from.isAfter(until)) {
      Set<LocalDate> skip = new HashSet<>(exclusionRepository.findExcludedDates(series.getId()));
      skip.addAll(todoRepository.findOccurrenceDates(series.getId(), from, until));
      List<Todo> rows =
          RecurrenceCalculator.slots(rule, from, until).stream()
              .filter(date -> !skip.contains(date))
              .map(date -> Todo.occurrenceOf(series, date))
              .toList();
      todoRepository.saveAll(rows);
      created = rows.size();
    }
    if (done == null || until.isAfter(done)) {
      series.setMaterializedUntil(until);
      seriesRepository.save(series);
    }
    return created;
  }
}
