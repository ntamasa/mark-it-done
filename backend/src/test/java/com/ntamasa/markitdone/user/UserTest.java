package com.ntamasa.markitdone.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  @DisplayName("zone() returns ZoneId.of(timezone)")
  void testZoneReturnsCorrectZoneId() {
    User user =
        new User(
            "test@example.com", "Test User", "hash", "America/New_York", ThemePreference.SYSTEM);
    ZoneId result = user.zone();
    assertThat(result).isEqualTo(ZoneId.of("America/New_York"));
  }

  @Test
  @DisplayName("zone() works with UTC timezone")
  void testZoneWithUtc() {
    User user = new User("test@example.com", "Test User", "hash", "UTC", ThemePreference.LIGHT);
    ZoneId result = user.zone();
    assertThat(result).isEqualTo(ZoneId.of("UTC"));
  }

  @Test
  @DisplayName("zone() works with various IANA timezone identifiers")
  void testZoneWithVariousTimezones() {
    String[] timezones = {"Europe/London", "Asia/Tokyo", "Australia/Sydney", "Europe/Berlin"};
    for (String tz : timezones) {
      User user = new User("test@example.com", "Test User", "hash", tz, ThemePreference.DARK);
      ZoneId result = user.zone();
      assertThat(result).isEqualTo(ZoneId.of(tz));
    }
  }
}
