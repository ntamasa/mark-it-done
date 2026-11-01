package com.ntamasa.markitdone.recurrence.dto;

import com.ntamasa.markitdone.recurrence.EndType;
import com.ntamasa.markitdone.recurrence.Frequency;
import com.ntamasa.markitdone.recurrence.RecurrenceRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The recurrence rule as sent to and returned by the API. Unused parts are null.
 *
 * @param frequency how often the series repeats
 * @param intervalDays the step in days for EVERY_N_DAYS
 * @param weekdays the weekdays for WEEKLY
 * @param monthDays the days of the month for MONTHLY
 * @param startDate the first possible date
 * @param endType how the series ends
 * @param endCount the number of occurrences for AFTER_COUNT
 * @param endDate the last possible date for ON_DATE
 */
public record RuleRequest(
    Frequency frequency,
    Integer intervalDays,
    Set<DayOfWeek> weekdays,
    Set<Integer> monthDays,
    LocalDate startDate,
    EndType endType,
    Integer endCount,
    LocalDate endDate) {

  /**
   * Converts the request to a rule. Null elements in the day sets are dropped.
   *
   * @return the rule, not yet validated
   */
  public RecurrenceRule toRule() {
    return new RecurrenceRule(
        frequency,
        intervalDays,
        weekdays == null
            ? null
            : weekdays.stream().filter(Objects::nonNull).collect(Collectors.toSet()),
        monthDays == null
            ? null
            : monthDays.stream().filter(Objects::nonNull).collect(Collectors.toSet()),
        startDate,
        endType,
        endCount,
        endDate);
  }

  /**
   * Converts a rule to its API shape. Empty day sets become null.
   *
   * @param rule the rule
   * @return the API representation
   */
  public static RuleRequest of(RecurrenceRule rule) {
    return new RuleRequest(
        rule.frequency(),
        rule.intervalDays(),
        rule.weekdays().isEmpty() ? null : rule.weekdays(),
        rule.monthDays().isEmpty() ? null : rule.monthDays(),
        rule.startDate(),
        rule.endType(),
        rule.endCount(),
        rule.endDate());
  }
}
