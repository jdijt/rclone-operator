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

import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Executes an RCloneSyncRun once its RCloneSync is Ready: renders the remotes into a per-run Secret, runs rclone in a
 * Job, and reports the outcome in the Succeeded condition.
 */
@ApplicationScoped
@ControllerConfiguration(name = "rclonesyncrun")
public class RCloneSyncRunReconciler implements Reconciler<RCloneSyncRun>, Cleaner<RCloneSyncRun> {

    @Override
    public UpdateControl<RCloneSyncRun> reconcile(RCloneSyncRun resource, Context<RCloneSyncRun> context) {
        // Not implemented yet: the tests are written first.
        return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(RCloneSyncRun resource, Context<RCloneSyncRun> context) {
        return DeleteControl.defaultDelete();
    }
}
