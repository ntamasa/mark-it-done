package com.ntamasa.markitdone.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user's own colour-coded category for grouping todos.
 *
 * <p>The name is stored trimmed, and {@code nameKey} holds its normalised form (trimmed and
 * lower-case). Uniqueness per user is enforced on {@code nameKey} by the database. Renaming always
 * updates {@code name} and {@code nameKey} together.
 */
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name", nullable = false, length = 40)
  private String name;

  @Column(name = "name_key", nullable = false, length = 40)
  private String nameKey;

  @Column(name = "swatch", nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private Swatch swatch;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  // Constructors
  public Category() {}

  public Category(UUID userId, String name, Swatch swatch) {
    this.userId = userId;
    this.swatch = swatch;
    rename(name);
  }

  /**
   * Returns the normalised form of a category name, used for uniqueness: trimmed and lower-case.
   *
   * @param name the category name
   * @return the name key
   */
  public static String key(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Renames the category, updating the trimmed name and its key together.
   *
   * @param name the new name
   */
  public void rename(String name) {
    this.name = name.trim();
    this.nameKey = key(name);
  }

  /**
   * Changes the swatch of the category.
   *
   * @param swatch the new swatch
   */
  public void changeSwatch(Swatch swatch) {
    this.swatch = swatch;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getNameKey() {
    return nameKey;
  }

  public Swatch getSwatch() {
    return swatch;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
