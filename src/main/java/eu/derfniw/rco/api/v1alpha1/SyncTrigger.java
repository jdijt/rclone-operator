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
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import jakarta.validation.Valid;

/**
 * Decides when an RCloneSync runs.
 *
 * <p>It is a discriminated union: {@code type} selects which of the trigger fields must be set.
 */
@ValidationRule(
        value = "self.type == 'interval' ? has(self.interval) : !has(self.interval)",
        message = "interval must be set if and only if type is interval")
@ValidationRule(
        value = "self.type == 'cron' ? has(self.cron) : !has(self.cron)",
        message = "cron must be set if and only if type is cron")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncTrigger {

    /** The kind of trigger. Each value is also the name of the field that configures it. */
    public enum Type {
        @JsonProperty("interval")
        INTERVAL,
        @JsonProperty("cron")
        CRON
    }

    /** Selects the trigger. Exactly the matching trigger field must be set. */
    @Required
    private Type type;

    /** Runs the sync once per period, at a time the operator chooses. */
    private IntervalTrigger interval;

    /** Runs the sync on a cron schedule. */
    @Valid
    private CronTrigger cron;

    public SyncTrigger() {}

    public static SyncTrigger interval(IntervalTrigger.Every every) {
        var trigger = new SyncTrigger();
        trigger.setType(Type.INTERVAL);
        trigger.setInterval(new IntervalTrigger(every));
        return trigger;
    }

    public static SyncTrigger cron(String expression) {
        var trigger = new SyncTrigger();
        trigger.setType(Type.CRON);
        trigger.setCron(new CronTrigger(expression));
        return trigger;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public IntervalTrigger getInterval() {
        return interval;
    }

    public void setInterval(IntervalTrigger interval) {
        this.interval = interval;
    }

    public CronTrigger getCron() {
        return cron;
    }

    public void setCron(CronTrigger cron) {
        this.cron = cron;
    }
}
