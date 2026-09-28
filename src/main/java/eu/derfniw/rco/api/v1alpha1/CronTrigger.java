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
package eu.derfniw.rco.api.v1alpha1;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.derfniw.rco.sync.ValidCron;
import eu.derfniw.rco.validation.Reason;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;

/**
 * Runs a sync on a cron schedule, as a Kubernetes CronJob would. The sync's concurrencyPolicy decides what happens
 * when a run is due while another one is still in progress.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CronTrigger {

    /**
     * Five space-separated numeric fields: minute (0-59), hour (0-23), day of month (1-31), month (1-12) and day of
     * week (0-7, Sunday is 0 or 7). Each field is *, a number, a range (1-5), a step (*&#47;15, 0-30/10) or a
     * comma-separated list of these. When both day fields are restricted, the sync runs on days matching either.
     *
     * <p>Or one of the Kubernetes CronJob macros: @yearly and @annually (0 0 1 1 *), @monthly (0 0 1 * *), @weekly (0 0
     * * * 0), @daily and @midnight (0 0 * * *), @hourly (0 * * * *).
     *
     * <p>Evaluated in UTC. Differences from CronJob schedules: no time zone, no names (use numbers) and no ?, and 7 is
     * also Sunday.
     */
    @Required
    @Size(min = 1, max = 128)
    @ValidationRule(
            value =
                    "self.matches('^( *[0-9*/,-]+( +[0-9*/,-]+){4} *|@(yearly|annually|monthly|weekly|daily|midnight|hourly))$')",
            message = "must be five space-separated fields of digits, *, /, , and -, or one of the macros @yearly,"
                    + " @annually, @monthly, @weekly, @daily, @midnight and @hourly")
    @ValidCron(payload = Reason.Invalid.class)
    private String expression;

    public CronTrigger() {}

    public CronTrigger(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
