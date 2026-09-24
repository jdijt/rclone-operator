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
import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.S3Backend;
import eu.derfniw.rco.api.v1alpha1.SftpBackend;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Validates an rclone remote spec, where this is not already handled by the API server via CEL rules. */
@ApplicationScoped
public class RemoteSpecValidator {

    private static final String SPEC = "spec";

    /**
     * Validates the spec. Only the variant selected by {@code type} is validated: the other variants are ignored
     * (and CEL rules prevent them from being set in the first place).
     */
    public List<FieldError> validate(RCloneRemoteSpec spec) {
        var type = spec.getType();
        if (type == null) {
            return List.of(FieldError.notSupported(SPEC + ".type", null, "supported values: " + supportedTypes()));
        }

        var path = SPEC + "." + type.value();
        return switch (spec.selectedBackend()) {
            case null -> List.of(FieldError.required(path, "must be specified when type is " + type.value()));
            case TemplateBackend template -> validateTemplateBackend(template, path);
            case SftpBackend ignored -> List.of();
            case S3Backend ignored -> List.of();
            case CryptBackend ignored -> List.of();
        };
    }

    /** Validates that the template is well-formed and only references declared inputs. */
    List<FieldError> validateTemplateBackend(TemplateBackend backend, String path) {
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
        return Arrays.stream(BackendType.values()).map(BackendType::value).collect(Collectors.joining(", "));
    }
}
