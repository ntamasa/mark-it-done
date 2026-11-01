package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;

/** Builds the API view of recurring series. */
public final class SeriesQueries {

  private SeriesQueries() {}

  /**
   * Maps a series to its API shape. The next occurrence and the ends summary are not computed here
   * yet and are left null.
   *
   * @param series the series
   * @return the API representation
   */
  public static SeriesDto toDto(RecurrenceSeries series) {
    RecurrenceRule rule = series.rule();
    return new SeriesDto(
        series.getId(),
        series.getName(),
        series.getDescription(),
        series.getDueTime(),
        series.getPriority(),
        series.getCategoryId(),
        RuleRequest.of(rule),
        RecurrenceRules.summary(rule),
        series.getStatus(),
        null,
        null,
        null,
        series.getCancelledOn());
  }
}
