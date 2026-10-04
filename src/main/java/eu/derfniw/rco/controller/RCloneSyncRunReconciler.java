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
import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRunStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.run.Obscure;
import eu.derfniw.rco.run.RCloneConfig;
import eu.derfniw.rco.run.ResolvedRemote;
import eu.derfniw.rco.run.SyncCommand;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes an RCloneSyncRun once its RCloneSync is Ready: renders the remotes into a per-run Secret, runs rclone in a
 * Job, and reports the outcome in the Succeeded condition.
 *
 * <p>The Job and Secret are named after the run's UID. They are in the run's namespace and owned by the run, unless the
 * sync uses an RCloneClusterRemote: its credentials stay in the operator namespace, so the Job and Secret go there,
 * and cleanup deletes them since owner references can't cross namespaces. The Secret is deleted once the run finishes.
 */
@ApplicationScoped
@ControllerConfiguration(name = "rclonesyncrun")
@AdditionalRBACRules({
    @RBACRule(
            apiGroups = "batch",
            resources = "jobs",
            verbs = {"get", "list", "watch", "create", "delete"}),
    @RBACRule(
            apiGroups = "",
            resources = "secrets",
            verbs = {"get", "create", "delete"}),
    @RBACRule(
            apiGroups = RCloneRemote.GROUP,
            resources = "rclonesyncs",
            verbs = {"get", "list", "watch"}),
    @RBACRule(
            apiGroups = RCloneRemote.GROUP,
            resources = {"rcloneremotes", "rcloneclusterremotes"},
            verbs = "get"),
})
public class RCloneSyncRunReconciler implements Reconciler<RCloneSyncRun>, Cleaner<RCloneSyncRun> {

    static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    static final String MANAGED_BY = "rclone-operator";
    /** Annotations, not labels, since a run name can be longer than a label value may be. */
    static final String RUN_NAMESPACE_ANNOTATION = RCloneRemote.GROUP + "/run-namespace";

    static final String RUN_NAME_ANNOTATION = RCloneRemote.GROUP + "/run-name";
    /** On the Job: the generation of the sync spec it runs, for taking over a Job whose creation wasn't recorded. */
    static final String SYNC_GENERATION_ANNOTATION = RCloneRemote.GROUP + "/sync-generation";

    private static final String NAME_PREFIX = "rclone-run-";
    /** Crypt remotes nest at most this deep; deeper means a cycle (which never becomes Ready). */
    private static final int MAX_DEPTH = 16;
    /** Index of the primary cache: runs by the sync they run, as namespace/name. */
    private static final String SYNC_INDEX = "sync";

    @Inject
    KubernetesClient client;

    @Inject
    OperatorConfig config;

    @Override
    public List<EventSource<?, RCloneSyncRun>> prepareEventSources(EventSourceContext<RCloneSyncRun> context) {
        context.getPrimaryCache()
                .addIndexer(
                        SYNC_INDEX,
                        // Only unfinished runs care about their sync.
                        run -> run.isUnfinished()
                                ? List.of(syncKey(
                                        run.getMetadata().getNamespace(),
                                        run.getSpec().getSyncRef().getName()))
                                : List.of());

        var jobs = InformerEventSourceConfiguration.from(Job.class, RCloneSyncRun.class)
                .withLabelSelector(MANAGED_BY_LABEL + "=" + MANAGED_BY)
                .withSecondaryToPrimaryMapper(job -> {
                    var annotations = job.getMetadata().getAnnotations();
                    if (annotations == null || !annotations.containsKey(RUN_NAME_ANNOTATION)) {
                        return Set.of();
                    }
                    return Set.of(new ResourceID(
                            annotations.get(RUN_NAME_ANNOTATION), annotations.get(RUN_NAMESPACE_ANNOTATION)));
                })
                .build();
        // A run waiting for its sync starts when the sync becomes Ready.
        var syncs = InformerEventSourceConfiguration.from(RCloneSync.class, RCloneSyncRun.class)
                .withSecondaryToPrimaryMapper(sync -> context
                        .getPrimaryCache()
                        .byIndex(
                                SYNC_INDEX,
                                syncKey(
                                        sync.getMetadata().getNamespace(),
                                        sync.getMetadata().getName()))
                        .stream()
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
                .build();
        return List.of(new InformerEventSource<>(jobs, context), new InformerEventSource<>(syncs, context));
    }

    private static String syncKey(String namespace, String name) {
        return namespace + "/" + name;
    }

    @Override
    public UpdateControl<RCloneSyncRun> reconcile(RCloneSyncRun run, Context<RCloneSyncRun> context) {
        if (run.getStatus() == null) {
            run.setStatus(new RCloneSyncRunStatus());
        }
        // The Secret was deleted when the run finished.
        if (!run.isUnfinished()) {
            return UpdateControl.noUpdate();
        }
        if (run.getStatus().getJobName() != null) {
            return follow(run, context);
        }
        // A Job whose creation wasn't recorded, e.g. because the status patch was lost: take it over.
        var existing = existingJob(run, context);
        if (existing.isPresent()) {
            var job = existing.get();
            var annotations = job.getMetadata().getAnnotations();
            recordJob(
                    run,
                    job.getMetadata().getNamespace(),
                    Optional.ofNullable(annotations.get(SYNC_GENERATION_ANNOTATION))
                            .map(Long::valueOf)
                            .orElse(null),
                    Instant.parse(job.getMetadata().getCreationTimestamp()));
            var control = follow(run, context);
            return control.isPatchStatus() ? control : UpdateControl.patchStatus(run);
        }
        return start(run, context);
    }

    private static String jobName(RCloneSyncRun run) {
        return NAME_PREFIX + run.getMetadata().getUid();
    }

    /** The run's Job, in either of the namespaces it can be in. */
    private Optional<Job> existingJob(RCloneSyncRun run, Context<RCloneSyncRun> context) {
        return Stream.of(run.getMetadata().getNamespace(), config.operatorNamespace())
                .flatMap(namespace -> context.getSecondaryResource(Job.class, null, jobName(run), namespace).stream())
                .findFirst();
    }

    private static void recordJob(RCloneSyncRun run, String jobNamespace, Long syncGeneration, Instant startTime) {
        var status = run.getStatus();
        status.setJobNamespace(jobNamespace);
        status.setJobName(jobName(run));
        status.setSyncGeneration(syncGeneration);
        status.setStartTime(startTime);
        Conditions.set(
                status.getConditions(),
                condition(
                        run,
                        ConditionStatus.UNKNOWN,
                        RCloneSyncRunStatus.REASON_RUNNING,
                        "Job " + jobNamespace + "/" + jobName(run) + " is running the sync"));
    }

    /** Starts the Job once the sync is Ready, or waits. */
    private UpdateControl<RCloneSyncRun> start(RCloneSyncRun run, Context<RCloneSyncRun> context) {
        var syncName = run.getSpec().getSyncRef().getName();
        var sync = context.getSecondaryResource(RCloneSync.class, null, syncName);
        if (sync.isEmpty()) {
            return succeeded(
                    run,
                    ConditionStatus.UNKNOWN,
                    RCloneSyncRunStatus.REASON_SYNC_NOT_FOUND,
                    "RCloneSync " + syncName + " does not exist");
        }
        if (!isReady(sync.get())) {
            return succeeded(
                    run,
                    ConditionStatus.UNKNOWN,
                    RCloneSyncRunStatus.REASON_SYNC_NOT_READY,
                    "RCloneSync " + syncName + " is not Ready");
        }

        var spec = sync.get().getSpec();
        var namespace = run.getMetadata().getNamespace();
        var resolver = new Resolver();
        String rcloneConf;
        try {
            var source = resolver.resolve(spec.getSource().getRemoteRef(), namespace, 0);
            var destination = resolver.resolve(spec.getDestination().getRemoteRef(), namespace, 0);
            rcloneConf = RCloneConfig.render(source, destination, Obscure.withRandomIvs());
        } catch (UnresolvableException e) {
            // The sync was Ready a moment ago; this resolves itself, or the sync stops being Ready.
            var control =
                    succeeded(run, ConditionStatus.UNKNOWN, RCloneSyncRunStatus.REASON_SYNC_NOT_READY, e.getMessage());
            return control.rescheduleAfter(config.secretRecheckInterval());
        } catch (RCloneConfig.InvalidValueException e) {
            return succeeded(
                    run, ConditionStatus.FALSE, RCloneSyncRunStatus.REASON_INVALID_CREDENTIALS, e.getMessage());
        }

        var jobNamespace = resolver.usesClusterRemote ? config.operatorNamespace() : namespace;
        var syncGeneration = sync.get().getMetadata().getGeneration();
        createIfAbsent(secret(run, jobNamespace, rcloneConf));
        createIfAbsent(job(run, jobNamespace, syncGeneration, SyncCommand.args(spec)));
        recordJob(run, jobNamespace, syncGeneration, Instant.now());
        return UpdateControl.patchStatus(run);
    }

    /** Ready, and checked for its current spec: a Ready left over from before a spec change doesn't count. */
    private static boolean isReady(RCloneSync sync) {
        var status = sync.getStatus();
        return status != null
                && status.getConditions().stream()
                        .anyMatch(c -> RCloneSyncStatus.READY.equals(c.getType())
                                && ConditionStatus.TRUE.equals(c.getStatus())
                                && Objects.equals(
                                        c.getObservedGeneration(),
                                        sync.getMetadata().getGeneration()));
    }

    /** Reports the outcome of the Job once it finishes. */
    private UpdateControl<RCloneSyncRun> follow(RCloneSyncRun run, Context<RCloneSyncRun> context) {
        var status = run.getStatus();
        var job = context.getSecondaryResource(Job.class, null, status.getJobName(), status.getJobNamespace())
                // The cache may not have seen a Job created moments ago.
                .or(() -> Optional.ofNullable(client.batch()
                        .v1()
                        .jobs()
                        .inNamespace(status.getJobNamespace())
                        .withName(status.getJobName())
                        .get()));
        if (job.isEmpty()) {
            deleteSecret(status);
            return succeeded(
                    run,
                    ConditionStatus.FALSE,
                    RCloneSyncRunStatus.REASON_JOB_NOT_FOUND,
                    "Job " + status.getJobNamespace() + "/" + status.getJobName() + " was deleted before it finished");
        }
        var jobStatus = job.get().getStatus();
        var conditions = jobStatus == null || jobStatus.getConditions() == null
                ? List.<JobCondition>of()
                : jobStatus.getConditions();
        var complete = trueCondition(conditions, "Complete");
        if (complete.isPresent()) {
            deleteSecret(status);
            status.setCompletionTime(
                    jobStatus.getCompletionTime() == null
                            ? Instant.now()
                            : Instant.parse(jobStatus.getCompletionTime()));
            return succeeded(run, ConditionStatus.TRUE, RCloneSyncRunStatus.REASON_COMPLETED, "the sync completed");
        }
        var failed = trueCondition(conditions, "Failed");
        if (failed.isPresent()) {
            deleteSecret(status);
            var failedAt = failed.get().getLastTransitionTime();
            status.setCompletionTime(failedAt == null ? Instant.now() : Instant.parse(failedAt));
            return succeeded(
                    run,
                    ConditionStatus.FALSE,
                    RCloneSyncRunStatus.REASON_FAILED,
                    Optional.ofNullable(failed.get().getMessage()).orElse("the Job failed"));
        }
        return UpdateControl.noUpdate();
    }

    private static Optional<JobCondition> trueCondition(List<JobCondition> conditions, String type) {
        return conditions.stream()
                .filter(c -> type.equals(c.getType()) && ConditionStatus.TRUE.equals(c.getStatus()))
                .findFirst();
    }

    /** Sets the Succeeded condition, and patches status if anything changed. */
    private static UpdateControl<RCloneSyncRun> succeeded(
            RCloneSyncRun run, String conditionStatus, String reason, String message) {
        var changed = Conditions.set(run.getStatus().getConditions(), condition(run, conditionStatus, reason, message));
        return changed ? UpdateControl.patchStatus(run) : UpdateControl.noUpdate();
    }

    private static Condition condition(RCloneSyncRun run, String conditionStatus, String reason, String message) {
        return new ConditionBuilder()
                .withType(RCloneSyncRunStatus.SUCCEEDED)
                .withStatus(conditionStatus)
                .withReason(reason)
                .withMessage(message)
                .withObservedGeneration(run.getMetadata().getGeneration())
                .build();
    }

    @Override
    public DeleteControl cleanup(RCloneSyncRun run, Context<RCloneSyncRun> context) {
        // In the run's own namespace, the run owns the Job and Secret. In the operator namespace they are deleted by
        // name, whatever status says: the status patch recording them may have been lost.
        var namespace = config.operatorNamespace();
        client.batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withName(jobName(run))
                .withPropagationPolicy(DeletionPropagation.BACKGROUND)
                .delete();
        client.secrets().inNamespace(namespace).withName(jobName(run)).delete();
        return DeleteControl.defaultDelete();
    }

    private void deleteSecret(RCloneSyncRunStatus status) {
        if (status.getJobName() != null) {
            client.secrets()
                    .inNamespace(status.getJobNamespace())
                    .withName(status.getJobName())
                    .delete();
        }
    }

    private void createIfAbsent(HasMetadata resource) {
        try {
            client.resource(resource).create();
        } catch (KubernetesClientException e) {
            // Created by an earlier reconcile that didn't get to record it.
            if (e.getCode() != HttpURLConnection.HTTP_CONFLICT) {
                throw e;
            }
        }
    }

    /** Owned by the run in its namespace; elsewhere only annotated with it. */
    private static ObjectMetaBuilder metadata(RCloneSyncRun run, String namespace) {
        var metadata = new ObjectMetaBuilder()
                .withName(jobName(run))
                .withNamespace(namespace)
                .withLabels(Map.of(MANAGED_BY_LABEL, MANAGED_BY))
                .withAnnotations(Map.of(
                        RUN_NAMESPACE_ANNOTATION,
                        run.getMetadata().getNamespace(),
                        RUN_NAME_ANNOTATION,
                        run.getMetadata().getName()));
        if (namespace.equals(run.getMetadata().getNamespace())) {
            metadata.withOwnerReferences(new OwnerReferenceBuilder()
                    .withApiVersion(HasMetadata.getApiVersion(RCloneSyncRun.class))
                    .withKind(HasMetadata.getKind(RCloneSyncRun.class))
                    .withName(run.getMetadata().getName())
                    .withUid(run.getMetadata().getUid())
                    .withController(true)
                    .withBlockOwnerDeletion(true)
                    .build());
        }
        return metadata;
    }

    private static Secret secret(RCloneSyncRun run, String namespace, String rcloneConf) {
        return new SecretBuilder()
                .withMetadata(metadata(run, namespace).build())
                .withImmutable(true)
                .withStringData(Map.of(SyncCommand.CONFIG_FILE, rcloneConf))
                .build();
    }

    /** One attempt, as a non-root user on a read-only root filesystem, without access to the API server. */
    private Job job(RCloneSyncRun run, String namespace, long syncGeneration, List<String> args) {
        long user = 65532;
        var name = jobName(run);
        return new JobBuilder()
                .withMetadata(metadata(run, namespace)
                        .addToAnnotations(SYNC_GENERATION_ANNOTATION, Long.toString(syncGeneration))
                        .build())
                .withNewSpec()
                .withBackoffLimit(0)
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(Map.of(MANAGED_BY_LABEL, MANAGED_BY))
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withAutomountServiceAccountToken(false)
                .withNewSecurityContext()
                .withRunAsNonRoot(true)
                .withRunAsUser(user)
                .withRunAsGroup(user)
                .withFsGroup(user)
                .withNewSeccompProfile()
                .withType("RuntimeDefault")
                .endSeccompProfile()
                .endSecurityContext()
                .addNewContainer()
                .withName("rclone")
                .withImage(config.rcloneImage())
                .withArgs(args)
                .addNewVolumeMount()
                .withName("config")
                .withMountPath(SyncCommand.CONFIG_DIR)
                .withReadOnly(true)
                .endVolumeMount()
                .addNewVolumeMount()
                .withName("tmp")
                .withMountPath("/tmp")
                .endVolumeMount()
                .withNewSecurityContext()
                .withAllowPrivilegeEscalation(false)
                .withReadOnlyRootFilesystem(true)
                .withNewCapabilities()
                .withDrop("ALL")
                .endCapabilities()
                .endSecurityContext()
                .endContainer()
                .addNewVolume()
                .withName("config")
                .withNewSecret()
                .withSecretName(name)
                .withDefaultMode(0440)
                .endSecret()
                .endVolume()
                .addNewVolume()
                .withName("tmp")
                .withNewEmptyDir()
                .endEmptyDir()
                .endVolume()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    /** A remote or Secret that doesn't exist (any more). */
    private static final class UnresolvableException extends Exception {
        UnresolvableException(String message) {
            super(message);
        }
    }

    /** Reads the remotes of a sync, the remotes crypt remotes wrap, and their Secret values. */
    private final class Resolver {

        private final Map<String, Optional<Secret>> secrets = new HashMap<>();
        boolean usesClusterRemote;

        /** {@code namespace} is where a namespaced remote is looked up: the namespace of what refers to it. */
        ResolvedRemote resolve(RemoteRef ref, String namespace, int depth) throws UnresolvableException {
            if (depth >= MAX_DEPTH) {
                throw new UnresolvableException("crypt remotes are nested more than " + MAX_DEPTH + " deep");
            }
            CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> remote;
            String secretNamespace;
            if (ref.getKind() == RemoteRef.Kind.CLUSTER_REMOTE) {
                usesClusterRemote = true;
                remote = client.resources(RCloneClusterRemote.class)
                        .withName(ref.getName())
                        .get();
                secretNamespace = config.operatorNamespace();
            } else {
                remote = client.resources(RCloneRemote.class)
                        .inNamespace(namespace)
                        .withName(ref.getName())
                        .get();
                secretNamespace = namespace;
            }
            if (remote == null) {
                throw new UnresolvableException(ref.getKind().name() + " " + ref.getName() + " does not exist");
            }

            var spec = remote.getSpec();
            var values = new HashMap<String, String>();
            for (var entry : spec.secretKeyRefs().entrySet()) {
                values.put(
                        entry.getKey(),
                        secretValue(
                                secretNamespace,
                                entry.getValue().getName(),
                                entry.getValue().getKey()));
            }
            ResolvedRemote wrapped = null;
            if (spec.getType() == BackendType.CRYPT) {
                wrapped = resolve(spec.getCrypt().getRemoteRef(), namespace, depth + 1);
            }
            return new ResolvedRemote(spec, values, wrapped);
        }

        private String secretValue(String namespace, String name, String key) throws UnresolvableException {
            var secret = secrets.computeIfAbsent(
                    namespace + "/" + name,
                    k -> Optional.ofNullable(client.secrets()
                            .inNamespace(namespace)
                            .withName(name)
                            .get()));
            if (secret.isEmpty()) {
                throw new UnresolvableException("Secret " + name + " does not exist");
            }
            var data = secret.get().getData();
            if (data == null || !data.containsKey(key)) {
                throw new UnresolvableException("Secret " + name + " has no key " + key);
            }
            return new String(Base64.getDecoder().decode(data.get(key)), StandardCharsets.UTF_8);
        }
    }
}
