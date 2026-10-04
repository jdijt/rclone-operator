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

import eu.derfniw.rco.api.v1alpha1.ConditionStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRunSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.SyncEndpoint;
import eu.derfniw.rco.api.v1alpha1.SyncRef;
import eu.derfniw.rco.sync.SyncSchedule;
import eu.derfniw.rco.sync.SyncValidator;
import eu.derfniw.rco.validation.FieldError;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reports whether an RCloneSync can run (its spec is valid and every remote it uses is Ready) and creates an
 * RCloneSyncRun whenever its trigger fires. Remotes and runs are watched: a change to a remote reconciles the syncs
 * that use it, and a change to a run reconciles its sync, so a run held back by concurrencyPolicy Forbid starts once
 * the other run finishes.
 */
@ApplicationScoped
@ControllerConfiguration(
        name = "rclonesync",
        maxReconciliationInterval = @MaxReconciliationInterval(interval = 1, timeUnit = TimeUnit.HOURS))
@AdditionalRBACRules({
    @RBACRule(
            apiGroups = RCloneRemote.GROUP,
            resources = "rclonesyncruns",
            verbs = {"get", "list", "watch", "create", "delete"}),
    @RBACRule(
            apiGroups = RCloneRemote.GROUP,
            resources = {"rcloneremotes", "rcloneclusterremotes"},
            verbs = {"get", "list", "watch"}),
})
public class RCloneSyncReconciler implements Reconciler<RCloneSync> {

    @Inject
    SyncValidator validator;

    @Inject
    KubernetesClient client;

    /** Index of the primary cache: syncs by the remotes they use, as {@link #remoteKey}. */
    private static final String REMOTES_INDEX = "remotes";

    @Override
    public List<EventSource<?, RCloneSync>> prepareEventSources(EventSourceContext<RCloneSync> context) {
        context.getPrimaryCache().addIndexer(REMOTES_INDEX, RCloneSyncReconciler::remoteKeys);

        // Runs map to the sync they name, so runs created by hand count too.
        var runs = InformerEventSourceConfiguration.from(RCloneSyncRun.class, RCloneSync.class)
                .withSecondaryToPrimaryMapper(run -> Set.of(new ResourceID(
                        run.getSpec().getSyncRef().getName(), run.getMetadata().getNamespace())))
                .build();
        return List.of(
                new InformerEventSource<>(runs, context),
                remoteEvents(RCloneRemote.class, context),
                remoteEvents(RCloneClusterRemote.class, context));
    }

    /**
     * An event source that, when a remote of {@code type} changes, reconciles the syncs using it. A namespaced remote
     * can only be used from its own namespace.
     */
    private static <W extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>>
            InformerEventSource<W, RCloneSync> remoteEvents(Class<W> type, EventSourceContext<RCloneSync> context) {
        var kind = RemoteRef.Kind.forType(type);
        var config = InformerEventSourceConfiguration.from(type, RCloneSync.class)
                .withSecondaryToPrimaryMapper(remote -> context
                        .getPrimaryCache()
                        .byIndex(
                                REMOTES_INDEX,
                                remoteKey(
                                        kind,
                                        remote.getMetadata().getNamespace(),
                                        remote.getMetadata().getName()))
                        .stream()
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
                .build();
        return new InformerEventSource<>(config, context);
    }

    private static List<String> remoteKeys(RCloneSync sync) {
        var spec = sync.getSpec();
        if (spec == null) {
            return List.of();
        }
        return Stream.of(spec.getSource(), spec.getDestination())
                .filter(Objects::nonNull)
                .map(SyncEndpoint::getRemoteRef)
                .filter(Objects::nonNull)
                .map(ref -> remoteKey(ref.getKind(), sync.getMetadata().getNamespace(), ref.getName()))
                .distinct()
                .toList();
    }

    /** Cluster remotes have no namespace: any sync may use them. */
    private static String remoteKey(RemoteRef.Kind kind, String namespace, String name) {
        return kind == RemoteRef.Kind.CLUSTER_REMOTE ? kind + "//" + name : kind + "/" + namespace + "/" + name;
    }

    @Override
    public UpdateControl<RCloneSync> reconcile(RCloneSync resource, Context<RCloneSync> context) {
        var now = Instant.now();
        var errors = validator.validate(resource);
        var reason = RCloneSyncStatus.REASON_INVALID;
        // Remotes are only looked up for a valid spec: an invalid one is reported as such first.
        if (errors.isEmpty()) {
            var remoteError = unusableRemote(resource, context);
            if (remoteError.isPresent()) {
                errors = List.of(remoteError.get());
                reason = remoteError.get().type() == FieldError.Type.NOT_FOUND
                        ? RCloneSyncStatus.REASON_REMOTE_NOT_FOUND
                        : RCloneSyncStatus.REASON_REMOTE_NOT_READY;
            }
        }
        boolean ready = errors.isEmpty();

        if (resource.getStatus() == null) {
            resource.setStatus(new RCloneSyncStatus());
        }
        var status = resource.getStatus();
        var lastScheduleTime = Optional.ofNullable(status.getLastScheduleTime());
        var nextScheduleTime = Optional.ofNullable(status.getNextScheduleTime());
        Optional<Instant> newLastScheduleTime = Optional.empty();
        Optional<Instant> newNextScheduleTime = Optional.empty();
        if (ready && !resource.getSpec().isSuspend()) {
            var schedule =
                    SyncSchedule.of(resource.getSpec().getTrigger().getCron().getExpression());
            var since = lastScheduleTime.orElseGet(
                    () -> Instant.parse(resource.getMetadata().getCreationTimestamp()));
            newLastScheduleTime = scheduleRun(resource, schedule, since, context, now);
            newNextScheduleTime = Optional.of(schedule.nextAfter(now));
        }

        var condition = new ConditionBuilder()
                .withType(RCloneSyncStatus.READY)
                .withStatus(ready ? ConditionStatus.TRUE : ConditionStatus.FALSE)
                .withReason(ready ? RCloneSyncStatus.REASON_VALID : reason)
                .withMessage(errors.stream().map(FieldError::toString).collect(Collectors.joining("; ")))
                .withObservedGeneration(resource.getMetadata().getGeneration())
                .build();

        boolean changed = Conditions.set(status.getConditions(), condition)
                || (newLastScheduleTime.isPresent() && !newLastScheduleTime.equals(lastScheduleTime))
                || !newNextScheduleTime.equals(nextScheduleTime);

        newLastScheduleTime.ifPresent(status::setLastScheduleTime);
        // Unset while suspended or not Ready.
        status.setNextScheduleTime(newNextScheduleTime.orElse(null));

        UpdateControl<RCloneSync> control = changed ? UpdateControl.patchStatus(resource) : UpdateControl.noUpdate();
        newNextScheduleTime.ifPresent(next -> control.rescheduleAfter(Duration.between(now, next)));
        return control;
    }

    /**
     * The first of source and destination that doesn't exist (a NOT_FOUND error) or isn't Ready.
     */
    private static Optional<FieldError> unusableRemote(RCloneSync sync, Context<RCloneSync> context) {
        var spec = sync.getSpec();
        return Stream.of(
                        unusableRemote("spec.source.remoteRef", spec.getSource().getRemoteRef(), context),
                        unusableRemote(
                                "spec.destination.remoteRef",
                                spec.getDestination().getRemoteRef(),
                                context))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<FieldError> unusableRemote(String field, RemoteRef ref, Context<RCloneSync> context) {
        Optional<? extends CustomResource<RCloneRemoteSpec, RCloneRemoteStatus>> remote =
                switch (ref.getKind()) {
                    case REMOTE -> context.getSecondaryResource(RCloneRemote.class, null, ref.getName());
                    case CLUSTER_REMOTE ->
                        context.getSecondaryResource(RCloneClusterRemote.class, null, ref.getName(), null);
                };
        if (remote.isEmpty()) {
            return Optional.of(FieldError.notFound(field, ref.getName(), "cannot find referenced remote"));
        }
        var status = remote.get().getStatus();
        if (status != null && Conditions.isTrue(status.getConditions(), RCloneRemoteStatus.READY)) {
            return Optional.empty();
        }
        return Optional.of(FieldError.invalid(field, ref.getName(), "referenced remote is not in ready state"));
    }

    /**
     * Creates the run that is due now, if any, applying the concurrency policy to the sync's unfinished runs.
     *
     * @param since the most recent time a run was created for, or when the sync was created
     * @return the scheduled time of the run, once it exists; empty if none is due or concurrencyPolicy Forbid holds
     *     it back
     */
    private Optional<Instant> scheduleRun(
            RCloneSync sync, SyncSchedule schedule, Instant since, Context<RCloneSync> context, Instant now) {
        var due = schedule.due(since, now, Duration.ofSeconds(sync.getSpec().getStartingDeadlineSeconds()));
        if (due.isEmpty()) {
            return Optional.empty();
        }

        var name = runName(sync, due.get());
        // The due run itself may exist already, if a previous reconcile created it but did not record it.
        var unfinished = context.getSecondaryResourcesAsStream(RCloneSyncRun.class)
                .filter(run -> !run.getMetadata().getName().equals(name))
                .filter(RCloneSyncRun::isUnfinished)
                .toList();
        if (!unfinished.isEmpty()) {
            switch (sync.getSpec().getConcurrencyPolicy()) {
                case FORBID -> {
                    return Optional.empty();
                }
                case REPLACE -> unfinished.forEach(run -> client.resource(run).delete());
                case ALLOW -> {}
            }
        }
        createRun(sync, name);
        return due;
    }

    /** Runs are named after their scheduled minute, so a run is never created twice. */
    private static String runName(RCloneSync sync, Instant scheduled) {
        return sync.getMetadata().getName() + "-" + scheduled.getEpochSecond() / 60;
    }

    /** Creates the run, owned by the sync. A run that already exists counts as created. */
    private void createRun(RCloneSync sync, String name) {
        var run = new RCloneSyncRun();
        run.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(sync.getMetadata().getNamespace())
                .withOwnerReferences(new OwnerReferenceBuilder()
                        .withApiVersion(HasMetadata.getApiVersion(RCloneSync.class))
                        .withKind(HasMetadata.getKind(RCloneSync.class))
                        .withName(sync.getMetadata().getName())
                        .withUid(sync.getMetadata().getUid())
                        .withController(true)
                        .withBlockOwnerDeletion(true)
                        .build())
                .build());
        run.setSpec(new RCloneSyncRunSpec(new SyncRef(sync.getMetadata().getName())));
        try {
            client.resource(run).create();
        } catch (KubernetesClientException e) {
            if (e.getCode() != HttpURLConnection.HTTP_CONFLICT) {
                throw e;
            }
        }
    }
}
