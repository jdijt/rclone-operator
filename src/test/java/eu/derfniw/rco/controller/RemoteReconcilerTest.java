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

import static org.assertj.core.api.Assertions.assertThat;

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Reconcile logic on in-memory resources: what gets written to status and what the reconciler asks JOSDK to do. The
 * shared base class does the work, so the namespaced reconciler stands in for both kinds. The API server is only there
 * because the operator starts with the app; these resources never reach it.
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
        var spec = new RCloneRemoteSpec();
        spec.setType(BackendType.TEMPLATE);
        spec.setTemplate(new TemplateBackend(template, null));
        var remote = new RCloneRemote();
        remote.setMetadata(new ObjectMetaBuilder()
                .withNamespace("default")
                .withName("remote")
                .withGeneration(1L)
                .build());
        remote.setSpec(spec);
        return remote;
    }

    private static Condition readyCondition(RCloneRemote remote) {
        return remote.getStatus().getConditions().stream()
                .filter(c -> c.getType().equals(RCloneRemoteStatus.READY))
                .findFirst()
                .orElseThrow();
    }
}
