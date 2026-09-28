/*
 * Copyright 2026.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.derfniw.rco.sync;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.ZoneId;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * The string must be an IANA time zone name, such as Europe/Amsterdam or UTC. Offsets like +02:00 are not names and
 * are rejected. The name is the dynamic payload.
 */
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = ValidTimeZone.Validator.class)
public @interface ValidTimeZone {

    String message() default "must be an IANA time zone name, such as Europe/Amsterdam";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTimeZone, String> {

        @Override
        public boolean isValid(String name, ConstraintValidatorContext context) {
            if (name == null || ZoneId.getAvailableZoneIds().contains(name)) {
                return true;
            }
            context.unwrap(HibernateConstraintValidatorContext.class).withDynamicPayload(name);
            return false;
        }
    }
}
