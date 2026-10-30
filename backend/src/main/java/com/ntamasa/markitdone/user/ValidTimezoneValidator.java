package com.ntamasa.markitdone.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;

/** Validates that a time zone is one of the IANA identifiers known to the JDK. */
public class ValidTimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

  private static final Set<String> KNOWN_ZONES = ZoneId.getAvailableZoneIds();

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || KNOWN_ZONES.contains(value);
  }
}
