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
import java.time.Instant;

/** A completed RCloneSyncRun, summarized in the status of its RCloneSync. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunSummary {

    /** How a run ended. */
    public enum Result {
        @JsonProperty("Succeeded")
        SUCCEEDED,
        @JsonProperty("Failed")
        FAILED
    }

    /** Name of the RCloneSyncRun. It may have been removed since, by the history limits. */
    private String name;

    /** How the run ended. */
    private Result result;

    /** When the run started. */
    private Instant startTime;

    /** When the run ended. */
    private Instant completionTime;

    /** What the run transferred. */
    private RunStatistics statistics;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(Instant completionTime) {
        this.completionTime = completionTime;
    }

    public RunStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(RunStatistics statistics) {
        this.statistics = statistics;
    }
}
