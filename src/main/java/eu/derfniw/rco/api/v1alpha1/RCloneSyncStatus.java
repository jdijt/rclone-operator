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
import io.fabric8.kubernetes.api.model.Condition;
import java.util.ArrayList;
import java.util.List;

/** Observed state of an RCloneSync. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RCloneSyncStatus {

    /** Condition type: the sync can run when its trigger fires. */
    public static final String READY = "Ready";

    /**
     * The current state of the sync. Each condition has a unique type.
     *
     * <p>Condition types include "Ready": the sync can run when its trigger fires.
     */
    private List<Condition> conditions = new ArrayList<>();

    /**
     * When the most recent scheduled run was due (RFC 3339). Runs are named after this time, so a run is never created
     * twice, even after it was removed by the history limits.
     */
    private String lastScheduleTime;

    /**
     * When the trigger creates the next run (RFC 3339). For an interval trigger this is how to see the time the operator
     * chose. Unset while the sync is suspended.
     */
    private String nextScheduleTime;

    /** When the most recent successful run completed (RFC 3339). */
    private String lastSuccessfulTime;

    /** The most recent completed run. */
    private RunSummary lastRun;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public String getLastScheduleTime() {
        return lastScheduleTime;
    }

    public void setLastScheduleTime(String lastScheduleTime) {
        this.lastScheduleTime = lastScheduleTime;
    }

    public String getNextScheduleTime() {
        return nextScheduleTime;
    }

    public void setNextScheduleTime(String nextScheduleTime) {
        this.nextScheduleTime = nextScheduleTime;
    }

    public String getLastSuccessfulTime() {
        return lastSuccessfulTime;
    }

    public void setLastSuccessfulTime(String lastSuccessfulTime) {
        this.lastSuccessfulTime = lastSuccessfulTime;
    }

    public RunSummary getLastRun() {
        return lastRun;
    }

    public void setLastRun(RunSummary lastRun) {
        this.lastRun = lastRun;
    }
}
