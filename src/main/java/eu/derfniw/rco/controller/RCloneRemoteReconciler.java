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
import eu.derfniw.rco.remote.RemoteSpecValidator;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@ControllerConfiguration(
        name = "rcloneremote",
        maxReconciliationInterval = @MaxReconciliationInterval(interval = 1, timeUnit = TimeUnit.HOURS))
@AdditionalRBACRules({
    @RBACRule(apiGroups = "", resources = "secrets", verbs = "get"),
})
public class RCloneRemoteReconciler extends AbstractRemoteReconciler<RCloneRemote> {

    public RCloneRemoteReconciler(OperatorConfig config, RemoteSpecValidator validator) {
        super(config, validator);
    }
}
