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

/**
 * A validation error on a single field, modelled on Kubernetes' {@code field.Error}.
 *
 * @param field the dotted path to the field, e.g. {@code spec.template.template}
 * @param type what kind of error this is
 * @param value the offending value, or {@code null} when the field is missing
 * @param detail a human-readable explanation
 */
public record FieldError(String field, Type type, Object value, String detail) {

    public enum Type {
        REQUIRED("Required value"),
        INVALID("Invalid value"),
        NOT_SUPPORTED("Unsupported value");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    static FieldError required(String field, String detail) {
        return new FieldError(field, Type.REQUIRED, null, detail);
    }

    static FieldError invalid(String field, Object value, String detail) {
        return new FieldError(field, Type.INVALID, value, detail);
    }

    static FieldError notSupported(String field, Object value, String detail) {
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
