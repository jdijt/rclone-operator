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
package eu.derfniw.rco.run;

/**
 * Renders the rclone configuration file of a sync: the sections {@value #SOURCE} and {@value #DESTINATION}, plus one
 * section per remote a crypt remote wraps ({@code source_1}, {@code source_2}, ...).
 */
public final class RCloneConfig {

    public static final String SOURCE = "source";
    public static final String DESTINATION = "destination";

    /** A value that can't be written to the configuration file. The message never contains the value. */
    public static final class InvalidValueException extends Exception {
        public InvalidValueException(String message) {
            super(message);
        }
    }

    private RCloneConfig() {}

    /** Renders the configuration, with password options obscured by {@code obscure}. */
    public static String render(ResolvedRemote source, ResolvedRemote destination, Obscure obscure)
            throws InvalidValueException {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
