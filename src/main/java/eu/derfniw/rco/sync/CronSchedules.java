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

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

/**
 * Parses cron triggers the way Kubernetes CronJobs interpret their schedules: five numeric fields (minute hour
 * day-of-month month day-of-week, Sunday is 0 or 7), or one of the macros @yearly, @annually, @monthly, @weekly,
 * @daily, @midnight and @hourly. The CRD's CEL rule already rules out names and {@code ?}.
 */
public final class CronSchedules {

    /**
     * cron-utils' UNIX definition plus the CronJob macros. It must not support {@code ?}: that switches cron-utils to
     * Quartz semantics, where a day must match both day fields instead of either.
     */
    private static final CronDefinition DEFINITION = CronDefinitionBuilder.defineCron()
            .withMinutes()
            .withValidRange(0, 59)
            .withStrictRange()
            .and()
            .withHours()
            .withValidRange(0, 23)
            .withStrictRange()
            .and()
            .withDayOfMonth()
            .withValidRange(1, 31)
            .withStrictRange()
            .and()
            .withMonth()
            .withValidRange(1, 12)
            .withStrictRange()
            .and()
            .withDayOfWeek()
            .withValidRange(0, 7)
            .withMondayDoWValue(1)
            .withIntMapping(7, 0)
            .withStrictRange()
            .and()
            .withSupportedNicknameYearly()
            .withSupportedNicknameAnnually()
            .withSupportedNicknameMonthly()
            .withSupportedNicknameWeekly()
            .withSupportedNicknameDaily()
            .withSupportedNicknameMidnight()
            .withSupportedNicknameHourly()
            .instance();

    private static final CronParser PARSER = new CronParser(DEFINITION);

    private CronSchedules() {}

    /**
     * Parses and validates a cron expression.
     *
     * @throws IllegalArgumentException if the expression is not valid; the message includes the expression
     */
    public static Cron parse(String expression) {
        return PARSER.parse(expression).validate();
    }
}
