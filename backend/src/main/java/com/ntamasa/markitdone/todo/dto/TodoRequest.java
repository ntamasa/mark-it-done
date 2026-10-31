package com.ntamasa.markitdone.todo.dto;

import com.ntamasa.markitdone.todo.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Body of the requests that create or edit a one-off todo.
 *
 * @param name the name, required, at most 200 characters after trimming
 * @param description the description, at most 2000 characters; blank means none
 * @param dueDate the due date, required
 * @param dueTime the due time, or null for an all-day todo
 * @param priority the priority, required
 * @param categoryId the id of one of the user's categories, or null
 */
public record TodoRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotNull LocalDate dueDate,
    LocalTime dueTime,
    @NotNull Priority priority,
    UUID categoryId) {}
