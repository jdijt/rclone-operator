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

/**
 * Runs a sync once per period, at a moment within the period that the operator derives from the sync's namespace and
 * name. That moment stays the same across restarts but differs between syncs, so syncs sharing a period don't all
 * start at once. The status of the sync shows when it runs next.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntervalTrigger {

    /** A period between runs. */
    public enum Every {
        @JsonProperty("hourly")
        HOURLY,
        @JsonProperty("daily")
        DAILY,
        @JsonProperty("weekly")
        WEEKLY,
        @JsonProperty("monthly")
        MONTHLY
    }

    /** The period between runs. */
    @Required
    private Every every;

    public IntervalTrigger() {}

    public IntervalTrigger(Every every) {
        this.every = every;
    }

    public Every getEvery() {
        return every;
    }

    public void setEvery(Every every) {
        this.every = every;
    }
}
