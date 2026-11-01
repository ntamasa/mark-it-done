package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecurrenceRulesTest {

  private static final LocalDate START = LocalDate.of(2026, 10, 19);

  private static RecurrenceRule every(Integer n, EndType et, Integer cnt, LocalDate end) {
    return new RecurrenceRule(Frequency.EVERY_N_DAYS, n, Set.of(), Set.of(), START, et, cnt, end);
  }

  private static RecurrenceRule weekly(Set<DayOfWeek> days, EndType et, Integer cnt) {
    return new RecurrenceRule(Frequency.WEEKLY, null, days, Set.of(), START, et, cnt, null);
  }

  private static RecurrenceRule monthly(Set<Integer> days) {
    return new RecurrenceRule(
        Frequency.MONTHLY, null, Set.of(), days, START, EndType.NEVER, null, null);
  }

  private static void fails(RecurrenceRule r, String message) {
    assertThatThrownBy(() -> RecurrenceRules.validate(r))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.getMessage()).isEqualTo(message);
            });
  }

  @Test
  void validRulePasses() {
    RecurrenceRules.validate(every(3, EndType.NEVER, null, null));
  }

  @Test
  void intervalOutOfRange() {
    fails(every(0, EndType.NEVER, null, null), "intervalDays must be 1-365");
    fails(every(366, EndType.NEVER, null, null), "intervalDays must be 1-365");
  }

  @Test
  void emptyWeekdays() {
    fails(weekly(Set.of(), EndType.NEVER, null), "pick at least one weekday");
  }

  @Test
  void monthDaysOutOfRangeOrEmpty() {
    String m = "monthDays must be 1-31 and not empty";
    fails(monthly(Set.of(0, 5)), m);
    fails(monthly(Set.of(32)), m);
    fails(monthly(Set.of()), m);
  }

  @Test
  void endCountOutOfRange() {
    fails(every(1, EndType.AFTER_COUNT, 0, null), "endCount must be 1-999");
    fails(every(1, EndType.AFTER_COUNT, 1000, null), "endCount must be 1-999");
  }

  @Test
  void endDateBeforeStart() {
    fails(
        every(1, EndType.ON_DATE, null, START.minusDays(1)),
        "endDate must be on or after startDate");
  }

  @Test
  void problemsAreCollected() {
    var r = new RecurrenceRule(null, null, null, null, null, null, null, null);
    fails(r, "frequency is required; startDate is required; endType is required");
    fails(
        every(0, EndType.AFTER_COUNT, 0, null),
        "intervalDays must be 1-365; endCount must be 1-999");
  }

  @Test
  void summarySingularForOneTime() {
    assertThat(RecurrenceRules.summary(every(1, EndType.AFTER_COUNT, 1, null)))
        .isEqualTo("Every day · 1 time");
  }

  @Test
  void summaries() {
    var mf =
        Set.of(DayOfWeek.values()).stream()
            .filter(d -> d.getValue() <= 5)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(RecurrenceRules.summary(every(1, EndType.NEVER, null, null))).isEqualTo("Every day");
    assertThat(RecurrenceRules.summary(every(3, EndType.NEVER, null, null)))
        .isEqualTo("Every 3 days");
    assertThat(RecurrenceRules.summary(weekly(mf, EndType.NEVER, null)))
        .isEqualTo("Weekly: Mon–Fri");
    assertThat(RecurrenceRules.summary(weekly(Set.of(DayOfWeek.values()), EndType.NEVER, null)))
        .isEqualTo("Weekly: every day");
    assertThat(
            RecurrenceRules.summary(
                weekly(
                    Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    EndType.AFTER_COUNT,
                    20)))
        .isEqualTo("Weekly: Mon, Wed, Fri · 20 times");
    assertThat(RecurrenceRules.summary(monthly(Set.of(31, 1, 15))))
        .isEqualTo("Monthly: 1st, 15th, 31st");
    assertThat(RecurrenceRules.summary(monthly(Set.of(2, 3, 11, 12, 13, 21, 22, 23))))
        .isEqualTo("Monthly: 2nd, 3rd, 11th, 12th, 13th, 21st, 22nd, 23rd");
    assertThat(RecurrenceRules.summary(every(2, EndType.ON_DATE, null, LocalDate.of(2026, 12, 31))))
        .isEqualTo("Every 2 days · until Dec 31, 2026");
  }
}
