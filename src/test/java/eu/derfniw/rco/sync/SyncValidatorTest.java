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
package eu.derfniw.rco.sync;

import static eu.derfniw.rco.testsupport.Syncs.sync;
import static org.assertj.core.api.Assertions.assertThat;

import eu.derfniw.rco.api.v1alpha1.IntervalTrigger;
import eu.derfniw.rco.api.v1alpha1.SyncTrigger;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import eu.derfniw.rco.validation.FieldError;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class SyncValidatorTest {

    private static final String EXPRESSION_FIELD = "spec.trigger.cron.expression";
    private static final String TIME_ZONE_FIELD = "spec.trigger.cron.timeZone";

    @Inject
    SyncValidator validator;

    @Test
    void validCron() {
        assertThat(validator.validate(sync(s -> s.getTrigger().getCron().setExpression("*/15 1-5 * * 1,5"))))
                .isEmpty();
    }

    /** cron-utils decides what is invalid; this checks how its verdict is reported. */
    @Test
    void invalidCron() {
        var expression = "60 * * * *";
        assertThat(validator.validate(sync(s -> s.getTrigger().getCron().setExpression(expression))))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.field()).isEqualTo(EXPRESSION_FIELD);
                    assertThat(e.type()).isEqualTo(FieldError.Type.INVALID);
                    assertThat(e.value()).isEqualTo(expression);
                    assertThat(e.detail())
                            .startsWith("must be a valid cron schedule: ")
                            .contains("[0, 59]");
                });
    }

    /**
     * The parser's problem echoes user input, which must come out verbatim: Hibernate Validator would evaluate
     * {@code ${...}} in it as Expression Language if it went through the default violation. The CEL rule keeps such
     * input away from the webhook, but the reconcilers validate too.
     */
    @Test
    void cronProblemIsNotInterpolated() {
        var expression = "${1+1} * * * *";
        assertThat(validator.validate(sync(s -> s.getTrigger().getCron().setExpression(expression))))
                .singleElement()
                .satisfies(e -> assertThat(e.detail()).contains("Expression: ${1+1} "));
    }

    @Test
    void intervalTriggerIsValid() {
        assertThat(validator.validate(sync(s -> s.setTrigger(SyncTrigger.interval(IntervalTrigger.Every.DAILY)))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Europe/Amsterdam", "America/Argentina/Buenos_Aires", "UTC", "Etc/GMT+2"})
    void validTimeZone(String timeZone) {
        assertThat(validator.validate(sync(s -> s.getTrigger().getCron().setTimeZone(timeZone))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mars/Olympus_Mons", "europe/amsterdam", "+02:00", "GMT+2", "Local"})
    void invalidTimeZone(String timeZone) {
        assertThat(validator.validate(sync(s -> s.getTrigger().getCron().setTimeZone(timeZone))))
                .singleElement()
                .isEqualTo(FieldError.invalid(
                        TIME_ZONE_FIELD, timeZone, "must be an IANA time zone name, such as Europe/Amsterdam"));
    }
}
