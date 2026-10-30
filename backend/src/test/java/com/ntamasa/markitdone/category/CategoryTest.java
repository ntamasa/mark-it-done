package com.ntamasa.markitdone.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  @DisplayName("key() trims surrounding spaces and lower-cases the name")
  void keyNormalisesNameKey() {
    assertThat(Category.key("  Work ")).isEqualTo("work");
    assertThat(Category.key("Work")).isEqualTo(Category.key("WORK"));
  }

  @Test
  @DisplayName("key() keeps inner spaces")
  void keyKeepsInnerSpaces() {
    assertThat(Category.key(" Deep  Work ")).isEqualTo("deep  work");
  }

  @Test
  @DisplayName("constructor stores trimmed name, its key, user and swatch")
  void constructorSetsFields() {
    UUID userId = UUID.randomUUID();
    Category category = new Category(userId, "  Work ", Swatch.TEAL);

    assertThat(category.getUserId()).isEqualTo(userId);
    assertThat(category.getName()).isEqualTo("Work");
    assertThat(category.getNameKey()).isEqualTo("work");
    assertThat(category.getSwatch()).isEqualTo(Swatch.TEAL);
  }

  @Test
  @DisplayName("rename() updates name and name key together")
  void renameUpdatesNameAndKey() {
    Category category = new Category(UUID.randomUUID(), "Work", Swatch.SKY);

    category.rename("  Home Life ");

    assertThat(category.getName()).isEqualTo("Home Life");
    assertThat(category.getNameKey()).isEqualTo("home life");
  }

  @Test
  @DisplayName("Swatch lists the ten MASTER section 2.3 swatches in order")
  void swatchOrder() {
    assertThat(Swatch.values())
        .containsExactly(
            Swatch.TEAL,
            Swatch.SKY,
            Swatch.INDIGO,
            Swatch.VIOLET,
            Swatch.PINK,
            Swatch.ROSE,
            Swatch.ORANGE,
            Swatch.AMBER,
            Swatch.LIME,
            Swatch.SLATE);
  }
}
