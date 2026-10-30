package com.ntamasa.markitdone.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidTimezoneValidatorTest {

  private final ValidTimezoneValidator validator = new ValidTimezoneValidator();

  @Test
  @DisplayName("accepts known IANA identifiers")
  void testAcceptsKnownZones() {
    assertThat(validator.isValid("America/New_York", null)).isTrue();
    assertThat(validator.isValid("Europe/Budapest", null)).isTrue();
    assertThat(validator.isValid("UTC", null)).isTrue();
  }

  @Test
  @DisplayName("accepts null because the field is optional")
  void testAcceptsNull() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  @DisplayName("rejects unknown zones, blanks and offsets")
  void testRejectsUnknownZones() {
    assertThat(validator.isValid("Mars/Base", null)).isFalse();
    assertThat(validator.isValid("", null)).isFalse();
    assertThat(validator.isValid("   ", null)).isFalse();
    assertThat(validator.isValid("+05:00", null)).isFalse();
  }
}
