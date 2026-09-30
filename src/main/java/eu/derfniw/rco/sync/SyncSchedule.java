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

import com.cronutils.model.time.ExecutionTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** When a cron trigger fires. Times are whole minutes; the expression is evaluated in UTC. */
public final class SyncSchedule {

    private final ExecutionTime executionTime;

    private SyncSchedule(ExecutionTime executionTime) {
        this.executionTime = executionTime;
    }

    /** The schedule of {@code expression}, which must pass {@link CronSchedules#parse}. */
    public static SyncSchedule of(String expression) {
        return new SyncSchedule(ExecutionTime.forCron(CronSchedules.parse(expression)));
    }

    /** The first time strictly after {@code time}. */
    public Instant nextAfter(Instant time) {
        return executionTime
                .nextExecution(utc(time))
                .map(ZonedDateTime::toInstant)
                .orElseThrow(() -> new IllegalStateException("schedule has no time after " + time));
    }

    /**
     * The run that should start at {@code now}: the most recent time after {@code since} and at or before {@code now},
     * if it is at most {@code deadline} ago. Earlier missed times are skipped.
     *
     * @param since the most recent time a run was created for, or when the sync was created
     */
    public Optional<Instant> due(Instant since, Instant now, Duration deadline) {
        return latestAtOrBefore(now)
                .filter(time -> time.isAfter(since))
                .filter(time -> !time.isBefore(now.minus(deadline)));
    }

    private Optional<Instant> latestAtOrBefore(Instant time) {
        // Schedule times are whole minutes, so the latest one at or before time is at or before this minute.
        var minute = utc(time.truncatedTo(ChronoUnit.MINUTES));
        if (executionTime.isMatch(minute)) {
            return Optional.of(minute.toInstant());
        }
        return executionTime.lastExecution(minute).map(ZonedDateTime::toInstant);
    }

    private static ZonedDateTime utc(Instant time) {
        return time.atZone(ZoneOffset.UTC);
    }
}
