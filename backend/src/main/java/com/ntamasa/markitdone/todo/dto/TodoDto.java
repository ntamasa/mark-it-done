package com.ntamasa.markitdone.todo.dto;

import com.ntamasa.markitdone.todo.Priority;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A todo as returned by the API.
 *
 * @param id the todo id
 * @param name the name
 * @param description the description, or null
 * @param dueDate the due date
 * @param dueTime the due time, or null for an all-day todo
 * @param priority the priority
 * @param categoryId the category id, or null
 * @param done whether the todo is done
 * @param doneAt when it was marked done, or null
 * @param createdAt when it was created
 * @param updatedAt when it was last changed
 * @param expired whether it is expired now in the user's time zone
 * @param seriesId the id of the recurring series, or null for a one-off todo
 * @param occurrenceDate the occurrence date within the series, or null
 * @param ruleSummary the readable summary of the series rule, or null
 */
public record TodoDto(
    UUID id,
    String name,
    String description,
    LocalDate dueDate,
    LocalTime dueTime,
    Priority priority,
    UUID categoryId,
    Boolean done,
    Instant doneAt,
    Instant createdAt,
    Instant updatedAt,
    Boolean expired,
    UUID seriesId,
    LocalDate occurrenceDate,
    String ruleSummary) {}
