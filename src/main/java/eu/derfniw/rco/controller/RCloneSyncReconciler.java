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

import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.TimeUnit;

/**
 * Reports whether an RCloneSync can run (its spec is valid and every remote it uses is Ready) and creates an
 * RCloneSyncRun whenever its trigger fires.
 */
@ApplicationScoped
@ControllerConfiguration(
        name = "rclonesync",
        maxReconciliationInterval = @MaxReconciliationInterval(interval = 1, timeUnit = TimeUnit.HOURS))
public class RCloneSyncReconciler implements Reconciler<RCloneSync> {

    @Override
    public UpdateControl<RCloneSync> reconcile(RCloneSync resource, Context<RCloneSync> context) {
        // Not implemented yet: the tests are written first.
        return UpdateControl.noUpdate();
    }
}
