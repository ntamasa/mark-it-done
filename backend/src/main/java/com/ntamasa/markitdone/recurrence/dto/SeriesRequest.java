package com.ntamasa.markitdone.recurrence.dto;

import com.ntamasa.markitdone.todo.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Body of the request that creates a recurring series.
 *
 * @param name the template name, required, at most 200 characters after trimming
 * @param description the template description, at most 2000 characters; blank means none
 * @param dueTime the template time, or null for all-day
 * @param priority the template priority, required
 * @param categoryId the id of one of the user's categories, or null
 * @param rule the recurrence rule, required
 */
public record SeriesRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    LocalTime dueTime,
    @NotNull Priority priority,
    UUID categoryId,
    @NotNull RuleRequest rule) {}
