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
package eu.derfniw.rco.webhook;

import static eu.derfniw.rco.testsupport.Syncs.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Admission of RCloneSyncRuns by a real API server: CRD schema validation and CEL rules. Runs have no webhook; their
 * spec only names a sync.
 */
@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class SyncRunAdmissionTest {

    @Inject
    KubernetesClient client;

    @Test
    void defaultAccepted() {
        var resource = run(r -> {});
        placeInTestNamespace(resource);
        assertThat(client.resource(resource).create()).isNotNull();
    }

    static Stream<Arguments> rejectedCases() {
        return Stream.of(
                argumentSet("CEL rejects missing spec", withoutSpec(), "spec is required"),
                argumentSet(
                        "required rejects missing syncRef",
                        run(r -> r.setSyncRef(null)),
                        "spec.syncRef: Required value"),
                argumentSet(
                        "minLength rejects empty sync name",
                        run(r -> r.getSyncRef().setName("")),
                        "should be at least 1 chars long"));
    }

    @ParameterizedTest
    @MethodSource("rejectedCases")
    void createRejected(RCloneSyncRun resource, String wantError) {
        placeInTestNamespace(resource);
        assertThatThrownBy(() -> client.resource(resource).create())
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining(wantError);
    }

    @Test
    void specIsImmutable() {
        var resource = run(r -> {});
        placeInTestNamespace(resource);
        var created = client.resource(resource).create();

        assertThatThrownBy(() -> client.resource(created).unlock().edit(r -> {
                    r.getSpec().getSyncRef().setName("other");
                    return r;
                }))
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining("spec is immutable");
    }

    /** Status is a subresource: the run controller writes it, which the immutable spec must not get in the way of. */
    @Test
    void statusCanBeUpdated() {
        var resource = run(r -> {});
        placeInTestNamespace(resource);
        var created = client.resource(resource).create();

        created.getStatus().setJobName("job");
        var updated = client.resource(created).updateStatus();

        assertThat(updated.getStatus().getJobName()).isEqualTo("job");
    }

    private static RCloneSyncRun withoutSpec() {
        var run = run(r -> {});
        run.setSpec(null);
        return run;
    }

    /** Each object gets a fresh namespace, so rows don't collide on names. */
    private void placeInTestNamespace(RCloneSyncRun resource) {
        var ns = client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("sync-run-test-")
                        .endMetadata()
                        .build())
                .create();
        resource.getMetadata().setNamespace(ns.getMetadata().getName());
    }
}
