package com.ntamasa.markitdone.recurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * The immutable recurrence rule of a series.
 *
 * @param frequency how often the series repeats
 * @param intervalDays the step in days for {@link Frequency#EVERY_N_DAYS}, otherwise null
 * @param weekdays the weekdays for {@link Frequency#WEEKLY}, otherwise empty
 * @param monthDays the days of the month (1-31, sorted) for {@link Frequency#MONTHLY}, otherwise
 *     empty
 * @param startDate the first possible date
 * @param endType how the series ends
 * @param endCount the number of occurrences for {@link EndType#AFTER_COUNT}, otherwise null
 * @param endDate the last possible date for {@link EndType#ON_DATE}, otherwise null
 */
public record RecurrenceRule(
    Frequency frequency,
    Integer intervalDays,
    Set<DayOfWeek> weekdays,
    Set<Integer> monthDays,
    LocalDate startDate,
    EndType endType,
    Integer endCount,
    LocalDate endDate) {}
