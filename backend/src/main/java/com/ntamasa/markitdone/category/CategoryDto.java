package com.ntamasa.markitdone.category;

import java.util.UUID;

/**
 * Data Transfer Object for a category.
 *
 * @param id the category id
 * @param name the trimmed category name
 * @param swatch the colour swatch
 * @param itemCount the number of one-off todos plus recurring series in the category (one-offs plus
 *     series)
 */
public record CategoryDto(UUID id, String name, Swatch swatch, Integer itemCount) {

  /**
   * Create a CategoryDto from a Category entity.
   *
   * @param category the category entity
   * @param itemCount the number of items in the category
   * @return the category as returned by the API
   */
  public static CategoryDto of(Category category, int itemCount) {
    return new CategoryDto(category.getId(), category.getName(), category.getSwatch(), itemCount);
  }
}
