package com.ntamasa.markitdone.category;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Where an item of a category that is being deleted goes.
 *
 * @param kind whether the item is a one-off todo or a recurring series
 * @param id the todo or series id
 * @param categoryId the target category, or null for "no category"
 */
public record Assignment(@NotNull Kind kind, @NotNull UUID id, UUID categoryId) {}
