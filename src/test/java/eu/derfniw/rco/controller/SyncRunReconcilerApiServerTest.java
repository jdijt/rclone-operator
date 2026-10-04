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

import static eu.derfniw.rco.testsupport.Remotes.cluster;
import static eu.derfniw.rco.testsupport.Remotes.namespaced;
import static eu.derfniw.rco.testsupport.Remotes.sftp;
import static eu.derfniw.rco.testsupport.Remotes.template;
import static eu.derfniw.rco.testsupport.Syncs.run;
import static eu.derfniw.rco.testsupport.Syncs.sync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import eu.derfniw.rco.OperatorConfig;
import eu.derfniw.rco.api.v1alpha1.ConditionStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRunStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.run.SyncCommand;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The run controller against a real API server, with the remote and sync controllers running. No Job controller runs
 * there: tests stand in for it by writing the Job's status.
 */
@QuarkusTest
@WithTestResource(
        value = KubeApiServerResource.class,
        initArgs = @ResourceArg(name = KubeApiServerResource.WEBHOOKS, value = "false"))
class SyncRunReconcilerApiServerTest {

    /** Becomes Ready: a template without placeholders needs no Secrets. */
    private static final RCloneRemoteSpec READY_REMOTE = template(t -> {});
    /** Never becomes Ready: the placeholder has no input. */
    private static final RCloneRemoteSpec INVALID_REMOTE = template(t -> t.setTemplate("${missing}"));

    private static final String PASSWORD = "correct horse battery staple";

    @Inject
    KubernetesClient client;

    @Inject
    OperatorConfig config;

    /** A run waits while its sync doesn't exist, and starts once the sync is created and Ready. */
    @Test
    void waitsForTheSyncToExist() {
        var namespace = freshNamespace();
        createRemote(namespace, "source", READY_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        var run = createRun(namespace);

        var waiting = awaitSucceeded(run, c -> true);
        assertThat(waiting.getStatus()).isEqualTo(ConditionStatus.UNKNOWN);
        assertThat(waiting.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_SYNC_NOT_FOUND);
        assertThat(jobs(namespace)).isEmpty();

        createSync(namespace, s -> {});

        assertThat(awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()))
                        .getStatus())
                .isEqualTo(ConditionStatus.UNKNOWN);
    }

    /** A run waits while its sync isn't Ready, and starts once it is. */
    @Test
    void waitsForTheSyncToBeReady() {
        var namespace = freshNamespace();
        createRemote(namespace, "source", INVALID_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        createSync(namespace, s -> {});
        var run = createRun(namespace);

        var waiting = awaitSucceeded(run, c -> true);
        assertThat(waiting.getStatus()).isEqualTo(ConditionStatus.UNKNOWN);
        assertThat(waiting.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_SYNC_NOT_READY);
        assertThat(jobs(namespace)).isEmpty();

        client.resources(RCloneRemote.class)
                .inNamespace(namespace)
                .withName("source")
                .edit(r -> {
                    r.setSpec(READY_REMOTE);
                    return r;
                });

        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()));
    }

    /**
     * With only namespaced remotes, the Job and its Secret are in the run's namespace and owned by the run. The
     * credentials are only in the Secret, obscured.
     */
    @Test
    void startsAJobInTheRunNamespace() {
        var namespace = freshNamespace();
        createSecret(namespace, "sftp", Map.of("password", PASSWORD));
        createRemote(namespace, "source", sftp(s -> {}));
        createRemote(namespace, "destination", READY_REMOTE);
        var sync = createSync(namespace, s -> s.getSource().setPath("photos"));
        var run = createRun(namespace);

        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()));
        var status = client.resource(run).get().getStatus();
        var name = "rclone-run-" + run.getMetadata().getUid();
        assertThat(status.getJobNamespace()).isEqualTo(namespace);
        assertThat(status.getJobName()).isEqualTo(name);
        assertThat(status.getSyncGeneration()).isEqualTo(1L);
        assertThat(status.getStartTime()).isNotNull();

        var job =
                client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
        assertOwnedByRun(job.getMetadata().getOwnerReferences(), run);
        assertThat(job.getSpec().getBackoffLimit()).isZero();
        var pod = job.getSpec().getTemplate().getSpec();
        assertThat(pod.getRestartPolicy()).isEqualTo("Never");
        assertThat(pod.getContainers()).singleElement().satisfies(container -> {
            assertThat(container.getImage()).isEqualTo(config.rcloneImage());
            assertThat(container.getArgs()).isEqualTo(SyncCommand.args(sync.getSpec()));
            assertThat(container.getVolumeMounts())
                    .anySatisfy(mount -> assertThat(mount.getMountPath()).isEqualTo(SyncCommand.CONFIG_DIR));
        });
        assertThat(pod.getVolumes())
                .anySatisfy(
                        volume -> assertThat(volume.getSecret().getSecretName()).isEqualTo(name));
        assertThat(Serialization.asJson(job)).doesNotContain(PASSWORD);

        var secret = client.secrets().inNamespace(namespace).withName(name).get();
        assertOwnedByRun(secret.getMetadata().getOwnerReferences(), run);
        assertThat(rcloneConf(secret))
                .contains("[source]\ntype = sftp\n", "[destination]\n", "pass = ")
                .doesNotContain(PASSWORD);
    }

    /**
     * A cluster remote's credentials stay in the operator namespace, so the Job and Secret go there, annotated with the
     * run. They can't be owned by it across namespaces: deleting the run deletes them.
     */
    @Test
    void clusterRemoteRunsInTheOperatorNamespace() {
        var namespace = freshNamespace();
        operatorNamespace();
        createRemote(namespace, "source", READY_REMOTE);
        var destination = client.resource(cluster(READY_REMOTE)).create();
        createSync(namespace, s -> s.getDestination()
                .setRemoteRef(new RemoteRef(
                        RemoteRef.Kind.CLUSTER_REMOTE, destination.getMetadata().getName())));
        var run = createRun(namespace);

        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()));
        var status = client.resource(run).get().getStatus();
        assertThat(status.getJobNamespace()).isEqualTo(config.operatorNamespace());
        var job = client.batch()
                .v1()
                .jobs()
                .inNamespace(config.operatorNamespace())
                .withName(status.getJobName())
                .get();
        var secret = client.secrets()
                .inNamespace(config.operatorNamespace())
                .withName(status.getJobName())
                .get();
        for (var metadata : List.of(job.getMetadata(), secret.getMetadata())) {
            assertThat(metadata.getOwnerReferences()).isEmpty();
            assertThat(metadata.getAnnotations())
                    .containsEntry("rco.frozenbits.se/run-namespace", namespace)
                    .containsEntry("rco.frozenbits.se/run-name", "run");
        }

        client.resource(run).delete();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(client.resource(run).get()).isNull();
            assertThat(client.resource(job).get()).isNull();
            assertThat(client.resource(secret).get()).isNull();
        });
    }

    /** A completed Job succeeds the run; the credentials are deleted. */
    @Test
    void completedJobSucceedsTheRun() {
        var namespace = freshNamespace();
        var run = startedRun(namespace);
        var job = finishJob(run, true);

        var succeeded = awaitSucceeded(run, c -> !ConditionStatus.UNKNOWN.equals(c.getStatus()));
        assertThat(succeeded.getStatus()).isEqualTo(ConditionStatus.TRUE);
        assertThat(succeeded.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_COMPLETED);
        assertThat(client.resource(run).get().getStatus().getCompletionTime())
                .isEqualTo(Instant.parse(job.getStatus().getCompletionTime()));
        awaitSecretDeleted(run);
    }

    /** A failed Job fails the run; the credentials are deleted. */
    @Test
    void failedJobFailsTheRun() {
        var namespace = freshNamespace();
        var run = startedRun(namespace);
        var job = finishJob(run, false);

        var failed = awaitSucceeded(run, c -> !ConditionStatus.UNKNOWN.equals(c.getStatus()));
        assertThat(failed.getStatus()).isEqualTo(ConditionStatus.FALSE);
        assertThat(failed.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_FAILED);
        // When the Job failed, not when the operator noticed.
        assertThat(client.resource(run).get().getStatus().getCompletionTime())
                .isEqualTo(
                        Instant.parse(job.getStatus().getConditions().getLast().getLastTransitionTime()));
        awaitSecretDeleted(run);
    }

    /** A Job deleted before it finished fails the run. */
    @Test
    void deletedJobFailsTheRun() {
        var namespace = freshNamespace();
        var run = startedRun(namespace);
        client.batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withName(client.resource(run).get().getStatus().getJobName())
                .delete();

        var failed = awaitSucceeded(run, c -> !ConditionStatus.UNKNOWN.equals(c.getStatus()));
        assertThat(failed.getStatus()).isEqualTo(ConditionStatus.FALSE);
        assertThat(failed.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_JOB_NOT_FOUND);
    }

    /**
     * A Job the run's status doesn't record, e.g. because the status patch after creating it was lost, is taken over
     * rather than started again, whatever the state of the sync.
     */
    @Test
    void takesOverAJobItDidNotRecord() {
        var namespace = freshNamespace();
        var run = createRun(namespace);
        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_SYNC_NOT_FOUND.equals(c.getReason()));

        var name = "rclone-run-" + run.getMetadata().getUid();
        client.resource(new JobBuilder()
                        .withNewMetadata()
                        .withNamespace(namespace)
                        .withName(name)
                        .withLabels(Map.of("app.kubernetes.io/managed-by", "rclone-operator"))
                        .withAnnotations(Map.of(
                                "rco.frozenbits.se/run-namespace", namespace,
                                "rco.frozenbits.se/run-name", "run",
                                "rco.frozenbits.se/sync-generation", "3"))
                        .endMetadata()
                        .withNewSpec()
                        .withNewTemplate()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewContainer()
                        .withName("rclone")
                        .withImage(config.rcloneImage())
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build())
                .create();

        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()));
        var status = client.resource(run).get().getStatus();
        assertThat(status.getJobNamespace()).isEqualTo(namespace);
        assertThat(status.getJobName()).isEqualTo(name);
        assertThat(status.getSyncGeneration()).isEqualTo(3L);
        assertThat(status.getStartTime()).isNotNull();

        finishJob(run, true);
        assertThat(awaitSucceeded(run, c -> !ConditionStatus.UNKNOWN.equals(c.getStatus()))
                        .getReason())
                .isEqualTo(RCloneSyncRunStatus.REASON_COMPLETED);
    }

    /** A Secret value that would break the rclone configuration fails the run before a Job is created. */
    @Test
    void lineBreakInASecretFailsTheRun() {
        var namespace = freshNamespace();
        createSecret(namespace, "sftp", Map.of("password", "pw\ntype = local"));
        createRemote(namespace, "source", sftp(s -> {}));
        createRemote(namespace, "destination", READY_REMOTE);
        createSync(namespace, s -> {});
        var run = createRun(namespace);

        var failed = awaitSucceeded(run, c -> !ConditionStatus.UNKNOWN.equals(c.getStatus()));
        assertThat(failed.getStatus()).isEqualTo(ConditionStatus.FALSE);
        assertThat(failed.getReason()).isEqualTo(RCloneSyncRunStatus.REASON_INVALID_CREDENTIALS);
        assertThat(failed.getMessage()).contains("Secret sftp key password").doesNotContain("type = local");
        assertThat(jobs(namespace)).isEmpty();
    }

    /** A run of a sync between two Ready template remotes, waited for until its Job runs. */
    private RCloneSyncRun startedRun(String namespace) {
        createRemote(namespace, "source", READY_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        createSync(namespace, s -> {});
        var run = createRun(namespace);
        awaitSucceeded(run, c -> RCloneSyncRunStatus.REASON_RUNNING.equals(c.getReason()));
        return run;
    }

    /** Stands in for the Job controller: marks the run's Job as succeeded or failed. */
    private Job finishJob(RCloneSyncRun run, boolean succeeded) {
        var status = client.resource(run).get().getStatus();
        var job = client.batch()
                .v1()
                .jobs()
                .inNamespace(status.getJobNamespace())
                .withName(status.getJobName())
                .get();
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        job.getStatus().setStartTime(now);
        if (succeeded) {
            job.getStatus().setSucceeded(1);
            job.getStatus().setCompletionTime(now);
            job.getStatus()
                    .setConditions(List.of(
                            jobCondition("SuccessCriteriaMet", "CompletionsReached", now),
                            jobCondition("Complete", "CompletionsReached", now)));
        } else {
            job.getStatus().setFailed(1);
            job.getStatus()
                    .setConditions(List.of(
                            jobCondition("FailureTarget", "BackoffLimitExceeded", now),
                            jobCondition("Failed", "BackoffLimitExceeded", now)));
        }
        return client.resource(job).updateStatus();
    }

    private static JobCondition jobCondition(String type, String reason, String now) {
        return new JobConditionBuilder()
                .withType(type)
                .withStatus("True")
                .withReason(reason)
                .withMessage(reason)
                .withLastProbeTime(now)
                .withLastTransitionTime(now)
                .build();
    }

    private void awaitSecretDeleted(RCloneSyncRun run) {
        var status = client.resource(run).get().getStatus();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> client.secrets()
                                .inNamespace(status.getJobNamespace())
                                .withName(status.getJobName())
                                .get()
                        == null);
    }

    private static void assertOwnedByRun(List<OwnerReference> owners, RCloneSyncRun run) {
        assertThat(owners).singleElement().satisfies(owner -> {
            assertThat(owner.getKind()).isEqualTo("RCloneSyncRun");
            assertThat(owner.getUid()).isEqualTo(run.getMetadata().getUid());
            assertThat(owner.getController()).isTrue();
        });
    }

    private static String rcloneConf(Secret secret) {
        return new String(
                Base64.getDecoder().decode(secret.getData().get(SyncCommand.CONFIG_FILE)), StandardCharsets.UTF_8);
    }

    /** A sync "sync" from the RCloneRemote "source" to the RCloneRemote "destination". */
    private RCloneSync createSync(String namespace, Consumer<RCloneSyncSpec> change) {
        var sync = sync(s -> {
            s.getDestination().setRemoteRef(new RemoteRef(RemoteRef.Kind.REMOTE, "destination"));
            change.accept(s);
        });
        sync.getMetadata().setNamespace(namespace);
        return client.resource(sync).create();
    }

    /** A run "run" of "sync", as if created by hand. */
    private RCloneSyncRun createRun(String namespace) {
        var run = run(r -> {});
        run.getMetadata().setNamespace(namespace);
        return client.resource(run).create();
    }

    private void createRemote(String namespace, String name, RCloneRemoteSpec spec) {
        var remote = namespaced(spec);
        remote.getMetadata().setName(name);
        remote.getMetadata().setNamespace(namespace);
        client.resource(remote).create();
    }

    private void createSecret(String namespace, String name, Map<String, String> data) {
        client.resource(new SecretBuilder()
                        .withNewMetadata()
                        .withNamespace(namespace)
                        .withName(name)
                        .endMetadata()
                        .withStringData(data)
                        .build())
                .create();
    }

    private List<Job> jobs(String namespace) {
        return client.batch().v1().jobs().inNamespace(namespace).list().getItems();
    }

    private String freshNamespace() {
        return client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("rclonesyncrun-test-")
                        .endMetadata()
                        .build())
                .create()
                .getMetadata()
                .getName();
    }

    /** The operator namespace, created if needed. */
    private void operatorNamespace() {
        client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(config.operatorNamespace())
                        .endMetadata()
                        .build())
                .serverSideApply();
    }

    /** Waits until the Succeeded condition exists and satisfies {@code done}, and returns it. */
    private Condition awaitSucceeded(RCloneSyncRun run, Predicate<Condition> done) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> {
                            var current = client.resource(run).get();
                            return current.getStatus() == null
                                    ? null
                                    : current.getStatus().getConditions().stream()
                                            .filter(c -> c.getType().equals(RCloneSyncRunStatus.SUCCEEDED))
                                            .findFirst()
                                            .orElse(null);
                        },
                        c -> c != null && done.test(c));
    }
}
