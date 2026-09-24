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

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * A cluster-scoped rclone remote. It can be used by syncs in any namespace; its Secrets are resolved in the operator
 * namespace.
 */
@Group(RCloneRemote.GROUP)
@Version(RCloneRemote.VERSION)
@ValidationRule(value = "has(self.spec)", message = "spec is required")
@ValidationRule(
        value = "has(self.spec.crypt) ? self.spec.crypt.remoteRef.kind == 'RCloneClusterRemote' : true",
        message = "RCloneClusterRemote can only refer to other RCloneClusterRemotes.")
@AdditionalPrinterColumn(name = "Type", jsonPath = ".spec.type", type = AdditionalPrinterColumn.Type.STRING)
@AdditionalPrinterColumn(
        name = "Ready",
        jsonPath = ".status.conditions[?(@.type==\"Ready\")].status",
        type = AdditionalPrinterColumn.Type.STRING)
@AdditionalPrinterColumn(
        name = "Reason",
        jsonPath = ".status.conditions[?(@.type==\"Ready\")].reason",
        type = AdditionalPrinterColumn.Type.STRING,
        priority = 1)
@AdditionalPrinterColumn(
        name = "Age",
        jsonPath = ".metadata.creationTimestamp",
        type = AdditionalPrinterColumn.Type.DATE)
public class RCloneClusterRemote extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> {

    @Override
    protected RCloneRemoteStatus initStatus() {
        return new RCloneRemoteStatus();
    }
}
