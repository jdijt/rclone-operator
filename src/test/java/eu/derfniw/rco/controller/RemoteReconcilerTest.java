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

import static eu.derfniw.rco.testsupport.Remotes.namespaced;
import static eu.derfniw.rco.testsupport.Remotes.ref;
import static eu.derfniw.rco.testsupport.Remotes.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Reconcile logic on in-memory resources: what gets written to status and what the reconciler asks JOSDK to do. The
 * shared base class does the work, so the namespaced reconciler stands in for both kinds. These resources never reach
 * the API server; the reconciler only looks up the Secrets they refer to there.
 */
@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class RemoteReconcilerTest {

    private static final Duration INTERVAL = Duration.ofSeconds(10);

    @Inject
    RCloneRemoteReconciler reconciler;

    @Test
    void validRemoteBecomesReadyAndIsRevalidated() {
        var remote = remote("A valid template");

        var control = reconciler.reconcile(remote, null);

        assertThat(control.isPatchStatus()).isTrue();
        var ready = readyCondition(remote);
        assertThat(ready.getStatus()).isEqualTo("True");
        assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_VALID);
        assertThat(ready.getObservedGeneration()).isEqualTo(1L);
        assertThat(ready.getLastTransitionTime()).isNotBlank();
        // Left to the periodic re-check.
        assertThat(control.getScheduleDelay()).isEmpty();
    }

    @Test
    void invalidRemoteIsNotReadyAndNotRevalidated() {
        var remote = remote("With a ${field} that has no input");

        var control = reconciler.reconcile(remote, null);

        assertThat(control.isPatchStatus()).isTrue();
        assertThat(control.getScheduleDelay()).isEmpty();
        var ready = readyCondition(remote);
        assertThat(ready.getStatus()).isEqualTo("False");
        assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_INVALID);
        assertThat(ready.getMessage()).contains("spec.template.template", "undeclared field field");
    }

    // A second reconcile with an unchanged spec must not write status again.
    @Test
    void secondReconcileWithoutChangesDoesNotPatchStatus() {
        var remote = remote("A valid template");
        reconciler.reconcile(remote, null);
        var firstTransition = readyCondition(remote).getLastTransitionTime();

        var control = reconciler.reconcile(remote, null);

        assertThat(control.isNoUpdate()).isTrue();
        assertThat(remote.getStatus().getConditions()).hasSize(1);
        assertThat(readyCondition(remote).getLastTransitionTime()).isEqualTo(firstTransition);
    }

    // Changing the spec bumps the generation; the condition must follow it.
    @Test
    void specChangeUpdatesConditionAndGeneration() {
        var remote = remote("A valid template");
        reconciler.reconcile(remote, null);
        readyCondition(remote).setLastTransitionTime("2000-01-01T00:00:00Z");

        remote.getSpec().getTemplate().setTemplate("With a ${field} that has no input");
        remote.getMetadata().setGeneration(2L);
        var control = reconciler.reconcile(remote, null);

        assertThat(control.isPatchStatus()).isTrue();
        assertThat(remote.getStatus().getConditions()).hasSize(1);
        var ready = readyCondition(remote);
        assertThat(ready.getStatus()).isEqualTo("False");
        assertThat(ready.getObservedGeneration()).isEqualTo(2L);
        assertThat(ready.getLastTransitionTime()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    private static RCloneRemote remote(String template) {
        return remote(template(t -> t.setTemplate(template)));
    }

    private static RCloneRemote remote(RCloneRemoteSpec spec) {
        var remote = namespaced(spec);
        remote.getMetadata().setNamespace("default");
        remote.getMetadata().setGeneration(1L);
        return remote;
    }

    /**
     * Secrets aren't watched, so a remote waiting for one is re-checked at the configured interval, however long it
     * has been waiting. {@code waited} is {@code null} for a remote that has no Ready condition yet.
     */
    static Stream<Arguments> secretRetryCases() {
        return Stream.of(
                argumentSet("just became unready", (Duration) null),
                argumentSet("unready for an hour", Duration.ofHours(1)));
    }

    @ParameterizedTest
    @MethodSource("secretRetryCases")
    void missingSecretIsRechecked(Duration waited) {
        // The API server has no such Secret.
        var remote = remote(template(t -> {
            t.setTemplate("${password}");
            t.setInputs(Map.of("password", ref("missing-" + UUID.randomUUID(), "password")));
        }));
        if (waited != null) {
            var status = new RCloneRemoteStatus();
            status.getConditions()
                    .add(new ConditionBuilder()
                            .withType(RCloneRemoteStatus.READY)
                            .withStatus("False")
                            .withReason(RCloneRemoteStatus.REASON_SECRET_NOT_FOUND)
                            .withLastTransitionTime(Instant.now()
                                    .minus(waited)
                                    .truncatedTo(ChronoUnit.SECONDS)
                                    .toString())
                            .build());
            remote.setStatus(status);
        }

        var control = reconciler.reconcile(remote, null);

        assertThat(readyCondition(remote).getReason()).isEqualTo(RCloneRemoteStatus.REASON_SECRET_NOT_FOUND);
        // Set for the test profile in application.properties; differs from the default, so this fails if ignored.
        assertThat(control.getScheduleDelay()).contains(Duration.ofSeconds(7).toMillis());
    }

    private static Condition readyCondition(RCloneRemote remote) {
        return remote.getStatus().getConditions().stream()
                .filter(c -> c.getType().equals(RCloneRemoteStatus.READY))
                .findFirst()
                .orElseThrow();
    }
}
