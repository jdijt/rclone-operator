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
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.remote.RemoteValidator;
import eu.derfniw.rco.validation.FieldError;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates an RCloneRemote or RCloneClusterRemote, checks that the Secrets and keys it refers to exist, and reports
 * the result in its Ready condition. Remotes are re-checked periodically; Secrets aren't watched, so a remote waiting
 * for one is re-checked at {@link OperatorConfig#secretRecheckInterval()}.
 */
abstract class AbstractRemoteReconciler<R extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>
        implements Reconciler<R> {

    @Inject
    RemoteValidator validator;

    @Inject
    KubernetesClient client;

    @Inject
    OperatorConfig config;

    /** The namespace the remote's Secret references are resolved in. */
    protected abstract String secretNamespace(R resource);

    @Override
    public UpdateControl<R> reconcile(R resource, Context<R> context) {
        var errors = validator.validate(resource);
        var reason = RCloneRemoteStatus.REASON_INVALID;
        // Secrets are only looked up for a valid spec: an invalid one is reported as such first.
        if (errors.isEmpty()) {
            errors = unresolvedSecretRefs(resource);
            reason = RCloneRemoteStatus.REASON_SECRET_NOT_FOUND;
        }
        boolean ready = errors.isEmpty();

        var condition = new ConditionBuilder()
                .withType(RCloneRemoteStatus.READY)
                .withStatus(ready ? "True" : "False")
                .withReason(ready ? RCloneRemoteStatus.REASON_VALID : reason)
                .withMessage(errors.stream().map(FieldError::toString).collect(Collectors.joining("; ")))
                .withObservedGeneration(resource.getMetadata().getGeneration())
                .build();

        if (resource.getStatus() == null) {
            resource.setStatus(new RCloneRemoteStatus());
        }
        boolean changed = Conditions.set(resource.getStatus().getConditions(), condition);

        UpdateControl<R> control = changed ? UpdateControl.patchStatus(resource) : UpdateControl.noUpdate();
        if (RCloneRemoteStatus.REASON_SECRET_NOT_FOUND.equals(condition.getReason())) {
            control.rescheduleAfter(config.secretRecheckInterval());
        }
        return control;
    }

    /**
     * An error per Secret reference whose Secret or key does not exist, in field order. Only names and keys are
     * reported, never Secret data.
     */
    private List<FieldError> unresolvedSecretRefs(R resource) {
        var namespace = secretNamespace(resource);
        var secrets = new HashMap<String, Optional<Secret>>();
        var errors = new ArrayList<FieldError>();
        resource.getSpec().secretKeyRefs().forEach((field, ref) -> {
            var secret = secrets.computeIfAbsent(
                    ref.getName(),
                    name -> Optional.ofNullable(client.secrets()
                            .inNamespace(namespace)
                            .withName(name)
                            .get()));
            if (secret.isEmpty()) {
                errors.add(FieldError.notFound("spec." + field, ref.getName(), "Secret does not exist"));
            } else if (secret.get().getData() == null || !secret.get().getData().containsKey(ref.getKey())) {
                errors.add(FieldError.notFound(
                        "spec." + field, ref.getKey(), "key does not exist in Secret " + ref.getName()));
            }
        });
        return errors;
    }
}
