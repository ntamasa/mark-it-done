package com.ntamasa.markitdone.category;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Body of the request that deletes a category.
 *
 * @param assignments where the listed items go; items left out end up without a category
 */
public record DeleteCategoryRequest(@Valid List<Assignment> assignments) {}
