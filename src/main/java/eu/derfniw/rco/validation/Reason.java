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

import jakarta.validation.Payload;

/**
 * Constraint payloads naming the Kubernetes field error type a violation maps to. Each spec constraint declares exactly
 * one of them.
 */
public final class Reason {

    /** A required value is missing ({@code FieldValueRequired}). */
    public interface Required extends Payload {}

    /** A value is set but invalid ({@code FieldValueInvalid}). */
    public interface Invalid extends Payload {}

    /** A value is not one of the supported values ({@code FieldValueNotSupported}). */
    public interface NotSupported extends Payload {}

    private Reason() {}
}
