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

import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * The template's placeholders must be well-formed and only reference declared inputs. Each offending placeholder is
 * reported on {@code template}, with its text as the dynamic payload.
 *
 * <p>Placeholder text is user input full of {@code ${...}}: it only ever goes into message parameters and the
 * payload, never into a message template, which Hibernate Validator would interpolate.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = DeclaredPlaceholders.Validator.class)
public @interface DeclaredPlaceholders {

    String message() default "placeholders must be well-formed and declared in inputs";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DeclaredPlaceholders, TemplateBackend> {

        @Override
        public boolean isValid(TemplateBackend backend, ConstraintValidatorContext context) {
            // A blank template is reported by the @NotBlank on the template field.
            if (backend == null
                    || backend.getTemplate() == null
                    || backend.getTemplate().isBlank()) {
                return true;
            }
            Map<String, ?> inputs = backend.getInputs() == null ? Map.of() : backend.getInputs();
            var hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);
            boolean valid = true;
            for (var placeholder : TemplateScanner.scan(backend.getTemplate().strip())) {
                switch (placeholder) {
                    case TemplateScanner.Reference ref
                    when !inputs.containsKey(ref.name()) -> {
                        report(
                                hibernateContext,
                                "reference to undeclared field {name}",
                                "name",
                                ref.name(),
                                ref.text());
                        valid = false;
                    }
                    case TemplateScanner.Reference ignored -> {}
                    case TemplateScanner.Malformed bad -> {
                        report(
                                hibernateContext,
                                "malformed placeholder at offset {offset}",
                                "offset",
                                bad.offset(),
                                bad.text());
                        valid = false;
                    }
                }
            }
            if (!valid) {
                context.disableDefaultConstraintViolation();
            }
            return valid;
        }

        private void report(
                HibernateConstraintValidatorContext context,
                String template,
                String parameter,
                Object argument,
                String placeholderText) {
            context.addMessageParameter(parameter, argument)
                    .withDynamicPayload(placeholderText)
                    .buildConstraintViolationWithTemplate(template)
                    .addPropertyNode("template")
                    .addConstraintViolation();
        }
    }
}
