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
package eu.derfniw.rco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Constraints on the operator configuration, checked the way Quarkus checks them at startup: on the return values of
 * the mapping's methods.
 */
@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class OperatorConfigTest {

    @Inject
    Validator validator;

    @Inject
    OperatorConfig config;

    // Zero or negative would re-check a remote waiting for a Secret without pause.
    static Stream<Arguments> secretRecheckIntervalCases() {
        return Stream.of(
                argumentSet("negative", Duration.ofSeconds(-1), false),
                argumentSet("zero", Duration.ZERO, false),
                argumentSet("below a second", Duration.ofMillis(999), false),
                argumentSet("one second", Duration.ofSeconds(1), true),
                argumentSet("the default", Duration.ofMinutes(1), true));
    }

    @ParameterizedTest
    @MethodSource("secretRecheckIntervalCases")
    void secretRecheckInterval(Duration interval, boolean valid) throws NoSuchMethodException {
        var violations = validator
                .forExecutables()
                .validateReturnValue(config, OperatorConfig.class.getMethod("secretRecheckInterval"), interval);

        assertThat(violations.isEmpty()).isEqualTo(valid);
    }
}
