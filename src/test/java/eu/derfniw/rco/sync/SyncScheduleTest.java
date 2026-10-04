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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SyncScheduleTest {

    private static final Duration HOUR = Duration.ofHours(1);

    private static final String nextCasesCronExpr = "0 3 * * *";

    static Stream<Arguments> nextCases() {
        return Stream.of(
                argumentSet("in UTC", "2026-09-28T12:00:00Z", "2026-09-29T03:00:00Z"),
                argumentSet("strictly after a scheduled time", "2026-09-29T03:00:00Z", "2026-09-30T03:00:00Z"));
    }

    @ParameterizedTest
    @MethodSource("nextCases")
    void nextAfter(String after, String want) {
        assertThat(SyncSchedule.of(nextCasesCronExpr).nextAfter(Instant.parse(after)))
                .isEqualTo(Instant.parse(want));
    }

    /**
     * All with an hourly schedule, on the hour.
     */
    private static final String dueCasesCronExpr = "0 * * * *";

    static Stream<Arguments> dueCases() {
        return Stream.of(
                argumentSet("none since the last run", "10:00", "10:59:59", HOUR, null),
                argumentSet("the next one, once it is time", "10:00", "11:00", HOUR, "11:00"),
                argumentSet("created between two times: the next one", "10:30", "11:00", HOUR, "11:00"),
                argumentSet("created between two times: not the one before", "10:30", "10:59", HOUR, null),
                argumentSet("of several missed, the most recent", "07:00", "10:30", HOUR, "10:00"),
                argumentSet("late, within the deadline", "07:00", "10:45", Duration.ofMinutes(45), "10:00"),
                // The earlier missed times don't stand in for it.
                argumentSet("too late: skipped", "07:00", "10:46", Duration.ofMinutes(45), null));
    }

    @ParameterizedTest
    @MethodSource("dueCases")
    void due(String since, String now, Duration deadline, String want) {
        var schedule = SyncSchedule.of(dueCasesCronExpr);
        assertThat(schedule.due(at(since), at(now), deadline))
                .isEqualTo(Optional.ofNullable(want).map(SyncScheduleTest::at));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-28T" + (time.length() == 5 ? time + ":00" : time) + "Z");
    }
}
