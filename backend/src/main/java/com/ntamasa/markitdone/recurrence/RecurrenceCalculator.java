package com.ntamasa.markitdone.recurrence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure calculation of the occurrence dates of a {@link RecurrenceRule}. */
public final class RecurrenceCalculator {

  private RecurrenceCalculator() {}

  /**
   * The occurrence dates inside the inclusive window.
   *
   * @param rule the rule
   * @param from the first date of the window
   * @param to the last date of the window
   * @return the occurrence dates in ascending order
   */
  public static List<LocalDate> slots(RecurrenceRule rule, LocalDate from, LocalDate to) {
    LocalDate stop = to;
    if (rule.endType() == EndType.ON_DATE && rule.endDate().isBefore(stop)) {
      stop = rule.endDate();
    }
    int step = rule.frequency() == Frequency.EVERY_N_DAYS ? rule.intervalDays() : 1;
    List<LocalDate> result = new ArrayList<>();
    int seen = 0;
    for (LocalDate d = rule.startDate(); !d.isAfter(stop); d = d.plusDays(step)) {
      if (!matches(rule, d)) {
        continue;
      }
      seen++;
      if (rule.endType() == EndType.AFTER_COUNT && seen > rule.endCount()) {
        break;
      }
      if (!d.isBefore(from)) {
        result.add(d);
      }
    }
    return result;
  }

  /**
   * The number of occurrences strictly before the date.
   *
   * @param rule the rule
   * @param date the exclusive upper bound
   * @return the count
   */
  public static int countBefore(RecurrenceRule rule, LocalDate date) {
    if (!rule.startDate().isBefore(date)) {
      return 0;
    }
    return slots(rule, rule.startDate(), date.minusDays(1)).size();
  }

  /**
   * The last occurrence of a finite series.
   *
   * @param rule the rule
   * @return the last occurrence, or empty for a series that never ends or has no occurrence
   */
  public static Optional<LocalDate> lastSlot(RecurrenceRule rule) {
    LocalDate bound;
    switch (rule.endType()) {
      case ON_DATE -> bound = rule.endDate();
      case AFTER_COUNT ->
          bound =
              rule.frequency() == Frequency.EVERY_N_DAYS
                  ? rule.startDate().plusDays((long) rule.endCount() * rule.intervalDays())
                  : rule.startDate().plusMonths(rule.endCount() + 1L).plusDays(31);
      default -> {
        return Optional.empty();
      }
    }
    List<LocalDate> all = slots(rule, rule.startDate(), bound);
    return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
  }

  private static boolean matches(RecurrenceRule rule, LocalDate d) {
    return switch (rule.frequency()) {
      case EVERY_N_DAYS -> true;
      case WEEKLY -> rule.weekdays().contains(d.getDayOfWeek());
      case MONTHLY -> {
        int length = YearMonth.from(d).lengthOfMonth();
        int day = d.getDayOfMonth();
        yield rule.monthDays().contains(day)
            || (day == length && rule.monthDays().stream().anyMatch(m -> m > length));
      }
    };
  }
}
