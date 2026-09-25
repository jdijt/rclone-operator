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
package eu.derfniw.rco.remote;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/** The backend field selected by {@code type} must be set. Reported on that field, e.g. {@code crypt}. */
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = SelectedBackendPresent.Validator.class)
public @interface SelectedBackendPresent {

    String message() default "must be specified when type is {type}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SelectedBackendPresent, RCloneRemoteSpec> {

        @Override
        public boolean isValid(RCloneRemoteSpec spec, ConstraintValidatorContext context) {
            // A missing type is reported by the @NotNull on the type field.
            if (spec == null || spec.getType() == null || spec.selectedBackend() != null) {
                return true;
            }
            var type = spec.getType().value();
            context.disableDefaultConstraintViolation();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .addMessageParameter("type", type)
                    .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(type)
                    .addConstraintViolation();
            return false;
        }
    }
}
