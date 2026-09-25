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
package eu.derfniw.rco.validation;

import io.fabric8.kubernetes.client.CustomResource;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.validator.engine.HibernateConstraintViolation;

/**
 * Validates a custom resource beyond what its CRD schema and CEL rules enforce: the Jakarta constraints on its spec,
 * then {@link #customValidation}.
 *
 * <p>Every constraint on the spec must carry exactly one {@link Reason} payload, which selects the error type. A
 * constraint reports the offending value through Hibernate Validator's dynamic payload.
 */
public abstract class ResourceValidator<S> {

    private static final String SPEC = "spec";

    @Inject
    Validator validator;

    /**
     * All errors, sorted by field and then rendering, so that messages built from them are stable across reconciles.
     */
    public final List<FieldError> validate(CustomResource<S, ?> resource) {
        var spec = resource.getSpec();
        if (spec == null) {
            return List.of(FieldError.required(SPEC, "must be specified"));
        }
        var errors = new ArrayList<FieldError>();
        for (var violation : validator.validate(spec)) {
            errors.add(toFieldError(violation));
        }
        errors.addAll(customValidation(resource));
        errors.sort(Comparator.comparing(FieldError::field).thenComparing(FieldError::toString));
        return errors;
    }

    /** Checks that don't fit a constraint annotation. Only called when the spec is set. */
    protected List<FieldError> customValidation(CustomResource<S, ?> resource) {
        return List.of();
    }

    private FieldError toFieldError(ConstraintViolation<?> violation) {
        var path = violation.getPropertyPath().toString();
        HibernateConstraintViolation<?> hibernateViolation = violation.unwrap(HibernateConstraintViolation.class);
        return new FieldError(
                path.isEmpty() ? SPEC : SPEC + "." + path,
                type(violation),
                hibernateViolation.getDynamicPayload(Object.class),
                violation.getMessage());
    }

    private FieldError.Type type(ConstraintViolation<?> violation) {
        var payload = violation.getConstraintDescriptor().getPayload();
        if (payload.contains(Reason.Required.class)) {
            return FieldError.Type.REQUIRED;
        } else if (payload.contains(Reason.Invalid.class)) {
            return FieldError.Type.INVALID;
        } else if (payload.contains(Reason.NotSupported.class)) {
            return FieldError.Type.NOT_SUPPORTED;
        }
        throw new IllegalStateException("constraint without a Reason payload: " + violation.getConstraintDescriptor());
    }
}
