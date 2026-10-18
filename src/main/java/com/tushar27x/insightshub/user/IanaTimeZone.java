package com.tushar27x.insightshub.user;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

/**
 * An IANA zone id such as {@code Asia/Kolkata} or {@code UTC}. Offsets like {@code +05:30} or {@code GMT+5} are
 * refused: they have no DST rules, and they're not what the browser's {@code Intl} API sends. Null is valid
 * (pair with {@code @NotNull}).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IanaTimeZone.Validator.class)
public @interface IanaTimeZone {

    String message() default "must be an IANA time zone, like Europe/Berlin";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IanaTimeZone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZoneId.getAvailableZoneIds().contains(value);
        }
    }
}
