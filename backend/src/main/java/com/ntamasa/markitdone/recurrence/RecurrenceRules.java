package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Validation and the human summary of a {@link RecurrenceRule}. */
public final class RecurrenceRules {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

  private RecurrenceRules() {}

  /**
   * Validate the rule and report all problems together.
   *
   * @param rule the rule
   * @throws ApiException VALIDATION_FAILED with the messages joined by "; "
   */
  public static void validate(RecurrenceRule rule) {
    List<String> errors = new ArrayList<>();
    if (rule.frequency() == null) {
      errors.add("frequency is required");
    }
    if (rule.startDate() == null) {
      errors.add("startDate is required");
    }
    if (rule.endType() == null) {
      errors.add("endType is required");
    }
    if (rule.frequency() == Frequency.EVERY_N_DAYS
        && (rule.intervalDays() == null || rule.intervalDays() < 1 || rule.intervalDays() > 365)) {
      errors.add("intervalDays must be 1-365");
    }
    if (rule.frequency() == Frequency.WEEKLY && rule.weekdays().isEmpty()) {
      errors.add("pick at least one weekday");
    }
    if (rule.frequency() == Frequency.MONTHLY
        && (rule.monthDays().isEmpty()
            || rule.monthDays().stream().anyMatch(d -> d == null || d < 1 || d > 31))) {
      errors.add("monthDays must be 1-31 and not empty");
    }
    if (rule.endType() == EndType.AFTER_COUNT
        && (rule.endCount() == null || rule.endCount() < 1 || rule.endCount() > 999)) {
      errors.add("endCount must be 1-999");
    }
    if (rule.endType() == EndType.ON_DATE
        && (rule.endDate() == null
            || (rule.startDate() != null && rule.endDate().isBefore(rule.startDate())))) {
      errors.add("endDate must be on or after startDate");
    }
    if (!errors.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, String.join("; ", errors));
    }
  }

  /**
   * The human summary, for example "Weekly: Mon-Fri . 20 times".
   *
   * @param rule a valid rule
   * @return the summary
   */
  public static String summary(RecurrenceRule rule) {
    String base =
        switch (rule.frequency()) {
          case EVERY_N_DAYS ->
              rule.intervalDays() == 1 ? "Every day" : "Every " + rule.intervalDays() + " days";
          case WEEKLY -> "Weekly: " + weekdays(rule);
          case MONTHLY ->
              "Monthly: "
                  + rule.monthDays().stream()
                      .map(RecurrenceRules::ordinal)
                      .collect(Collectors.joining(", "));
        };
    return switch (rule.endType()) {
      case NEVER -> base;
      case AFTER_COUNT ->
          base + " · " + rule.endCount() + (rule.endCount() == 1 ? " time" : " times");
      case ON_DATE -> base + " · until " + DATE.format(rule.endDate());
    };
  }

  private static String weekdays(RecurrenceRule rule) {
    if (rule.weekdays().size() == 7) {
      return "every day";
    }
    boolean monToFri =
        rule.weekdays().size() == 5
            && rule.weekdays().stream().noneMatch(d -> d.getValue() > DayOfWeek.FRIDAY.getValue());
    if (monToFri) {
      return "Mon–Fri";
    }
    return rule.weekdays().stream()
        .sorted()
        .map(d -> d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
        .collect(Collectors.joining(", "));
  }

  private static String ordinal(int n) {
    int mod100 = n % 100;
    String suffix;
    if (mod100 >= 11 && mod100 <= 13) {
      suffix = "th";
    } else {
      suffix =
          switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
          };
    }
    return n + suffix;
  }
}
