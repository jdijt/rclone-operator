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
import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import jakarta.validation.Valid;

/** Desired state of an RCloneSync: makes the destination match the source (rclone sync), whenever the trigger fires. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RCloneSyncSpec {

    /** Where files are copied from. It is never modified. */
    @Required
    private SyncEndpoint source;

    /** Where files are copied to. Files that are not in the source are deleted here. */
    @Required
    private SyncEndpoint destination;

    /** Decides when the sync runs. */
    @Required
    @Valid
    private SyncTrigger trigger;

    /** Tunes how rclone runs the sync. */
    private SyncOptions options;

    /** Stops the trigger from creating runs. Existing runs, and runs created by hand, are not affected. */
    @Default("false")
    private Boolean suspend;

    /** Number of succeeded RCloneSyncRuns to keep. */
    @Default("3")
    @Min(0)
    private Integer successfulRunsHistoryLimit;

    /** Number of failed RCloneSyncRuns to keep. */
    @Default("1")
    @Min(0)
    private Integer failedRunsHistoryLimit;

    public SyncEndpoint getSource() {
        return source;
    }

    public void setSource(SyncEndpoint source) {
        this.source = source;
    }

    public SyncEndpoint getDestination() {
        return destination;
    }

    public void setDestination(SyncEndpoint destination) {
        this.destination = destination;
    }

    public SyncTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(SyncTrigger trigger) {
        this.trigger = trigger;
    }

    public SyncOptions getOptions() {
        return options;
    }

    public void setOptions(SyncOptions options) {
        this.options = options;
    }

    public Boolean getSuspend() {
        return suspend;
    }

    public void setSuspend(Boolean suspend) {
        this.suspend = suspend;
    }

    public Integer getSuccessfulRunsHistoryLimit() {
        return successfulRunsHistoryLimit;
    }

    public void setSuccessfulRunsHistoryLimit(Integer successfulRunsHistoryLimit) {
        this.successfulRunsHistoryLimit = successfulRunsHistoryLimit;
    }

    public Integer getFailedRunsHistoryLimit() {
        return failedRunsHistoryLimit;
    }

    public void setFailedRunsHistoryLimit(Integer failedRunsHistoryLimit) {
        this.failedRunsHistoryLimit = failedRunsHistoryLimit;
    }
}
