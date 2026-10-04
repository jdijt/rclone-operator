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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Observed state of an RCloneSyncRun. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RCloneSyncRunStatus {

    /**
     * Condition type: the run completed successfully. Unknown while the run is pending or in progress, then True or
     * False.
     */
    public static final String SUCCEEDED = "Succeeded";

    /** Unknown: waiting, the RCloneSync does not exist. */
    public static final String REASON_SYNC_NOT_FOUND = "SyncNotFound";
    /** Unknown: waiting, the RCloneSync or a remote it uses is not Ready. */
    public static final String REASON_SYNC_NOT_READY = "SyncNotReady";
    /** Unknown: the Job is running the sync. */
    public static final String REASON_RUNNING = "Running";
    /** True: the Job completed. */
    public static final String REASON_COMPLETED = "Completed";
    /** False: the Job failed. */
    public static final String REASON_FAILED = "Failed";
    /** False: the Job disappeared before it finished. */
    public static final String REASON_JOB_NOT_FOUND = "JobNotFound";
    /** False: a value would not fit in the rclone configuration, e.g. a Secret value with a line break. */
    public static final String REASON_INVALID_CREDENTIALS = "InvalidCredentials";

    /**
     * The current state of the run. Each condition has a unique type.
     *
     * <p>Condition types include "Succeeded": Unknown while the run is pending or in progress, then True or False.
     */
    private List<Condition> conditions = new ArrayList<>();

    /** The metadata.generation of the RCloneSync whose spec the run used. */
    private Long syncGeneration;

    /** Namespace of the Job executing the run: this run's namespace, or the operator namespace. */
    private String jobNamespace;

    /** Name of the Job executing the run. */
    private String jobName;

    /** When the run started. */
    private Instant startTime;

    /** When the run ended. */
    private Instant completionTime;

    /** What the run transferred. Updated while the run is in progress. */
    private RunStatistics statistics;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public Long getSyncGeneration() {
        return syncGeneration;
    }

    public void setSyncGeneration(Long syncGeneration) {
        this.syncGeneration = syncGeneration;
    }

    public String getJobNamespace() {
        return jobNamespace;
    }

    public void setJobNamespace(String jobNamespace) {
        this.jobNamespace = jobNamespace;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
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
