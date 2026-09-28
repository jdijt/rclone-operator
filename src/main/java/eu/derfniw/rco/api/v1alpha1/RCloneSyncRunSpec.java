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
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;

/** Desired state of an RCloneSyncRun. It cannot be changed after creation. */
@ValidationRule(value = "self == oldSelf", message = "spec is immutable")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RCloneSyncRunSpec {

    /**
     * The RCloneSync to run, in the same namespace. The run uses the sync's spec as it is when the run starts, not
     * when it was created.
     */
    @Required
    private SyncRef syncRef;

    public RCloneSyncRunSpec() {}

    public RCloneSyncRunSpec(SyncRef syncRef) {
        this.syncRef = syncRef;
    }

    public SyncRef getSyncRef() {
        return syncRef;
    }

    public void setSyncRef(SyncRef syncRef) {
        this.syncRef = syncRef;
    }
}
