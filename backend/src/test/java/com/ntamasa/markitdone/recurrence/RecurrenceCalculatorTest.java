package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecurrenceCalculatorTest {

  private static LocalDate d(int y, int m, int day) {
    return LocalDate.of(y, m, day);
  }

  private static RecurrenceRule every(
      int n, LocalDate start, EndType et, Integer cnt, LocalDate end) {
    return new RecurrenceRule(Frequency.EVERY_N_DAYS, n, Set.of(), Set.of(), start, et, cnt, end);
  }

  private static RecurrenceRule weekly(
      Set<DayOfWeek> days, LocalDate start, EndType et, Integer cnt, LocalDate end) {
    return new RecurrenceRule(Frequency.WEEKLY, null, days, Set.of(), start, et, cnt, end);
  }

  private static RecurrenceRule monthly(
      Set<Integer> days, LocalDate start, EndType et, Integer cnt, LocalDate end) {
    return new RecurrenceRule(Frequency.MONTHLY, null, Set.of(), days, start, et, cnt, end);
  }

  private static final Set<DayOfWeek> MWF =
      Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

  @Test
  void everyThreeDaysNeverEnding() {
    var r = every(3, d(2026, 10, 19), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 19), d(2026, 10, 31)))
        .containsExactly(
            d(2026, 10, 19), d(2026, 10, 22), d(2026, 10, 25), d(2026, 10, 28), d(2026, 10, 31));
  }

  @Test
  void dailyAfterFiveCountsFromStart() {
    var r = every(1, d(2026, 10, 1), EndType.AFTER_COUNT, 5, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 1), d(2026, 10, 31)))
        .containsExactly(
            d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 3), d(2026, 10, 4), d(2026, 10, 5));
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 4), d(2026, 10, 31)))
        .containsExactly(d(2026, 10, 4), d(2026, 10, 5));
  }

  @Test
  void weeklyStartsFromStartDate() {
    var r = weekly(MWF, d(2026, 10, 21), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 19), d(2026, 10, 25)))
        .containsExactly(d(2026, 10, 21), d(2026, 10, 23));
  }

  @Test
  void weeklyWeekdaysEndingOnDateInclusive() {
    var days =
        Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);
    var r = weekly(days, d(2026, 10, 19), EndType.ON_DATE, null, d(2026, 10, 23));
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 19), d(2026, 10, 31)))
        .containsExactly(
            d(2026, 10, 19), d(2026, 10, 20), d(2026, 10, 21), d(2026, 10, 22), d(2026, 10, 23));
  }

  @Test
  void monthly31FallsBackToLastDay() {
    var r = monthly(Set.of(31), d(2026, 1, 31), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 1, 1), d(2026, 6, 30)))
        .containsExactly(
            d(2026, 1, 31),
            d(2026, 2, 28),
            d(2026, 3, 31),
            d(2026, 4, 30),
            d(2026, 5, 31),
            d(2026, 6, 30));
  }

  @Test
  void monthly30And31MergeIntoOneOccurrence() {
    var r = monthly(Set.of(30, 31), d(2026, 1, 1), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 2, 1), d(2026, 2, 28)))
        .containsExactly(d(2026, 2, 28));
    assertThat(RecurrenceCalculator.slots(r, d(2028, 2, 1), d(2028, 2, 29)))
        .containsExactly(d(2028, 2, 29));
  }

  @Test
  void monthlyAfterThree() {
    var r = monthly(Set.of(1, 15), d(2026, 1, 1), EndType.AFTER_COUNT, 3, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 1, 1), d(2026, 12, 31)))
        .containsExactly(d(2026, 1, 1), d(2026, 1, 15), d(2026, 2, 1));
  }

  @Test
  void countBefore() {
    var r = weekly(MWF, d(2026, 9, 7), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.countBefore(r, d(2026, 10, 21))).isEqualTo(19);
    assertThat(RecurrenceCalculator.countBefore(r, d(2026, 9, 7))).isZero();
  }

  @Test
  void lastSlotAfterCount() {
    var r = weekly(MWF, d(2026, 9, 7), EndType.AFTER_COUNT, 20, null);
    assertThat(RecurrenceCalculator.lastSlot(r)).contains(d(2026, 10, 21));
    var e = every(2, d(2026, 1, 1), EndType.AFTER_COUNT, 4, null);
    assertThat(RecurrenceCalculator.lastSlot(e)).contains(d(2026, 1, 7));
    var m = monthly(Set.of(31), d(2026, 1, 31), EndType.AFTER_COUNT, 3, null);
    assertThat(RecurrenceCalculator.lastSlot(m)).contains(d(2026, 3, 31));
  }

  @Test
  void lastSlotOnDateAndNever() {
    var r = every(3, d(2026, 10, 19), EndType.ON_DATE, null, d(2026, 10, 30));
    assertThat(RecurrenceCalculator.lastSlot(r)).contains(d(2026, 10, 28));
    var n = every(3, d(2026, 10, 19), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.lastSlot(n)).isEmpty();
  }

  @Test
  void endDateBeforeWindowGivesNothing() {
    var r = every(1, d(2026, 10, 1), EndType.ON_DATE, null, d(2026, 10, 5));
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 10), d(2026, 10, 20))).isEmpty();
  }

  @Test
  void nothingBeforeStart() {
    var r = every(1, d(2026, 10, 10), EndType.NEVER, null, null);
    assertThat(RecurrenceCalculator.slots(r, d(2026, 10, 1), d(2026, 10, 9))).isEqualTo(List.of());
  }
}
