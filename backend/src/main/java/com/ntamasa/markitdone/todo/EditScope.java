package com.ntamasa.markitdone.todo;

/** Which occurrences of a recurring todo an edit applies to. */
public enum EditScope {
  /** Only the edited occurrence. */
  THIS,
  /** The edited occurrence and all later ones that are not detached. */
  FUTURE
}
