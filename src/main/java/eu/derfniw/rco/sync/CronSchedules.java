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
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

/**
 * Parses cron triggers with rclone-operator's cron definition: cron-utils' UNIX type, five numeric fields (minute hour
 * day-of-month month day-of-week, Sunday is 0 or 7). The CRD's CEL rule already rules out names and macros.
 */
public final class CronSchedules {

    private static final CronParser PARSER = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

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
