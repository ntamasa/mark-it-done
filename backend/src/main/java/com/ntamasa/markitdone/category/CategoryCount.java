package com.ntamasa.markitdone.category;

import java.util.UUID;

/** Projection of a grouped count of items per category. */
public interface CategoryCount {

  /**
   * Returns the category id.
   *
   * @return the category id
   */
  UUID getCategoryId();

  /**
   * Returns the number of items in the category.
   *
   * @return the count
   */
  long getItemCount();
}
