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

/** A validation error on one field of a resource, as the Kubernetes API server reports it. */
public record FieldError(String field, Type type, Object value, String detail) {

    public enum Type {
        REQUIRED("Required value", "FieldValueRequired"),
        INVALID("Invalid value", "FieldValueInvalid"),
        NOT_SUPPORTED("Unsupported value", "FieldValueNotSupported");

        private final String description;
        private final String causeReason;

        Type(String description, String causeReason) {
            this.description = description;
            this.causeReason = causeReason;
        }

        public String description() {
            return description;
        }

        /** The reason of a {@code StatusCause} for this error. */
        public String causeReason() {
            return causeReason;
        }
    }

    public static FieldError required(String field, String detail) {
        return new FieldError(field, Type.REQUIRED, null, detail);
    }

    public static FieldError invalid(String field, Object value, String detail) {
        return new FieldError(field, Type.INVALID, value, detail);
    }

    public static FieldError notSupported(String field, Object value, String detail) {
        return new FieldError(field, Type.NOT_SUPPORTED, value, detail);
    }

    /** Renders the error the way the Kubernetes API server does, e.g. {@code spec.x: Invalid value: "y": detail}. */
    @Override
    public String toString() {
        var sb = new StringBuilder(field).append(": ").append(type.description());
        if (value != null) {
            sb.append(": \"").append(value).append('"');
        }
        if (detail != null && !detail.isEmpty()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }
}
