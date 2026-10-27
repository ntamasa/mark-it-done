package com.ntamasa.markitdone.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a user account.
 *
 * <p>Stores user authentication and profile information, including email, display name, password
 * hash, time zone, and theme preference.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "timezone", nullable = false, length = 64)
  private String timezone;

  @Column(name = "theme_preference", nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private ThemePreference themePreference;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  // Constructors
  public User() {}

  public User(
      String email,
      String displayName,
      String passwordHash,
      String timezone,
      ThemePreference themePreference) {
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.timezone = timezone;
    this.themePreference = themePreference;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getTimezone() {
    return timezone;
  }

  public ThemePreference getThemePreference() {
    return themePreference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // Setters for mutable fields
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public void setThemePreference(ThemePreference themePreference) {
    this.themePreference = themePreference;
  }

  /**
   * Returns the user's time zone as a ZoneId.
   *
   * @return the ZoneId for this user's time zone
   */
  public ZoneId zone() {
    return ZoneId.of(timezone);
  }
}
