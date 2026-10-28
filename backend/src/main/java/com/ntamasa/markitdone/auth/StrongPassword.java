package com.ntamasa.markitdone.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {
  String message() default
      "Password must be at least 12 characters and include an uppercase letter, a lowercase letter, a digit and a symbol";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
