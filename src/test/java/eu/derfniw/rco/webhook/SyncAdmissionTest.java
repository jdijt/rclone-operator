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

import static eu.derfniw.rco.testsupport.Syncs.sync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.FilterRule;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.SyncOptions;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Admission of RCloneSyncs by a real API server with the operator's webhooks registered: CRD schema validation, CEL
 * rules, and the webhook for rules neither can express.
 */
@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class SyncAdmissionTest {

    private static final String DENIED = "admission webhook \"vrclonesync.rco.frozenbits.se\" denied the request";

    @Inject
    KubernetesClient client;

    static Stream<Arguments> acceptedCases() {
        return Stream.of(
                // The other cases change one thing of the default, so it must be valid by itself.
                argumentSet("default, a cron trigger", sync(s -> {})),
                argumentSet(
                        "remoteRef kind defaults to RCloneRemote",
                        sync(s -> s.getDestination().getRemoteRef().setKind(null))),
                argumentSet("endpoints with paths", sync(s -> {
                    s.getSource().setPath("bucket/photos");
                    s.getDestination().setPath("/backup/photos");
                })),
                argumentSet(
                        "cron with ranges, steps and lists",
                        sync(s -> s.getTrigger().getCron().setExpression("*/15 1-5 * 1-3 1,5"))),
                argumentSet("cron macro", sync(s -> s.getTrigger().getCron().setExpression("@weekly"))),
                argumentSet("every option", sync(s -> s.setOptions(allOptions()))),
                argumentSet("longest name", named("s".repeat(242))),
                argumentSet("no run history", sync(s -> {
                    s.setSuccessfulRunsHistoryLimit(0);
                    s.setFailedRunsHistoryLimit(0);
                })));
    }

    @ParameterizedTest
    @MethodSource("acceptedCases")
    void createAccepted(HasMetadata resource) {
        placeInTestNamespace(resource);
        assertThat(client.resource(resource).create()).isNotNull();
    }

    static Stream<Arguments> rejectedCases() {
        return Stream.of(
                argumentSet("CEL rejects missing spec", withoutSpec(), "spec is required"),

                // CRD structural (OpenAPI) validation: one row per kind of constraint.
                argumentSet(
                        "required rejects missing source", sync(s -> s.setSource(null)), "spec.source: Required value"),
                argumentSet(
                        "required rejects endpoint without remoteRef",
                        sync(s -> s.getDestination().setRemoteRef(null)),
                        "spec.destination.remoteRef: Required value"),
                argumentSet(
                        "required rejects missing trigger",
                        sync(s -> s.setTrigger(null)),
                        "spec.trigger: Required value"),
                argumentSet(
                        "enum rejects unknown trigger type",
                        genericSync(Map.of("type", "manual")),
                        "Unsupported value: \"manual\""),
                argumentSet(
                        "enum rejects removed interval trigger type",
                        genericSync(Map.of("type", "interval", "interval", Map.of("every", "daily"))),
                        "Unsupported value: \"interval\""),
                argumentSet(
                        "minLength rejects empty cron expression",
                        sync(s -> s.getTrigger().getCron().setExpression("")),
                        "should be at least 1 chars long"),
                argumentSet(
                        "minimum rejects zero transfers",
                        sync(s -> {
                            var options = new SyncOptions();
                            options.setTransfers(0);
                            s.setOptions(options);
                        }),
                        "should be greater than or equal to 1"),
                argumentSet(
                        "minimum rejects zero starting deadline",
                        sync(s -> s.setStartingDeadlineSeconds(0L)),
                        "should be greater than or equal to 1"),
                argumentSet(
                        "minimum rejects negative history limit",
                        sync(s -> s.setFailedRunsHistoryLimit(-1)),
                        "should be greater than or equal to 0"),
                argumentSet(
                        "required rejects filter without pattern",
                        sync(s -> {
                            var options = new SyncOptions();
                            options.setFilters(List.of(new FilterRule(FilterRule.Action.EXCLUDE, null)));
                            s.setOptions(options);
                        }),
                        "spec.options.filters[0].pattern: Required value"),

                // CEL: run names (<sync>-<minutes since the epoch>) must fit in 253 characters.
                argumentSet(
                        "CEL rejects a name too long for its run names",
                        named("s".repeat(243)),
                        "metadata.name must be at most 242 characters"),

                // CEL: type <=> matching trigger set.
                argumentSet(
                        "CEL rejects type cron without cron",
                        sync(s -> s.getTrigger().setCron(null)),
                        "cron must be set if and only if type is cron"),

                // CEL: cron expression shape, numeric fields only.
                argumentSet(
                        "CEL rejects four fields",
                        sync(s -> s.getTrigger().getCron().setExpression("0 3 * *")),
                        "must be five space-separated fields"),
                argumentSet(
                        "CEL rejects six fields",
                        sync(s -> s.getTrigger().getCron().setExpression("0 0 3 * * *")),
                        "must be five space-separated fields"),
                argumentSet(
                        "CEL rejects names",
                        sync(s -> s.getTrigger().getCron().setExpression("0 3 * * MON-FRI")),
                        "must be five space-separated fields"),
                argumentSet(
                        "CEL rejects unknown macro",
                        sync(s -> s.getTrigger().getCron().setExpression("@every 1h")),
                        "must be five space-separated fields"),
                argumentSet(
                        "CEL rejects question mark",
                        sync(s -> s.getTrigger().getCron().setExpression("0 3 ? * *")),
                        "must be five space-separated fields"),

                // Webhook: checks that pass the CRD schema and CEL rules.
                argumentSet(
                        "webhook rejects cron value out of range",
                        sync(s -> s.getTrigger().getCron().setExpression("61 * * * *")),
                        "spec.trigger.cron.expression: Invalid value: \"61 * * * *\": must be a valid cron schedule"));
    }

    @ParameterizedTest
    @MethodSource("rejectedCases")
    void createRejected(HasMetadata resource, String wantError) {
        placeInTestNamespace(resource);
        assertThatThrownBy(() -> client.resource(resource).create())
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining(wantError);
    }

    /** Makes a valid sync invalid for the webhook only (CEL still passes), proving the webhook sees UPDATE. */
    @Test
    void webhookRejectsUpdate() {
        var resource = sync(s -> {});
        placeInTestNamespace(resource);
        var created = client.resource(resource).create();

        assertThatThrownBy(() -> client.resource(created).unlock().edit(r -> {
                    r.getSpec().getTrigger().getCron().setExpression("0 25 * * *");
                    return r;
                }))
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining(DENIED);
    }

    @Test
    void defaultsAreApplied() {
        var resource = sync(s -> s.setOptions(new SyncOptions()));
        placeInTestNamespace(resource);

        var created = client.resource(resource).create();

        assertThat(created.getSpec().getSuspend()).isFalse();
        assertThat(created.getSpec().getConcurrencyPolicy()).isEqualTo(RCloneSyncSpec.ConcurrencyPolicy.FORBID);
        assertThat(created.getSpec().getStartingDeadlineSeconds()).isEqualTo(3600L);
        assertThat(created.getSpec().getOptions().getDeleteMode()).isEqualTo(SyncOptions.DeleteMode.AFTER);
        assertThat(created.getSpec().getSource().getRemoteRef().getKind()).isEqualTo(RemoteRef.Kind.REMOTE);
        assertThat(created.getSpec().getSuccessfulRunsHistoryLimit()).isEqualTo(3);
        assertThat(created.getSpec().getFailedRunsHistoryLimit()).isEqualTo(1);
    }

    private static SyncOptions allOptions() {
        var options = new SyncOptions();
        options.setDryRun(true);
        options.setTransfers(2);
        options.setCheckers(4);
        options.setDeleteMode(SyncOptions.DeleteMode.DURING);
        options.setFilters(List.of(
                new FilterRule(FilterRule.Action.INCLUDE, "/photos/**"),
                new FilterRule(FilterRule.Action.EXCLUDE, "*")));
        return options;
    }

    private static RCloneSync named(String name) {
        var sync = sync(s -> {});
        sync.getMetadata().setName(name);
        return sync;
    }

    private static RCloneSync withoutSpec() {
        var sync = sync(s -> {});
        sync.setSpec(null);
        return sync;
    }

    /** A sync with a raw trigger, for values the typed model can't hold. */
    private static HasMetadata genericSync(Map<String, Object> trigger) {
        var spec = Map.of(
                "source", Map.of("remoteRef", Map.of("name", "source")),
                "destination", Map.of("remoteRef", Map.of("name", "destination")),
                "trigger", trigger);
        return new GenericKubernetesResourceBuilder()
                .withApiVersion(RCloneRemote.GROUP + "/" + RCloneRemote.VERSION)
                .withKind("RCloneSync")
                .withNewMetadata()
                .withName("sync")
                .endMetadata()
                .addToAdditionalProperties("spec", spec)
                .build();
    }

    /** Each object gets a fresh namespace, so rows don't collide on names. */
    private void placeInTestNamespace(HasMetadata resource) {
        var ns = client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("sync-test-")
                        .endMetadata()
                        .build())
                .create();
        resource.getMetadata().setNamespace(ns.getMetadata().getName());
    }
}
