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

import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.remote.RemoteValidator;
import eu.derfniw.rco.validation.FieldError;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.inject.Inject;
import java.util.stream.Collectors;

/**
 * Validates an RCloneRemote or RCloneClusterRemote and reports the result in its Ready condition. Valid remotes are
 * re-validated periodically.
 */
abstract class AbstractRemoteReconciler<R extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>
        implements Reconciler<R> {

    @Inject
    RemoteValidator validator;

    @Override
    public UpdateControl<R> reconcile(R resource, Context<R> context) {
        var errors = validator.validate(resource);
        boolean valid = errors.isEmpty();

        var condition = new ConditionBuilder()
                .withType(RCloneRemoteStatus.READY)
                .withStatus(valid ? "True" : "False")
                .withReason(valid ? RCloneRemoteStatus.REASON_VALID : RCloneRemoteStatus.REASON_INVALID)
                .withMessage(errors.stream().map(FieldError::toString).collect(Collectors.joining("; ")))
                .withObservedGeneration(resource.getMetadata().getGeneration())
                .build();

        if (resource.getStatus() == null) {
            resource.setStatus(new RCloneRemoteStatus());
        }
        boolean changed = Conditions.set(resource.getStatus().getConditions(), condition);

        if (changed) {
            return UpdateControl.patchStatus(resource);
        } else {
            return UpdateControl.noUpdate();
        }
    }
}
