package com.ntamasa.markitdone.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of the requests that create or edit a category.
 *
 * @param name the category name, required; the length limit applies after trimming
 * @param swatch the colour swatch, required
 */
public record CategoryRequest(@NotBlank String name, @NotNull Swatch swatch) {}
