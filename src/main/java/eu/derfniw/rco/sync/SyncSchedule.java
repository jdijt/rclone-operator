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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** When a cron trigger fires. Times are whole minutes; the expression is evaluated in UTC. */
public final class SyncSchedule {

    private SyncSchedule() {}

    /** The schedule of {@code expression}, which must pass {@link CronSchedules#parse}. */
    public static SyncSchedule of(String expression) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** The first time strictly after {@code time}. */
    public Instant nextAfter(Instant time) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * The run that should start at {@code now}: the most recent time after {@code since} and at or before {@code now},
     * if it is at most {@code deadline} ago. Earlier missed times are skipped.
     *
     * @param since the most recent time a run was created for, or when the sync was created
     */
    public Optional<Instant> due(Instant since, Instant now, Duration deadline) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
