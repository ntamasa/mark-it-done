package com.ntamasa.markitdone.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Checks that a value is a known IANA time zone identifier. A null value is valid. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTimezoneValidator.class)
@Documented
public @interface ValidTimezone {
  String message() default "Unknown timezone";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
