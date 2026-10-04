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

    /** Replaces every placeholder with its value from {@code values}; every placeholder must have one. */
    public static String fill(String template, Map<String, String> values) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
