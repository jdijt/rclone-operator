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
package eu.derfniw.rco.controller;

import eu.derfniw.rco.OperatorConfig;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.inject.Singleton;

@Singleton
@ControllerConfiguration(name = "rcloneremote")
@AdditionalRBACRules({
    @RBACRule(apiGroups = "", resources = "secrets", verbs = "get"),
    @RBACRule(apiGroups = RCloneRemote.GROUP, resources = "rcloneremotes", verbs = {"get","watch","list"})
})
public class RCloneRemoteReconciler extends AbstractRemoteReconciler<RCloneRemote> {

    public RCloneRemoteReconciler(OperatorConfig config) {
        super(config);
    }
}
