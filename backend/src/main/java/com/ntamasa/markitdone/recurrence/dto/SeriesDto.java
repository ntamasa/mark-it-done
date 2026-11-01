package com.ntamasa.markitdone.recurrence.dto;

import com.ntamasa.markitdone.recurrence.SeriesStatus;
import com.ntamasa.markitdone.todo.Priority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A recurring series as returned by the API.
 *
 * @param id the series id
 * @param name the template name
 * @param description the template description, or null
 * @param dueTime the template time, or null for all-day
 * @param priority the template priority
 * @param categoryId the template category id, or null
 * @param rule the recurrence rule
 * @param ruleSummary the readable summary of the rule
 * @param status the series status
 * @param nextOccurrence the next occurrence date, or null (not computed yet)
 * @param nextOccurrenceTodoId the todo of the next occurrence, or null (not computed yet)
 * @param endsSummary the readable end of the series, or null (not computed yet)
 * @param cancelledOn the cancellation day, or null
 */
public record SeriesDto(
    UUID id,
    String name,
    String description,
    LocalTime dueTime,
    Priority priority,
    UUID categoryId,
    RuleRequest rule,
    String ruleSummary,
    SeriesStatus status,
    LocalDate nextOccurrence,
    UUID nextOccurrenceTodoId,
    String endsSummary,
    LocalDate cancelledOn) {}
