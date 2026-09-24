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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The running operator against a real API server: the Ready condition is written, and follows spec changes. Runs
 * without the webhooks, so specs the webhook would reject can be stored.
 */
@QuarkusTest
@WithTestResource(
        value = KubeApiServerResource.class,
        initArgs = @ResourceArg(name = KubeApiServerResource.WEBHOOKS, value = "false"))
class RemoteReconcilerApiServerTest {

    private static final String VALID_TEMPLATE = "A valid template";
    private static final String INVALID_TEMPLATE = "With a ${field} that has no input";

    @Inject
    KubernetesClient client;

    enum Scope {
        NAMESPACED,
        CLUSTER
    }

    static Stream<Arguments> statusCases() {
        return Stream.of(Scope.values())
                .flatMap(scope -> Stream.of(
                        argumentSet(scope + " valid", scope, VALID_TEMPLATE, "True", RCloneRemoteStatus.REASON_VALID),
                        argumentSet(
                                scope + " invalid",
                                scope,
                                INVALID_TEMPLATE,
                                "False",
                                RCloneRemoteStatus.REASON_INVALID)));
    }

    @ParameterizedTest
    @MethodSource("statusCases")
    void statusReflectsValidation(Scope scope, String template, String wantStatus, String wantReason) {
        // The API server stores lastTransitionTime in whole seconds.
        var creation = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var created = client.resource(newRemote(scope, template)).create();

        var ready = awaitReady(created, c -> true);
        assertThat(ready.getStatus()).isEqualTo(wantStatus);
        assertThat(ready.getReason()).isEqualTo(wantReason);
        assertThat(ready.getObservedGeneration())
                .isEqualTo(created.getMetadata().getGeneration());
        assertThat(Instant.parse(ready.getLastTransitionTime())).isAfterOrEqualTo(creation);
    }

    // Changing the spec bumps the generation; the condition must follow it.
    @ParameterizedTest
    @EnumSource(Scope.class)
    void conditionFollowsSpecUpdate(Scope scope) {
        var created = client.resource(newRemote(scope, VALID_TEMPLATE)).create();
        assertThat(awaitReady(created, c -> true).getStatus()).isEqualTo("True");

        var current = client.resource(created).get();
        current.getSpec().getTemplate().setTemplate(INVALID_TEMPLATE);
        var updated = client.resource(current).update();
        // Guards the premise of this test: a spec change must bump the generation.
        assertThat(updated.getMetadata().getGeneration()).isEqualTo(2L);

        var ready = awaitReady(updated, c -> c.getObservedGeneration() == 2L);
        assertThat(ready.getStatus()).isEqualTo("False");
        assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_INVALID);
        assertThat(client.resource(updated).get().getStatus().getConditions()).hasSize(1);
    }

    private CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> newRemote(Scope scope, String template) {
        var spec = new RCloneRemoteSpec();
        spec.setType(BackendType.TEMPLATE);
        spec.setTemplate(new TemplateBackend(template, null));

        CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> remote;
        var metadata = new ObjectMetaBuilder();
        if (scope == Scope.NAMESPACED) {
            var ns = client.resource(new NamespaceBuilder()
                            .withNewMetadata()
                            .withGenerateName("rcloneremote-test-")
                            .endMetadata()
                            .build())
                    .create();
            remote = new RCloneRemote();
            metadata.withNamespace(ns.getMetadata().getName()).withName("test-rcloneremote");
        } else {
            // Cluster-scoped names are shared by all tests, so the name is generated.
            remote = new RCloneClusterRemote();
            metadata.withGenerateName("test-rcloneclusterremote-");
        }
        remote.setMetadata(metadata.build());
        remote.setSpec(spec);
        return remote;
    }

    /** Waits until the Ready condition exists and satisfies {@code done}, and returns it. */
    private Condition awaitReady(
            CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> resource, Predicate<Condition> done) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> {
                            var status = client.resource(resource).get().getStatus();
                            if (status == null) {
                                return null;
                            }
                            return status.getConditions().stream()
                                    .filter(c -> c.getType().equals(RCloneRemoteStatus.READY))
                                    .findFirst()
                                    .orElse(null);
                        },
                        c -> c != null && done.test(c));
    }
}
