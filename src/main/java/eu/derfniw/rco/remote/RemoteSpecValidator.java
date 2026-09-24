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

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Statically validates an rclone remote spec, where this is not already handled by the API server via CEL rules. */
public final class RemoteSpecValidator {

    private static final String SPEC = "spec";

    private RemoteSpecValidator() {}

    /**
     * Validates the spec. Only the variant selected by {@code type} is validated: the other variants are ignored
     * (and CEL rules prevent them from being set in the first place).
     */
    public static List<FieldError> validate(RCloneRemoteSpec spec) {
        var type = spec.getType();
        if (type == null) {
            return List.of(FieldError.notSupported(SPEC + ".type", null, "supported values: " + supportedTypes()));
        }

        var path = SPEC + "." + type.fieldName();
        Object variant =
                switch (type) {
                    case SFTP -> spec.getSftp();
                    case S3 -> spec.getS3();
                    case CRYPT -> spec.getCrypt();
                    case TEMPLATE -> spec.getTemplate();
                };
        if (variant == null) {
            return List.of(FieldError.required(path, "must be specified when type is " + type.fieldName()));
        }
        if (type == BackendType.TEMPLATE) {
            return validateTemplateBackend(spec.getTemplate(), path);
        }
        return List.of();
    }

    /** Validates that the template is well-formed and only references declared inputs. */
    static List<FieldError> validateTemplateBackend(TemplateBackend backend, String path) {
        var field = path + ".template";
        var template =
                backend.getTemplate() == null ? "" : backend.getTemplate().strip();
        if (template.isEmpty()) {
            return List.of(FieldError.required(field, "must be specified and not blank"));
        }

        Map<String, ?> inputs = backend.getInputs() == null ? Map.of() : backend.getInputs();
        var errors = new ArrayList<FieldError>();
        for (var placeholder : TemplateScanner.scan(template)) {
            switch (placeholder) {
                case TemplateScanner.Reference ref
                when !inputs.containsKey(ref.name()) ->
                    errors.add(FieldError.invalid(field, ref.text(), "reference to undeclared field " + ref.name()));
                case TemplateScanner.Reference ignored -> {}
                case TemplateScanner.Malformed bad ->
                    errors.add(
                            FieldError.invalid(field, bad.text(), "malformed placeholder at offset " + bad.offset()));
            }
        }
        return errors;
    }

    private static String supportedTypes() {
        return Arrays.stream(BackendType.values()).map(BackendType::fieldName).collect(Collectors.joining(", "));
    }
}
