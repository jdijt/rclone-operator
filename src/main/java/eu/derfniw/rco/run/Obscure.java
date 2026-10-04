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

import java.util.function.Supplier;

/**
 * rclone's password obscuring ({@code rclone obscure}): what rclone expects for password options in its config. The
 * key is rclone's public constant, so this hides passwords from casual reading but is not encryption.
 */
public final class Obscure {

    private final Supplier<byte[]> ivs;

    /** Obscures each value with the next 16-byte IV from {@code ivs}. */
    public Obscure(Supplier<byte[]> ivs) {
        this.ivs = ivs;
    }

    /** Obscures each value with a fresh IV from a secure random source, as rclone does. */
    public static Obscure withRandomIvs() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public String obscure(String plaintext) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
