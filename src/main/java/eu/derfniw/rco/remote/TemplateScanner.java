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

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the {@code ${name}} placeholders in a remote template.
 *
 * <p>A {@code $} that is not followed by {@code {} is literal text. A {@code ${} must be followed by an identifier
 * ({@code [A-Za-z_][A-Za-z0-9_]*}) and a closing {@code }}; anything else is reported as malformed.
 */
final class TemplateScanner {

    sealed interface Placeholder permits Reference, Malformed {}

    /** A well-formed {@code ${name}}. */
    record Reference(String name, String text) implements Placeholder {}

    /** A {@code ${} that does not start a well-formed placeholder. */
    record Malformed(String text, int offset) implements Placeholder {}

    private TemplateScanner() {}

    static List<Placeholder> scan(String template) {
        var result = new ArrayList<Placeholder>();
        int i = 0;
        while ((i = template.indexOf("${", i)) >= 0) {
            int nameStart = i + 2;
            int end = nameStart;
            while (end < template.length() && isIdentifierChar(template.charAt(end), end == nameStart)) {
                end++;
            }
            boolean closed = end < template.length() && template.charAt(end) == '}';
            if (end > nameStart && closed) {
                result.add(new Reference(template.substring(nameStart, end), template.substring(i, end + 1)));
                i = end + 1;
            } else {
                // Report up to and including the next '}' (or the rest of the template) so the error shows the
                // offending construct.
                int close = template.indexOf('}', nameStart);
                int textEnd = close < 0 ? template.length() : close + 1;
                result.add(new Malformed(template.substring(i, textEnd), i));
                i = nameStart;
            }
        }
        return result;
    }

    private static boolean isIdentifierChar(char c, boolean first) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (!first && c >= '0' && c <= '9');
    }
}
