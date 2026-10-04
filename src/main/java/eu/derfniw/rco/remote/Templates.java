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

import java.util.Map;

/** Fills the {@code ${name}} placeholders of remote templates. */
public final class Templates {

    private Templates() {}

    /**
     * Replaces every placeholder with its value from {@code values}. The template must be valid (see
     * {@link DeclaredPlaceholders}), so every placeholder has a value. Values are inserted as they are, not scanned
     * again.
     */
    public static String fill(String template, Map<String, String> values) {
        var result = new StringBuilder();
        int copied = 0;
        for (var placeholder : TemplateScanner.scan(template)) {
            if (placeholder instanceof TemplateScanner.Reference reference) {
                var value = values.get(reference.name());
                if (value == null) {
                    throw new IllegalArgumentException("no value for placeholder " + reference.text());
                }
                result.append(template, copied, reference.offset()).append(value);
                copied = reference.offset() + reference.text().length();
            }
        }
        return result.append(template, copied, template.length()).toString();
    }
}
