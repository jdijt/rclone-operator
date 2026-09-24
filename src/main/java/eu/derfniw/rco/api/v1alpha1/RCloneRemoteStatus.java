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

/** Observed state of an RCloneRemote or RCloneClusterRemote. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RCloneRemoteStatus {

    /** Condition type: the remote is ready to use in sync jobs. */
    public static final String READY = "Ready";

    public static final String REASON_VALID = "Valid";
    public static final String REASON_INVALID = "InvalidSpec";

    /**
     * The current state of the remote. Each condition has a unique type.
     *
     * <p>Condition types include "Ready": the resource is ready to use in sync jobs.
     */
    private List<Condition> conditions = new ArrayList<>();

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }
}
