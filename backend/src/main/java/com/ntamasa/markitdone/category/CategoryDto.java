package com.ntamasa.markitdone.category;

import java.util.UUID;

/**
 * Data Transfer Object for a category.
 *
 * @param id the category id
 * @param name the trimmed category name
 * @param swatch the colour swatch
 * @param itemCount the number of todos in the category (0 until todos are linked)
 */
public record CategoryDto(UUID id, String name, Swatch swatch, Integer itemCount) {

  /**
   * Create a CategoryDto from a Category entity. The item count is 0 for now.
   *
   * @param category the category entity
   * @return the category as returned by the API
   */
  public static CategoryDto of(Category category) {
    return new CategoryDto(category.getId(), category.getName(), category.getSwatch(), 0);
  }
}
