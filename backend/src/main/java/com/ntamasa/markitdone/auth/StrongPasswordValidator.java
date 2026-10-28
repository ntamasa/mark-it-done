package com.ntamasa.markitdone.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

  private static final int MIN_LENGTH = 12;
  private static final int MAX_LENGTH = 128;
  private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
  private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
  private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
  private static final Pattern SYMBOL_PATTERN = Pattern.compile("[^A-Za-z0-9]");

  @Override
  public void initialize(StrongPassword constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // Null values are considered invalid
    if (value == null) {
      return false;
    }

    // Check length constraints
    if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
      return false;
    }

    // Check for required character types
    boolean hasUppercase = UPPERCASE_PATTERN.matcher(value).find();
    boolean hasLowercase = LOWERCASE_PATTERN.matcher(value).find();
    boolean hasDigit = DIGIT_PATTERN.matcher(value).find();
    boolean hasSymbol = SYMBOL_PATTERN.matcher(value).find();

    return hasUppercase && hasLowercase && hasDigit && hasSymbol;
  }
}
