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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * One run of an RCloneSync in the same namespace. Its trigger creates them; creating one by hand runs the sync once.
 *
 * <p>The run executes as a Job, in the run's namespace, or in the operator namespace if the sync uses an
 * RCloneClusterRemote.
 */
@Group(RCloneRemote.GROUP)
@Version(RCloneRemote.VERSION)
@ValidationRule(value = "has(self.spec)", message = "spec is required")
@AdditionalPrinterColumn(name = "Sync", jsonPath = ".spec.syncRef.name", type = AdditionalPrinterColumn.Type.STRING)
@AdditionalPrinterColumn(
        name = "Succeeded",
        jsonPath = ".status.conditions[?(@.type==\"Succeeded\")].status",
        type = AdditionalPrinterColumn.Type.STRING)
@AdditionalPrinterColumn(
        name = "Reason",
        jsonPath = ".status.conditions[?(@.type==\"Succeeded\")].reason",
        type = AdditionalPrinterColumn.Type.STRING)
@AdditionalPrinterColumn(name = "Started", jsonPath = ".status.startTime", type = AdditionalPrinterColumn.Type.DATE)
@AdditionalPrinterColumn(
        name = "Completed",
        jsonPath = ".status.completionTime",
        type = AdditionalPrinterColumn.Type.DATE,
        priority = 1)
@AdditionalPrinterColumn(
        name = "Age",
        jsonPath = ".metadata.creationTimestamp",
        type = AdditionalPrinterColumn.Type.DATE)
public class RCloneSyncRun extends CustomResource<RCloneSyncRunSpec, RCloneSyncRunStatus> implements Namespaced {

    @Override
    protected RCloneSyncRunStatus initStatus() {
        return new RCloneSyncRunStatus();
    }

    /** Whether the run is pending or in progress: its Succeeded condition is absent or Unknown. */
    @JsonIgnore
    public boolean isUnfinished() {
        return getStatus() == null
                || getStatus().getConditions().stream()
                        .filter(c -> c.getType().equals(RCloneSyncRunStatus.SUCCEEDED))
                        .noneMatch(c -> ConditionStatus.TRUE.equals(c.getStatus())
                                || ConditionStatus.FALSE.equals(c.getStatus()));
    }
}
