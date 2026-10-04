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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ObscureTest {

    /** Produced by rclone 1.74.3 ({@code rclone obscure}); each starts with the IV it used. */
    static Stream<Arguments> rcloneCases() {
        return Stream.of(
                argumentSet("empty", "", "YWFhYWFhYWFhYWFhYWFhYQ"),
                argumentSet("ascii", "potato", "YWFhYWFhYWFhYWFhYWFhYXMaGgIlEQ"),
                argumentSet("another IV", "potato", "YmJiYmJiYmJiYmJiYmJiYp3gcEWbAw"),
                argumentSet(
                        "non-ascii, longer than a block",
                        "pässwörd with spaces & ü",
                        "GmO1CHkqgALQqS5Qw7PjtzvOWRcWcpG4oJjhnhDrN0r_8oKTDdK4EreyLw"));
    }

    @ParameterizedTest
    @MethodSource("rcloneCases")
    void matchesRclone(String plaintext, String obscured) {
        var iv = Arrays.copyOf(Base64.getUrlDecoder().decode(obscured), 16);

        assertThat(new Obscure(() -> iv).obscure(plaintext)).isEqualTo(obscured);
    }

    @Test
    void eachValueGetsARandomIv() {
        var obscure = Obscure.withRandomIvs();

        assertThat(obscure.obscure("potato")).isNotEqualTo(obscure.obscure("potato"));
    }
}
