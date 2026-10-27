package com.ntamasa.markitdone.user;

/** Enumeration of theme preferences for users. */
public enum ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  @Override
  public String toString() {
    return this.name();
  }
}
