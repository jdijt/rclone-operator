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
import static eu.derfniw.rco.testsupport.Remotes.crypt;
import static eu.derfniw.rco.testsupport.Remotes.namespaced;
import static eu.derfniw.rco.testsupport.Remotes.ref;
import static eu.derfniw.rco.testsupport.Remotes.s3;
import static eu.derfniw.rco.testsupport.Remotes.sftp;
import static eu.derfniw.rco.testsupport.Remotes.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.OperatorConfig;
import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.SecretKeyRef;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The running operator against a real API server: the Ready condition is written, and follows spec changes and the
 * referenced Secrets. Runs without the webhooks, so specs the webhook would reject can be stored.
 */
@QuarkusTest
@WithTestResource(
        value = KubeApiServerResource.class,
        initArgs = @ResourceArg(name = KubeApiServerResource.WEBHOOKS, value = "false"))
class RemoteReconcilerApiServerTest {

    private static final String VALID_TEMPLATE = "A valid template";
    private static final String INVALID_TEMPLATE = "With a ${field} that has no input";
    /** The Ready RCloneClusterRemote the crypt remotes of {@link #statusReflectsSecretRefs} wrap. */
    private static final String CRYPT_FIXTURE_WRAPPED = "crypt-fixture-wrapped";

    @Inject
    KubernetesClient client;

    @Inject
    OperatorConfig config;

    enum Scope {
        NAMESPACED,
        CLUSTER;

        CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> remote(RCloneRemoteSpec spec) {
            return this == NAMESPACED ? namespaced(spec) : cluster(spec);
        }

        /** The kind a remoteRef names a remote of this scope by. */
        RemoteRef.Kind kind() {
            return this == NAMESPACED ? RemoteRef.Kind.REMOTE : RemoteRef.Kind.CLUSTER_REMOTE;
        }
    }

    /** What exists of the Secret a remote refers to when the remote is created. */
    enum SecretState {
        /** No Secret of that name: every reference is unresolved. */
        SECRET_MISSING,
        /** The Secret exists without the key of the backend's first reference: only that reference is unresolved. */
        KEY_MISSING,
        /**
         * A Secret of that name with every key exists, but in the other scope's namespace: the operator namespace for
         * a namespaced remote, another namespace for a cluster remote. It must not be found.
         */
        OTHER_NAMESPACE,
        /** The Secret has every referenced key. */
        PRESENT
    }

    /** A spec and its Secret references: field path to Secret key, in declaration order. */
    private record SpecWithRefs(RCloneRemoteSpec spec, SequencedMap<String, String> keysByField) {}

    /**
     * Spec validation alone, per scope: a template remote without Secret references that passes or fails the
     * validator.
     */
    static Stream<Arguments> statusCases() {
        return Stream.of(Scope.values())
                .flatMap(
                        scope -> Stream.of(
                                argumentSet(
                                        scope + " valid",
                                        scope,
                                        VALID_TEMPLATE,
                                        "True",
                                        RCloneRemoteStatus.REASON_VALID,
                                        ""),
                                argumentSet(
                                        scope + " invalid",
                                        scope,
                                        INVALID_TEMPLATE,
                                        "False",
                                        RCloneRemoteStatus.REASON_INVALID,
                                        "spec.template.template: Invalid value: \"${field}\": reference to undeclared field field")));
    }

    @ParameterizedTest
    @MethodSource("statusCases")
    void statusReflectsValidation(
            Scope scope, String template, String wantStatus, String wantReason, String wantMessage) {
        // The API server stores lastTransitionTime in whole seconds.
        var creation = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var remote = scope.remote(template(t -> t.setTemplate(template)));
        placeInTestNamespace(remote);
        var created = client.resource(remote).create();

        var ready = awaitReady(created, c -> true);
        assertThat(ready.getStatus()).isEqualTo(wantStatus);
        assertThat(ready.getReason()).isEqualTo(wantReason);
        assertThat(ready.getMessage()).isEqualTo(wantMessage);
        assertThat(ready.getObservedGeneration())
                .isEqualTo(created.getMetadata().getGeneration());
        assertThat(Instant.parse(ready.getLastTransitionTime())).isAfterOrEqualTo(creation);
    }

    // Changing the spec bumps the generation; the condition must follow it.
    @ParameterizedTest
    @EnumSource(Scope.class)
    void conditionFollowsSpecUpdate(Scope scope) {
        var remote = scope.remote(template(t -> t.setTemplate(VALID_TEMPLATE)));
        placeInTestNamespace(remote);
        var created = client.resource(remote).create();
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

    /**
     * Secret resolution: every scope × backend × {@link SecretState}. Each spec passes validation and sets every Secret
     * reference of its backend, so only the Secret decides the outcome.
     */
    static Stream<Arguments> secretRefCases() {
        return Stream.of(Scope.values())
                .flatMap(scope -> Stream.of(BackendType.values()).flatMap(type -> Stream.of(SecretState.values())
                        .map(state -> argumentSet(scope + " " + type + " " + state, scope, type, state))));
    }

    // Every Secret reference of every backend must resolve to an existing Secret and key.
    @ParameterizedTest
    @MethodSource("secretRefCases")
    void statusReflectsSecretRefs(Scope scope, BackendType type, SecretState state) {
        // Random, as the operator namespace is shared by all tests.
        var secretName = "test-secret-" + UUID.randomUUID();
        var withRefs = specWithSecretRefs(type, secretName);
        var remote = scope.remote(withRefs.spec());
        var namespace = placeInTestNamespace(remote);
        var firstRef = withRefs.keysByField().firstEntry();

        // Created before the remote, so the outcome doesn't depend on watching Secrets.
        var keys = new ArrayList<>(withRefs.keysByField().values());
        switch (state) {
            case SECRET_MISSING -> {}
            case KEY_MISSING -> createSecret(namespace, secretName, keys.subList(1, keys.size()));
            case OTHER_NAMESPACE -> {
                var other = scope == Scope.NAMESPACED ? operatorNamespace() : freshNamespace();
                createSecret(other, secretName, keys);
            }
            case PRESENT -> createSecret(namespace, secretName, keys);
        }
        if (type == BackendType.CRYPT) {
            // Secrets are checked first, so only with every Secret present does the wrapped remote matter.
            ensureReadyClusterRemote(CRYPT_FIXTURE_WRAPPED);
        }
        var created = client.resource(remote).create();

        var ready = awaitReady(created, c -> true);
        switch (state) {
            case PRESENT -> {
                assertThat(ready.getStatus()).isEqualTo("True");
                assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_VALID);
            }
            case SECRET_MISSING, OTHER_NAMESPACE -> {
                assertThat(ready.getStatus()).isEqualTo("False");
                assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_SECRET_NOT_FOUND);
                assertThat(ready.getMessage())
                        .contains(secretName)
                        .contains(withRefs.keysByField().sequencedKeySet());
            }
            case KEY_MISSING -> {
                assertThat(ready.getStatus()).isEqualTo("False");
                assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_SECRET_NOT_FOUND);
                assertThat(ready.getMessage()).contains(firstRef.getKey(), firstRef.getValue());
                // One by one: doesNotContain rejects an empty list, and template has a single reference.
                withRefs.keysByField().sequencedKeySet().stream()
                        .skip(1)
                        .forEach(field -> assertThat(ready.getMessage()).doesNotContain(field));
            }
        }
    }

    /**
     * A crypt remote is only Ready while the remote it wraps is, and follows it: from missing, to not Ready, to Ready.
     * Namespaced crypt remotes may wrap either kind; cluster crypt remotes only cluster remotes.
     */
    static Stream<Arguments> wrappingCases() {
        return Stream.of(
                argumentSet("namespaced wraps namespaced", Scope.NAMESPACED, Scope.NAMESPACED),
                argumentSet("namespaced wraps cluster", Scope.NAMESPACED, Scope.CLUSTER),
                argumentSet("cluster wraps cluster", Scope.CLUSTER, Scope.CLUSTER));
    }

    @ParameterizedTest
    @MethodSource("wrappingCases")
    void cryptFollowsTheWrappedRemote(Scope cryptScope, Scope wrappedScope) {
        var secretName = "test-secret-" + UUID.randomUUID();
        var wrappedName = "wrapped-" + UUID.randomUUID();
        var crypt = cryptScope.remote(crypt(c -> {
            c.setRemoteRef(new RemoteRef(wrappedScope.kind(), wrappedName));
            c.setPasswordRef(ref(secretName, "password"));
        }));
        createSecret(placeInTestNamespace(crypt), secretName, List.of("password"));
        var created = client.resource(crypt).create();

        var notFound = awaitReady(created, c -> c.getMessage().contains("cannot find"));
        assertThat(notFound.getStatus()).isEqualTo("False");
        assertThat(notFound.getReason()).isEqualTo(RCloneRemoteStatus.REASON_REMOTE_NOT_READY);
        assertThat(notFound.getMessage()).contains("spec.crypt.remoteRef", wrappedName);

        var wrapped = wrappedScope.remote(template(t -> t.setTemplate(INVALID_TEMPLATE)));
        wrapped.getMetadata().setName(wrappedName);
        if (wrappedScope == Scope.NAMESPACED) {
            wrapped.getMetadata().setNamespace(crypt.getMetadata().getNamespace());
        }
        client.resource(wrapped).create();

        var notReady = awaitReady(created, c -> c.getMessage().contains("not in ready state"));
        assertThat(notReady.getStatus()).isEqualTo("False");
        assertThat(notReady.getMessage()).contains("spec.crypt.remoteRef", wrappedName);

        client.resource(wrapped).unlock().edit(r -> {
            r.getSpec().getTemplate().setTemplate(VALID_TEMPLATE);
            return r;
        });

        assertThat(awaitReady(created, c -> "True".equals(c.getStatus())).getReason())
                .isEqualTo(RCloneRemoteStatus.REASON_VALID);
    }

    /** Crypt remotes that wrap each other can never be Ready, and say why. */
    @ParameterizedTest
    @EnumSource(Scope.class)
    void cryptCycleIsNotReady(Scope scope) {
        var secretName = "test-secret-" + UUID.randomUUID();
        var first = "crypt-a-" + UUID.randomUUID();
        var second = "crypt-b-" + UUID.randomUUID();
        var a = cryptWrapping(scope, first, second, secretName);
        var b = cryptWrapping(scope, second, first, secretName);
        var namespace = placeInTestNamespace(a);
        if (scope == Scope.NAMESPACED) {
            b.getMetadata().setNamespace(namespace);
        }
        createSecret(namespace, secretName, List.of("password"));
        var createdA = client.resource(a).create();
        var createdB = client.resource(b).create();

        for (var created : List.of(createdA, createdB)) {
            var ready = awaitReady(created, c -> c.getMessage().contains("not in ready state"));
            assertThat(ready.getStatus()).isEqualTo("False");
            assertThat(ready.getReason()).isEqualTo(RCloneRemoteStatus.REASON_REMOTE_NOT_READY);
        }
    }

    /** A crypt remote {@code name} of {@code scope} wrapping the remote {@code wrapped} of the same scope. */
    private static CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> cryptWrapping(
            Scope scope, String name, String wrapped, String secretName) {
        var remote = scope.remote(crypt(c -> {
            c.setRemoteRef(new RemoteRef(scope.kind(), wrapped));
            c.setPasswordRef(ref(secretName, "password"));
        }));
        remote.getMetadata().setGenerateName(null);
        remote.getMetadata().setName(name);
        return remote;
    }

    /**
     * Secrets aren't watched: a remote waiting for one re-checks at the configured interval (7s in tests, instead of
     * the hourly re-check), so it becomes Ready soon after the Secret is created.
     */
    @ParameterizedTest
    @EnumSource(Scope.class)
    void missingSecretIsPickedUpOnceCreated(Scope scope) {
        var secretName = "test-secret-" + UUID.randomUUID();
        var remote = scope.remote(template(t -> {
            t.setTemplate("${password}");
            t.setInputs(Map.of("password", ref(secretName, "password")));
        }));
        var namespace = placeInTestNamespace(remote);
        var created = client.resource(remote).create();
        assertThat(awaitReady(created, c -> true).getReason()).isEqualTo(RCloneRemoteStatus.REASON_SECRET_NOT_FOUND);

        createSecret(namespace, secretName, List.of("password"));

        assertThat(awaitReady(created, c -> "True".equals(c.getStatus())).getReason())
                .isEqualTo(RCloneRemoteStatus.REASON_VALID);
    }

    /**
     * Guards the fixture of {@link #statusReflectsSecretRefs}: it must set every Secret reference of the backend (so a
     * reference added to a backend is added there too), and its keys must be distinct and never part of a field path
     * (or asserting that the message names a key would pass whenever it names the field).
     */
    @ParameterizedTest
    @EnumSource(BackendType.class)
    void secretRefFixtureSetsEveryReference(BackendType type) throws IllegalAccessException {
        var withRefs = specWithSecretRefs(type, "secret");
        var backend = withRefs.spec().selectedBackend();

        int refs = 0;
        for (var field : backend.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() == SecretKeyRef.class) {
                assertThat(field.get(backend)).as(field.getName()).isNotNull();
                refs++;
            } else if (field.getGenericType() instanceof ParameterizedType map
                    && map.getRawType() == Map.class
                    && map.getActualTypeArguments()[1] == SecretKeyRef.class) {
                var inputs = (Map<?, ?>) field.get(backend);
                assertThat(inputs).as(field.getName()).isNotEmpty();
                refs += inputs.size();
            }
        }
        assertThat(withRefs.keysByField()).hasSize(refs);

        assertThat(withRefs.keysByField().values()).doesNotHaveDuplicates();
        for (var key : withRefs.keysByField().values()) {
            assertThat(withRefs.keysByField().sequencedKeySet()).noneMatch(field -> field.contains(key));
        }
    }

    /**
     * A spec for {@code type} with every Secret reference of its backend set, each to its own key of the Secret
     * {@code secretName}. Keys are opaque, so a message only names one by reporting it.
     */
    private static SpecWithRefs specWithSecretRefs(BackendType type, String secretName) {
        var keysByField = new LinkedHashMap<String, String>();
        var spec =
                switch (type) {
                    case SFTP -> {
                        keysByField.put("spec.sftp.passwordRef", "secret-key-1");
                        keysByField.put("spec.sftp.privateKeyRef", "secret-key-2");
                        keysByField.put("spec.sftp.privateKeyPassphraseRef", "secret-key-3");
                        yield sftp(s -> {
                            s.setPasswordRef(ref(secretName, "secret-key-1"));
                            s.setPrivateKeyRef(ref(secretName, "secret-key-2"));
                            s.setPrivateKeyPassphraseRef(ref(secretName, "secret-key-3"));
                        });
                    }
                    case S3 -> {
                        keysByField.put("spec.s3.accessKeyIDRef", "secret-key-1");
                        keysByField.put("spec.s3.secretAccessKeyRef", "secret-key-2");
                        yield s3(s -> {
                            s.setAccessKeyIDRef(ref(secretName, "secret-key-1"));
                            s.setSecretAccessKeyRef(ref(secretName, "secret-key-2"));
                        });
                    }
                    case CRYPT -> {
                        keysByField.put("spec.crypt.passwordRef", "secret-key-1");
                        keysByField.put("spec.crypt.saltRef", "secret-key-2");
                        yield crypt(s -> {
                            s.setRemoteRef(new RemoteRef(RemoteRef.Kind.CLUSTER_REMOTE, CRYPT_FIXTURE_WRAPPED));
                            s.setPasswordRef(ref(secretName, "secret-key-1"));
                            s.setSaltRef(ref(secretName, "secret-key-2"));
                        });
                    }
                    case TEMPLATE -> {
                        keysByField.put("spec.template.inputs[password]", "secret-key-1");
                        yield template(t -> {
                            t.setTemplate(":webdav,url=https://example.com,pass=${password}:");
                            t.setInputs(Map.of("password", ref(secretName, "secret-key-1")));
                        });
                    }
                };
        return new SpecWithRefs(spec, keysByField);
    }

    /**
     * Gives a namespaced remote a fresh namespace, so tests don't collide on names, and returns the namespace its
     * Secrets are resolved in: that one, or the operator namespace for a cluster remote.
     */
    private String placeInTestNamespace(HasMetadata remote) {
        if (remote instanceof RCloneRemote) {
            var namespace = freshNamespace();
            remote.getMetadata().setNamespace(namespace);
            return namespace;
        }
        return operatorNamespace();
    }

    private String freshNamespace() {
        return client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("rcloneremote-test-")
                        .endMetadata()
                        .build())
                .create()
                .getMetadata()
                .getName();
    }

    /** The operator namespace, created if needed. */
    private String operatorNamespace() {
        client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(config.operatorNamespace())
                        .endMetadata()
                        .build())
                .serverSideApply();
        return config.operatorNamespace();
    }

    /** Creates the RCloneClusterRemote {@code name} unless it exists, and waits until it is Ready. */
    private void ensureReadyClusterRemote(String name) {
        var remote = cluster(template(t -> t.setTemplate(VALID_TEMPLATE)));
        remote.getMetadata().setGenerateName(null);
        remote.getMetadata().setName(name);
        awaitReady(client.resource(remote).serverSideApply(), c -> "True".equals(c.getStatus()));
    }

    private void createSecret(String namespace, String name, List<String> keys) {
        var data = new LinkedHashMap<String, String>();
        keys.forEach(key -> data.put(key, "value of " + key));
        client.resource(new SecretBuilder()
                        .withNewMetadata()
                        .withNamespace(namespace)
                        .withName(name)
                        .endMetadata()
                        .withStringData(data)
                        .build())
                .create();
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
