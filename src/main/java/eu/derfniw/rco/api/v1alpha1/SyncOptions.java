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
import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Min;
import java.util.List;

/** Tunes how rclone runs a sync. Unset fields use rclone's defaults. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncOptions {

    /** When files missing from the source are deleted from the destination (rclone flags: --delete-*). */
    public enum DeleteMode {
        /** Before transferring; frees space first, but deletes even if the transfers then fail. */
        @JsonProperty("before")
        BEFORE,
        /** While transferring. */
        @JsonProperty("during")
        DURING,
        /** After all transfers succeeded. */
        @JsonProperty("after")
        AFTER
    }

    /** Reports what would be transferred and deleted without changing anything (rclone flag: --dry-run). */
    private Boolean dryRun;

    /** Number of file transfers to run in parallel (rclone flag: --transfers). */
    @Min(1)
    private Integer transfers;

    /** Number of checkers comparing files in parallel (rclone flag: --checkers). */
    @Min(1)
    private Integer checkers;

    /** When files missing from the source are deleted from the destination (rclone flags: --delete-*). */
    @Default("after")
    private DeleteMode deleteMode;

    /**
     * Rules selecting which files are synced, applied in order: the first rule that matches a path decides. Paths
     * that match no rule are included (rclone flag: --filter).
     */
    private List<FilterRule> filters;

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Integer getTransfers() {
        return transfers;
    }

    public void setTransfers(Integer transfers) {
        this.transfers = transfers;
    }

    public Integer getCheckers() {
        return checkers;
    }

    public void setCheckers(Integer checkers) {
        this.checkers = checkers;
    }

    public DeleteMode getDeleteMode() {
        return deleteMode;
    }

    public void setDeleteMode(DeleteMode deleteMode) {
        this.deleteMode = deleteMode;
    }

    public List<FilterRule> getFilters() {
        return filters;
    }

    public void setFilters(List<FilterRule> filters) {
        this.filters = filters;
    }
}
