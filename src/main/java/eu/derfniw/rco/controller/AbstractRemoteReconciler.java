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
import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.ConditionStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.RemoteRef.Kind;
import eu.derfniw.rco.remote.RemoteValidator;
import eu.derfniw.rco.validation.FieldError;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates an RCloneRemote or RCloneClusterRemote, checks that the Secrets and keys it refers to exist and, for a
 * crypt remote, that the remote it wraps is Ready, and reports the result in its Ready condition. Remotes are
 * re-checked periodically; Secrets aren't watched, so a remote waiting for one is re-checked at
 * {@link OperatorConfig#secretRecheckInterval()}. Remotes are watched: a change to a remote reconciles the crypt
 * remotes that wrap it.
 */
abstract class AbstractRemoteReconciler<R extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>
        implements Reconciler<R> {

    @Inject
    RemoteValidator validator;

    @Inject
    KubernetesClient client;

    @Inject
    OperatorConfig config;

    /** Index of the primary cache: crypt remotes by the remote they wrap, as {@link #cryptWrappedRemoteKey}. */
    private static final String WRAPS_INDEX = "wraps";

    /** The namespace the remote's Secret references are resolved in. */
    protected abstract String secretNamespace(R resource);

    protected abstract List<Class<? extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>> canWrap();

    @Override
    public List<EventSource<?, R>> prepareEventSources(EventSourceContext<R> context) {
        context.getPrimaryCache().addIndexer(WRAPS_INDEX, AbstractRemoteReconciler::cryptWrapsKeys);
        return canWrap().stream()
                .<EventSource<?, R>>map(type -> cryptWrappedRemotesEvents(type, context))
                .toList();
    }

    /**
     * An event source that, when a remote of {@code type} changes, reconciles the crypt remotes wrapping it. A
     * namespaced remote can only be wrapped from its own namespace.
     */
    protected <W extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>
            InformerEventSource<W, R> cryptWrappedRemotesEvents(Class<W> type, EventSourceContext<R> context) {
        var kind = Kind.forType(type);
        var config = InformerEventSourceConfiguration.from(type, context.getPrimaryResourceClass())
                .withName(cryptWrappedEventSourceName(type))
                .withSecondaryToPrimaryMapper(wrapped -> context
                        .getPrimaryCache()
                        .byIndex(
                                WRAPS_INDEX,
                                cryptWrappedRemoteKey(
                                        kind, wrapped.getMetadata().getName()))
                        .stream()
                        .filter(crypt -> kind == RemoteRef.Kind.CLUSTER_REMOTE
                                || Objects.equals(
                                        crypt.getMetadata().getNamespace(),
                                        wrapped.getMetadata().getNamespace()))
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
                .build();
        return new InformerEventSource<>(config, context);
    }

    private static String cryptWrappedEventSourceName(Class<?> type) {
        return "wrapped-" + type.getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static List<String> cryptWrapsKeys(CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> remote) {
        var spec = remote.getSpec();
        if (BackendType.CRYPT.equals(spec.getType())) {
            var ref = spec.getCrypt().getRemoteRef();
            return List.of(cryptWrappedRemoteKey(ref.getKind(), ref.getName()));
        }
        return List.of();
    }

    private static String cryptWrappedRemoteKey(RemoteRef.Kind kind, String name) {
        return kind + "/" + name;
    }

    @Override
    public UpdateControl<R> reconcile(R resource, Context<R> context) {
        var errors = validator.validate(resource);
        var reason = RCloneRemoteStatus.REASON_INVALID;
        // Secrets are only looked up for a valid spec: an invalid one is reported as such first.
        if (errors.isEmpty()) {
            errors = unresolvedSecretRefs(resource);
            reason = RCloneRemoteStatus.REASON_SECRET_NOT_FOUND;
        }
        if (errors.isEmpty() && BackendType.CRYPT.equals(resource.getSpec().getType())) {
            errors.addAll(
                    getCryptWrappedRemoteStatus(resource, context).stream().toList());
            reason = RCloneRemoteStatus.REASON_REMOTE_NOT_READY;
        }
        boolean ready = errors.isEmpty();

        var condition = new ConditionBuilder()
                .withType(RCloneRemoteStatus.READY)
                .withStatus(ready ? ConditionStatus.TRUE : ConditionStatus.FALSE)
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
     * An error per Secret reference whose Secret or key does not exist, in field order.
     * Only names and keys are reported, never Secret data.
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

    /** Reads the wrapped remote from the cache of the event source that watches its kind. */
    private Optional<FieldError> getCryptWrappedRemoteStatus(R resource, Context<R> context) {
        var ref = resource.getSpec().getCrypt().getRemoteRef();
        Optional<? extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>> otherRemote =
                switch (ref.getKind()) {
                    // validation ensures only RCloneRemotes point to non-cluster remotes.
                    case REMOTE ->
                        context.getSecondaryResource(
                                RCloneRemote.class,
                                cryptWrappedEventSourceName(RCloneRemote.class),
                                ref.getName(),
                                resource.getMetadata().getNamespace());
                    case CLUSTER_REMOTE ->
                        context.getSecondaryResource(
                                RCloneClusterRemote.class,
                                cryptWrappedEventSourceName(RCloneClusterRemote.class),
                                ref.getName(),
                                null);
                };
        if (otherRemote.isEmpty()) {
            return Optional.of(
                    FieldError.notFound("spec.crypt.remoteRef", ref.getName(), "cannot find referenced remote"));
        }
        var status = otherRemote.get().getStatus();
        if (status != null && Conditions.isTrue(status.getConditions(), RCloneRemoteStatus.READY)) {
            return Optional.empty();
        }
        return Optional.of(
                FieldError.invalid("spec.crypt.remoteRef", ref.getName(), "referenced remote is not in ready state"));
    }
}
