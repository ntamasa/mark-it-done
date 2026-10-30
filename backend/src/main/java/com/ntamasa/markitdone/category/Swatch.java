package com.ntamasa.markitdone.category;

/** The fixed palette of colour swatches a category can use (design system MASTER section 2.3). */
public enum Swatch {
  TEAL,
  SKY,
  INDIGO,
  VIOLET,
  PINK,
  ROSE,
  ORANGE,
  AMBER,
  LIME,
  SLATE;

  @Override
  public String toString() {
    return this.name();
  }
}
