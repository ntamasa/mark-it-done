package com.ntamasa.markitdone.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class StrongPasswordValidatorTest {

  private final StrongPasswordValidator validator = new StrongPasswordValidator();
  private final ConstraintValidatorContext context = Mockito.mock(ConstraintValidatorContext.class);

  @Test
  @DisplayName("valid password with all required character types")
  void testValidPassword() {
    boolean result = validator.isValid("Summer-2026!x", context);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("null password is invalid")
  void testNullPasswordIsInvalid() {
    boolean result = validator.isValid(null, context);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Short1!a"})
  @DisplayName("too short password is invalid")
  void testTooShortPasswordIsInvalid(String password) {
    boolean result = validator.isValid(password, context);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"alllowercase1!"})
  @DisplayName("password without uppercase is invalid")
  void testPasswordWithoutUppercaseIsInvalid(String password) {
    boolean result = validator.isValid(password, context);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ALLUPPERCASE1!"})
  @DisplayName("password without lowercase is invalid")
  void testPasswordWithoutLowercaseIsInvalid(String password) {
    boolean result = validator.isValid(password, context);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NoDigitsHere!!"})
  @DisplayName("password without digit is invalid")
  void testPasswordWithoutDigitIsInvalid(String password) {
    boolean result = validator.isValid(password, context);
    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NoSymbols12345"})
  @DisplayName("password without symbol is invalid")
  void testPasswordWithoutSymbolIsInvalid(String password) {
    boolean result = validator.isValid(password, context);
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("password exceeding max length (129 chars) is invalid")
  void testPasswordExceedingMaxLengthIsInvalid() {
    // Create a string with 129 characters
    String longPassword =
        "Summer2026VeryLongPasswordWithAllRequiredCharTypesUpperLowerDigitSymbol!Summer2026VeryLongPasswordWithAllRequiredCharTypesUpperLowerDigitSymbolExtra";
    assertThat(longPassword.length()).isGreaterThan(128);
    boolean result = validator.isValid(longPassword, context);
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("password with space as symbol is valid")
  void testPasswordWithSpaceAsSymbolIsValid() {
    String password = "Summer 2026 Abc1";
    boolean result = validator.isValid(password, context);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("password at minimum length is valid")
  void testPasswordAtMinimumLengthIsValid() {
    // 12 characters: Aa1!Bb2@Cc3#Dd4
    String password = "Aa1!Bb2@Cc3#";
    assertThat(password.length()).isEqualTo(12);
    boolean result = validator.isValid(password, context);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("password at maximum length is valid")
  void testPasswordAtMaximumLengthIsValid() {
    // 128 characters with all required types
    String password =
        "Aa1!bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbC2!";
    assertThat(password.length()).isEqualTo(128);
    boolean result = validator.isValid(password, context);
    assertThat(result).isTrue();
  }
}
