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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * rclone's password obscuring ({@code rclone obscure}): what rclone expects for password options in its config. The
 * key is rclone's public constant, so this hides passwords from casual reading but is not encryption.
 */
public final class Obscure {

    /** rclone's fixed key (fs/config/obscure). */
    private static final byte[] KEY =
            HexFormat.of().parseHex("9c935b48730a554d6bfd7c63c886a92bd390198eb8128afbf4de162b8b95f638");

    private static final int IV_LENGTH = 16;

    private final Supplier<byte[]> ivs;

    /** Obscures each value with the next 16-byte IV from {@code ivs}. */
    public Obscure(Supplier<byte[]> ivs) {
        this.ivs = ivs;
    }

    /** Obscures each value with a fresh IV from a secure random source, as rclone does. */
    public static Obscure withRandomIvs() {
        var random = new SecureRandom();
        return new Obscure(() -> {
            var iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            return iv;
        });
    }

    /** AES-256-CTR with rclone's key; the result is the IV followed by the ciphertext, base64url without padding. */
    public String obscure(String plaintext) {
        var iv = ivs.get();
        if (iv.length != IV_LENGTH) {
            throw new IllegalArgumentException("IV must be " + IV_LENGTH + " bytes, not " + iv.length);
        }
        try {
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var out = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            // AES/CTR is a required algorithm of every Java platform.
            throw new IllegalStateException(e);
        }
    }
}
