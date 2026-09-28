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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.cronutils.model.time.ExecutionTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Our cron definition must interpret schedules as Kubernetes CronJobs do. */
class CronSchedulesTest {

    private static final ZonedDateTime FROM = ZonedDateTime.of(2026, 9, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    /** The macros and their equivalents, as documented for CronJobs. */
    static Stream<Arguments> macroCases() {
        return Stream.of(
                argumentSet("@yearly", "@yearly", "0 0 1 1 *"),
                argumentSet("@annually", "@annually", "0 0 1 1 *"),
                argumentSet("@monthly", "@monthly", "0 0 1 * *"),
                argumentSet("@weekly", "@weekly", "0 0 * * 0"),
                argumentSet("@daily", "@daily", "0 0 * * *"),
                argumentSet("@midnight", "@midnight", "0 0 * * *"),
                argumentSet("@hourly", "@hourly", "0 * * * *"));
    }

    @ParameterizedTest
    @MethodSource("macroCases")
    void macroRunsLikeItsEquivalent(String macro, String equivalent) {
        assertThat(nextRuns(macro)).isEqualTo(nextRuns(equivalent));
    }

    /** When both day fields are restricted, a day matching either one is a run day. */
    @Test
    void restrictedDayFieldsMatchEither() {
        assertThat(nextRuns("0 0 13 * 5").stream().map(ZonedDateTime::toLocalDate)) // the 13th, or a Friday
                .containsExactly(
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 9),
                        LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 16));
    }

    /** A stepped field counts as restricted, so here too either day field matching is enough. */
    @Test
    void steppedDayFieldMatchesEither() {
        assertThat(nextRuns("0 0 */2 * 1").stream().map(ZonedDateTime::toLocalDate)) // odd days, or a Monday
                .containsExactly(
                        LocalDate.of(2026, 9, 29),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 3),
                        LocalDate.of(2026, 10, 5));
    }

    private static List<ZonedDateTime> nextRuns(String expression) {
        var executionTime = ExecutionTime.forCron(CronSchedules.parse(expression));
        var runs = new ArrayList<ZonedDateTime>();
        var from = FROM;
        for (int i = 0; i < 4; i++) {
            from = executionTime.nextExecution(from).orElseThrow();
            runs.add(from);
        }
        return runs;
    }
}
