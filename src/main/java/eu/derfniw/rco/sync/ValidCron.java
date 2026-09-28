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
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * The string must be a schedule {@link CronSchedules} can parse. The expression is the dynamic payload.
 *
 * <p>The parser's error message includes the expression, so it only goes into a message parameter of a custom
 * violation: Hibernate Validator evaluates Expression Language in the default violation's message after substituting
 * parameters, but leaves it disabled for custom violations.
 */
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = ValidCron.Validator.class)
public @interface ValidCron {

    String message() default "must be a valid cron schedule: {problem}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCron, String> {

        @Override
        public boolean isValid(String expression, ConstraintValidatorContext context) {
            // A missing schedule is reported by the CRD schema.
            if (expression == null) {
                return true;
            }
            try {
                CronSchedules.parse(expression);
                return true;
            } catch (IllegalArgumentException e) {
                context.disableDefaultConstraintViolation();
                context.unwrap(HibernateConstraintValidatorContext.class)
                        .addMessageParameter("problem", e.getMessage())
                        .withDynamicPayload(expression)
                        .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addConstraintViolation();
                return false;
            }
        }
    }
}
