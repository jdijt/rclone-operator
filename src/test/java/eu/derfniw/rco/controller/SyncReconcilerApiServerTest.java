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
import static eu.derfniw.rco.testsupport.Remotes.template;
import static eu.derfniw.rco.testsupport.Syncs.run;
import static eu.derfniw.rco.testsupport.Syncs.sync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRunStatus;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec.ConcurrencyPolicy;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.SyncTrigger;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The sync controller against a real API server, with the remote controllers running: readiness follows the remotes,
 * and runs are created on schedule.
 */
@QuarkusTest
@WithTestResource(
        value = KubeApiServerResource.class,
        initArgs = @ResourceArg(name = KubeApiServerResource.WEBHOOKS, value = "false"))
class SyncReconcilerApiServerTest {

    /** Becomes Ready: a template without placeholders needs no Secrets. */
    private static final RCloneRemoteSpec READY_REMOTE = template(t -> {});
    /** Never becomes Ready: the placeholder has no input. */
    private static final RCloneRemoteSpec INVALID_REMOTE = template(t -> t.setTemplate("${missing}"));

    private static final String HOURLY = "0 * * * *";

    @Inject
    KubernetesClient client;

    @Test
    void readySyncWaitsForItsFirstTime() {
        var namespace = freshNamespace();
        createRemote(namespace, "source", READY_REMOTE);
        var destination = client.resource(cluster(READY_REMOTE)).create();
        var nextBefore = next0300();
        var sync = sync(s -> s.getDestination()
                .getRemoteRef()
                .setName(destination.getMetadata().getName()));
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();

        var ready = awaitReady(created, c -> "True".equals(c.getStatus()));
        assertThat(ready.getReason()).isEqualTo(RCloneSyncStatus.REASON_VALID);
        assertThat(ready.getObservedGeneration()).isEqualTo(1L);

        // A new sync doesn't run before its first scheduled time: the next 03:00 UTC, at whichever moment the
        // reconciler looked.
        var next = awaitStatus(created, s -> s.getNextScheduleTime() != null).getNextScheduleTime();
        assertThat(next).isIn(nextBefore, next0300());
        assertThat(runs(namespace)).isEmpty();
    }

    @Test
    void invalidSpecIsNotReady() {
        // Passes the CEL rules; only the validator (the webhook, which is off here) rejects it.
        var sync = localSync(s -> s.getTrigger().getCron().setExpression("61 * * * *"));
        var namespace = freshNamespace();
        createRemote(namespace, "source", READY_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();

        var ready = awaitReady(created, c -> true);
        assertThat(ready.getStatus()).isEqualTo("False");
        assertThat(ready.getReason()).isEqualTo(RCloneSyncStatus.REASON_INVALID);
        assertThat(ready.getMessage()).contains("spec.trigger.cron.expression");
        assertThat(client.resource(created).get().getStatus().getNextScheduleTime())
                .isNull();
    }

    /** The sync watches remotes: it becomes Ready once a missing remote is created and Ready. */
    @Test
    void missingRemoteIsReportedUntilCreated() {
        var namespace = freshNamespace();
        createRemote(namespace, "destination", READY_REMOTE);
        var sync = localSync(s -> {});
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();

        var notFound = awaitReady(created, c -> true);
        assertThat(notFound.getStatus()).isEqualTo("False");
        assertThat(notFound.getReason()).isEqualTo(RCloneSyncStatus.REASON_REMOTE_NOT_FOUND);
        assertThat(notFound.getMessage()).contains("spec.source.remoteRef", "\"source\"");
        assertThat(client.resource(created).get().getStatus().getNextScheduleTime())
                .isNull();

        createRemote(namespace, "source", READY_REMOTE);

        assertThat(awaitReady(created, c -> "True".equals(c.getStatus())).getReason())
                .isEqualTo(RCloneSyncStatus.REASON_VALID);
    }

    /** A remote that isn't Ready keeps the sync from being Ready; the sync reacts when the remote becomes Ready. */
    @Test
    void remoteMustBeReady() {
        var namespace = freshNamespace();
        createRemote(namespace, "source", INVALID_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        var sync = localSync(s -> {});
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();

        var notReady = awaitReady(created, c -> true);
        assertThat(notReady.getStatus()).isEqualTo("False");
        assertThat(notReady.getReason()).isEqualTo(RCloneSyncStatus.REASON_REMOTE_NOT_READY);
        assertThat(notReady.getMessage()).contains("spec.source.remoteRef", "\"source\"");

        client.resources(RCloneRemote.class)
                .inNamespace(namespace)
                .withName("source")
                .edit(r -> {
                    r.setSpec(READY_REMOTE);
                    return r;
                });

        assertThat(awaitReady(created, c -> "True".equals(c.getStatus())).getReason())
                .isEqualTo(RCloneSyncStatus.REASON_VALID);
    }

    /**
     * After two missed hourly times, one run is created, for the most recent; it is named after that time and owned
     * by the sync.
     */
    @Test
    void onlyTheMostRecentMissedTimeRuns() {
        var namespace = freshNamespace();
        var missed = missedTwoHourlyRuns(namespace, READY_REMOTE, s -> {});
        resume(missed.sync());

        var run = await().atMost(Duration.ofSeconds(30))
                .until(() -> runs(namespace), r -> !r.isEmpty())
                .getFirst();
        assertThat(run.getMetadata().getName()).isEqualTo(runName(missed.due()));
        assertThat(run.getSpec().getSyncRef().getName()).isEqualTo("sync");
        assertThat(run.getMetadata().getOwnerReferences()).singleElement().satisfies(owner -> {
            assertThat(owner.getKind()).isEqualTo("RCloneSync");
            assertThat(owner.getName()).isEqualTo("sync");
            assertThat(owner.getUid()).isEqualTo(missed.sync().getMetadata().getUid());
            assertThat(owner.getController()).isTrue();
        });

        var status = awaitStatus(missed.sync(), s -> missed.due().equals(s.getLastScheduleTime()));
        assertThat(status.getNextScheduleTime()).isEqualTo(missed.due().plus(Duration.ofHours(1)));
        assertThat(runs(namespace)).hasSize(1);
    }

    /** A suspended sync creates no run, even with one due, and has no next time. */
    @Test
    void suspendedSyncCreatesNoRuns() {
        var namespace = freshNamespace();
        var missed = missedTwoHourlyRuns(namespace, READY_REMOTE, s -> {});

        // A spec change that keeps it suspended, so there is a reconcile to wait for.
        client.resource(missed.sync()).unlock().edit(r -> {
            r.getSpec().setStartingDeadlineSeconds(7200L);
            return r;
        });

        awaitReady(missed.sync(), c -> c.getObservedGeneration() == 2L);
        assertThat(runs(namespace)).isEmpty();
        var status = client.resource(missed.sync()).get().getStatus();
        assertThat(status.getNextScheduleTime()).isNull();
        assertThat(status.getLastScheduleTime()).isEqualTo(missed.lastScheduled());
    }

    /** Suspending a sync that has a next time unsets it. */
    @Test
    void suspendingUnsetsTheNextTime() {
        var namespace = freshNamespace();
        createRemote(namespace, "source", READY_REMOTE);
        createRemote(namespace, "destination", READY_REMOTE);
        var sync = localSync(s -> {});
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();
        awaitStatus(created, s -> s.getNextScheduleTime() != null);

        client.resource(created).unlock().edit(r -> {
            r.getSpec().setSuspend(true);
            return r;
        });

        awaitReady(created, c -> c.getObservedGeneration() == 2L);
        assertThat(client.resource(created).get().getStatus().getNextScheduleTime())
                .isNull();
    }

    /** A sync that isn't Ready creates no run; once Ready, it catches up on the due one. */
    @Test
    void notReadySyncCatchesUpOnceReady() {
        var namespace = freshNamespace();
        var missed = missedTwoHourlyRuns(namespace, INVALID_REMOTE, s -> {});
        resume(missed.sync());

        var notReady = awaitReady(missed.sync(), c -> c.getObservedGeneration() == 2L);
        assertThat(notReady.getReason()).isEqualTo(RCloneSyncStatus.REASON_REMOTE_NOT_READY);
        assertThat(runs(namespace)).isEmpty();
        assertThat(client.resource(missed.sync()).get().getStatus().getNextScheduleTime())
                .isNull();

        client.resources(RCloneRemote.class)
                .inNamespace(namespace)
                .withName("source")
                .edit(r -> {
                    r.setSpec(READY_REMOTE);
                    return r;
                });

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(runs(namespace))
                .extracting(r -> r.getMetadata().getName())
                .containsExactly(runName(missed.due())));
    }

    /** Forbid: the due run waits until the unfinished one finishes, then starts. */
    @Test
    void forbidWaitsForTheUnfinishedRun() {
        var namespace = freshNamespace();
        var manual = createManualRun(namespace);
        var missed =
                missedTwoHourlyRuns(namespace, READY_REMOTE, s -> s.setConcurrencyPolicy(ConcurrencyPolicy.FORBID));
        resume(missed.sync());

        // Once the sync is reconciled after being resumed, it must have held back the due run.
        awaitReady(missed.sync(), c -> c.getObservedGeneration() == 2L);
        assertThat(runs(namespace)).extracting(r -> r.getMetadata().getName()).containsExactly("manual");
        assertThat(client.resource(missed.sync()).get().getStatus().getLastScheduleTime())
                .isEqualTo(missed.lastScheduled());

        finish(manual);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(runs(namespace))
                .extracting(r -> r.getMetadata().getName())
                .containsExactlyInAnyOrder("manual", runName(missed.due())));
    }

    /** Replace: the unfinished run is deleted and the due run starts. */
    @Test
    void replaceDeletesTheUnfinishedRun() {
        var namespace = freshNamespace();
        createManualRun(namespace);
        var missed =
                missedTwoHourlyRuns(namespace, READY_REMOTE, s -> s.setConcurrencyPolicy(ConcurrencyPolicy.REPLACE));
        resume(missed.sync());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(runs(namespace))
                .extracting(r -> r.getMetadata().getName())
                .containsExactly(runName(missed.due())));
    }

    /** Allow: the due run starts next to the unfinished one. */
    @Test
    void allowRunsNextToTheUnfinishedRun() {
        var namespace = freshNamespace();
        createManualRun(namespace);
        var missed = missedTwoHourlyRuns(namespace, READY_REMOTE, s -> s.setConcurrencyPolicy(ConcurrencyPolicy.ALLOW));
        resume(missed.sync());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(runs(namespace))
                .extracting(r -> r.getMetadata().getName())
                .containsExactlyInAnyOrder("manual", runName(missed.due())));
    }

    /**
     * A suspended hourly sync whose last run was scheduled two hours before {@code due}: resumed, it must run once, for
     * {@code due} (the current hour).
     */
    private record Missed(RCloneSync sync, Instant lastScheduled, Instant due) {}

    /**
     * Creates the remotes "source" (with {@code sourceSpec}) and "destination" (Ready), and a suspended hourly sync
     * "sync" in {@code namespace}, then sets its lastScheduleTime two hours back. Waits first if the hour is about to
     * turn, so the due time can't change during the test.
     */
    private Missed missedTwoHourlyRuns(String namespace, RCloneRemoteSpec sourceSpec, Consumer<RCloneSyncSpec> change) {
        var nextHour = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
        if (Instant.now().isAfter(nextHour.minus(Duration.ofSeconds(90)))) {
            await().atMost(Duration.ofSeconds(100)).until(() -> Instant.now().isAfter(nextHour));
        }
        var due = Instant.now().truncatedTo(ChronoUnit.HOURS);
        var lastScheduled = due.minus(Duration.ofHours(2));

        createRemote(namespace, "source", sourceSpec);
        createRemote(namespace, "destination", READY_REMOTE);
        var sync = localSync(s -> {
            s.setTrigger(SyncTrigger.cron(HOURLY));
            s.setSuspend(true);
            change.accept(s);
        });
        sync.getMetadata().setNamespace(namespace);
        var created = client.resource(sync).create();
        awaitReady(created, c -> true);

        client.resource(created).unlock().editStatus(r -> {
            r.getStatus().setLastScheduleTime(lastScheduled);
            return r;
        });
        return new Missed(created, lastScheduled, due);
    }

    /** Unsuspends the sync (generation 2). */
    private void resume(RCloneSync sync) {
        client.resource(sync).unlock().edit(r -> {
            r.getSpec().setSuspend(false);
            return r;
        });
    }

    /** A run of "sync" created by hand, not finished. */
    private RCloneSyncRun createManualRun(String namespace) {
        var run = run(r -> {});
        run.getMetadata().setName("manual");
        run.getMetadata().setNamespace(namespace);
        return client.resource(run).create();
    }

    /** Stands in for the run controller: marks the run as succeeded. */
    private void finish(RCloneSyncRun run) {
        var current = client.resource(run).get();
        current.getStatus()
                .setConditions(List.of(new ConditionBuilder()
                        .withType(RCloneSyncRunStatus.SUCCEEDED)
                        .withStatus("True")
                        .withReason("Completed")
                        .withLastTransitionTime(
                                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                        .build()));
        client.resource(current).updateStatus();
    }

    /** A sync "sync" from the RCloneRemote "source" to the RCloneRemote "destination". */
    private static RCloneSync localSync(Consumer<RCloneSyncSpec> change) {
        return sync(s -> {
            s.getDestination().setRemoteRef(new RemoteRef(RemoteRef.Kind.REMOTE, "destination"));
            change.accept(s);
        });
    }

    /** The next 03:00 UTC. */
    private static Instant next0300() {
        var today = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(3));
        return today.isAfter(Instant.now()) ? today : today.plus(Duration.ofDays(1));
    }

    private static String runName(Instant scheduled) {
        return "sync-" + scheduled.getEpochSecond() / 60;
    }

    private List<RCloneSyncRun> runs(String namespace) {
        return client.resources(RCloneSyncRun.class)
                .inNamespace(namespace)
                .list()
                .getItems();
    }

    private void createRemote(String namespace, String name, RCloneRemoteSpec spec) {
        var remote = namespaced(spec);
        remote.getMetadata().setName(name);
        remote.getMetadata().setNamespace(namespace);
        client.resource(remote).create();
    }

    private String freshNamespace() {
        return client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("rclonesync-test-")
                        .endMetadata()
                        .build())
                .create()
                .getMetadata()
                .getName();
    }

    /** Waits until the status satisfies {@code done}, and returns it. */
    private RCloneSyncStatus awaitStatus(HasMetadata sync, Predicate<RCloneSyncStatus> done) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> client.resources(RCloneSync.class)
                                .inNamespace(sync.getMetadata().getNamespace())
                                .withName(sync.getMetadata().getName())
                                .get()
                                .getStatus(),
                        s -> s != null && done.test(s));
    }

    /** Waits until the Ready condition exists and satisfies {@code done}, and returns it. */
    private Condition awaitReady(CustomResource<?, RCloneSyncStatus> sync, Predicate<Condition> done) {
        var status = awaitStatus(sync, s -> s.getConditions().stream()
                .anyMatch(c -> c.getType().equals(RCloneSyncStatus.READY) && done.test(c)));
        return status.getConditions().stream()
                .filter(c -> c.getType().equals(RCloneSyncStatus.READY))
                .findFirst()
                .orElseThrow();
    }
}
