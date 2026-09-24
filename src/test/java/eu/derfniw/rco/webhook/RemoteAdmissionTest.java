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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteStatus;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.S3Backend;
import eu.derfniw.rco.api.v1alpha1.SecretKeyRef;
import eu.derfniw.rco.api.v1alpha1.SftpBackend;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Admission of remotes by a real API server with the operator's webhooks registered: CRD schema validation, CEL
 * rules, and the webhooks for rules neither can express.
 */
@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class RemoteAdmissionTest {

    private static final String NAMESPACED_DENIED =
            "admission webhook \"vrcloneremote.rco.frozenbits.se\" denied the request";
    private static final String CLUSTER_DENIED =
            "admission webhook \"vrcloneclusterremote.rco.frozenbits.se\" denied the request";

    @Inject
    KubernetesClient client;

    static Stream<Arguments> acceptedCases() {
        return Stream.of(
                argumentSet("namespaced template", namespaced(template("valid"))),
                argumentSet("cluster template", cluster(template("valid"))),
                argumentSet(
                        "template with declared input",
                        namespaced(templateWithInputs(
                                ":webdav,pass=${password}:", Map.of("password", ref("webdav", "password"))))),
                argumentSet("sftp with password", namespaced(sftp(s -> s.setPasswordRef(ref("sftp", "password"))))),
                argumentSet("sftp with private key and passphrase", namespaced(sftp(s -> {
                    s.setPrivateKeyRef(ref("sftp", "key"));
                    s.setPrivateKeyPassphraseRef(ref("sftp", "passphrase"));
                }))),
                argumentSet("s3 on AWS without endpoint", namespaced(s3("AWS", null))),
                argumentSet("s3 on Ceph with https endpoint", namespaced(s3("Ceph", "https://s3.example.com"))),
                // RCloneRemote may refer to either kind; remoteRef.kind defaults to RCloneRemote.
                argumentSet("namespaced crypt referring to RCloneRemote by default", namespaced(crypt(null))),
                argumentSet(
                        "namespaced crypt referring to RCloneClusterRemote",
                        namespaced(crypt(RemoteRef.Kind.CLUSTER_REMOTE))),
                argumentSet(
                        "cluster crypt referring to RCloneClusterRemote",
                        cluster(crypt(RemoteRef.Kind.CLUSTER_REMOTE))));
    }

    @ParameterizedTest
    @MethodSource("acceptedCases")
    void createAccepted(HasMetadata resource) {
        placeInTestNamespace(resource);
        assertThat(client.resource(resource).create()).isNotNull();
    }

    static Stream<Arguments> rejectedCases() {
        return Stream.of(
                // CRD structural (OpenAPI) validation: one row per kind of constraint.
                argumentSet(
                        "enum rejects unknown type",
                        new GenericKubernetesResourceBuilder()
                                .withApiVersion(RCloneRemote.GROUP + "/" + RCloneRemote.VERSION)
                                .withKind("RCloneRemote")
                                .withNewMetadata()
                                .withName("remote")
                                .endMetadata()
                                .addToAdditionalProperties("spec", Map.of("type", "ftp"))
                                .build(),
                        "Unsupported value: \"ftp\""),
                argumentSet(
                        "minLength rejects empty template",
                        namespaced(template("")),
                        "should be at least 1 chars long"),
                argumentSet(
                        "maximum rejects out of range sftp port",
                        namespaced(sftp(s -> {
                            s.setPort(70000);
                            s.setPasswordRef(ref("sftp", "password"));
                        })),
                        "should be less than or equal to 65535"),

                // CEL: type <=> matching backend set.
                argumentSet(
                        "CEL rejects type sftp without sftp",
                        namespaced(spec(BackendType.SFTP)),
                        "sftp must be set if and only if type is sftp"),
                argumentSet(
                        "CEL rejects type s3 without s3",
                        namespaced(spec(BackendType.S3)),
                        "s3 must be set if and only if type is s3"),
                argumentSet(
                        "CEL rejects type crypt without crypt",
                        namespaced(spec(BackendType.CRYPT)),
                        "crypt must be set if and only if type is crypt"),
                argumentSet(
                        "CEL rejects type template without template",
                        namespaced(spec(BackendType.TEMPLATE)),
                        "template must be set if and only if type is template"),
                argumentSet(
                        "CEL rejects a second backend besides the selected one",
                        namespaced(with(template("valid"), s -> s.setS3(s3Backend("AWS", null)))),
                        "s3 must be set if and only if type is s3"),

                // CEL: sftp credentials.
                argumentSet(
                        "CEL rejects sftp without password or private key",
                        namespaced(sftp(s -> {})),
                        "one of passwordRef or privateKeyRef must be set"),
                argumentSet(
                        "CEL rejects sftp passphrase without private key",
                        namespaced(sftp(s -> {
                            s.setPasswordRef(ref("sftp", "password"));
                            s.setPrivateKeyPassphraseRef(ref("sftp", "passphrase"));
                        })),
                        "privateKeyPassphrase supplied but no private key used"),

                // CEL: s3 endpoint.
                argumentSet(
                        "CEL rejects s3 on non-AWS without endpoint",
                        namespaced(s3("Ceph", null)),
                        "Endpoint must be specified for non-aws providers"),
                argumentSet(
                        "CEL rejects s3 endpoint with non-http scheme",
                        namespaced(s3("Ceph", "ftp://s3.example.com")),
                        "endpoint must be a valid http/https URL"),

                // CEL: RCloneClusterRemote may only refer to RCloneClusterRemote (remoteRef.kind defaults to
                // RCloneRemote).
                argumentSet(
                        "CEL rejects cluster crypt referring to RCloneRemote",
                        cluster(crypt(RemoteRef.Kind.REMOTE)),
                        "RCloneClusterRemote can only refer to other RCloneClusterRemotes."),
                argumentSet(
                        "CEL rejects cluster crypt with defaulted kind",
                        cluster(crypt(null)),
                        "RCloneClusterRemote can only refer to other RCloneClusterRemotes."),

                // Webhook: one rejection per kind proves each webhook is wired; the rules themselves are covered by
                // RemoteSpecValidatorTest.
                argumentSet(
                        "webhook rejects namespaced template with undeclared input",
                        namespaced(template("${missing}")),
                        NAMESPACED_DENIED),
                argumentSet(
                        "webhook rejects cluster template with undeclared input",
                        cluster(template("${missing}")),
                        CLUSTER_DENIED),
                argumentSet(
                        "webhook reports the offending field",
                        namespaced(template("${missing}")),
                        "spec.template.template: Invalid value: \"${missing}\": reference to undeclared field missing"));
    }

    @ParameterizedTest
    @MethodSource("rejectedCases")
    void createRejected(HasMetadata resource, String wantError) {
        placeInTestNamespace(resource);
        assertThatThrownBy(() -> client.resource(resource).create())
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining(wantError);
    }

    static Stream<Arguments> updateCases() {
        return Stream.of(
                argumentSet(
                        "webhook rejects namespaced update to undeclared input",
                        namespaced(template("valid")),
                        NAMESPACED_DENIED),
                argumentSet(
                        "webhook rejects cluster update to undeclared input",
                        cluster(template("valid")),
                        CLUSTER_DENIED));
    }

    /** Makes a valid object invalid (for the webhook only, CEL still passes), proving the webhooks see UPDATE. */
    @ParameterizedTest
    @MethodSource("updateCases")
    void update(CustomResource<RCloneRemoteSpec, RCloneRemoteStatus> resource, String wantError) {
        placeInTestNamespace(resource);
        var created = client.resource(resource).create();

        // The operator may write status concurrently; unlock() drops the resourceVersion precondition from the patch.
        assertThatThrownBy(() -> client.resource(created).unlock().edit(r -> {
                    r.getSpec().getTemplate().setTemplate("${missing}");
                    return r;
                }))
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining(wantError);
    }

    /**
     * Stores objects the webhooks would reject (with the webhook configuration removed), restores the webhooks, then
     * checks the objects can still be deleted. This is the upgrade case: validation got stricter, and existing
     * objects must stay deletable.
     */
    @Test
    void objectsRejectedByWebhookCanBeDeleted() {
        var webhooks = client.admissionRegistration()
                .v1()
                .validatingWebhookConfigurations()
                .withName(KubeApiServerResource.WEBHOOK_CONFIGURATION)
                .get();
        var saved = client.getKubernetesSerialization().clone(webhooks);
        saved.getMetadata().setResourceVersion(null);
        saved.getMetadata().setUid(null);

        var created = new ArrayList<HasMetadata>();
        try {
            client.resource(webhooks).delete();
            for (HasMetadata resource : List.of(namespaced(template("${missing}")), cluster(template("${missing}")))) {
                placeInTestNamespace(resource);
                // Webhook configuration changes reach the API server's admission chain asynchronously.
                created.add(await().atMost(Duration.ofSeconds(10))
                        .ignoreException(KubernetesClientException.class)
                        .until(() -> client.resource(resource).create(), Objects::nonNull));
            }
        } finally {
            if (client.resource(saved).get() == null) {
                client.resource(saved).create();
            }
        }

        // Wait until the restored webhooks are in effect.
        var probe = cluster(template("${missing}"));
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            try {
                client.resource(probe).dryRun().create();
                return false;
            } catch (KubernetesClientException e) {
                return e.getMessage().contains("denied the request");
            }
        });

        assertThat(created).hasSize(2);
        for (var resource : created) {
            assertThat(client.resource(resource).delete()).isNotEmpty();
        }
    }

    /** Namespaced objects each get a fresh namespace, so rows don't collide on names. */
    private void placeInTestNamespace(HasMetadata resource) {
        if (!resource.getKind().equals("RCloneRemote")) {
            return;
        }
        var ns = client.resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("webhook-test-")
                        .endMetadata()
                        .build())
                .create();
        resource.getMetadata().setNamespace(ns.getMetadata().getName());
    }

    private static RCloneRemote namespaced(RCloneRemoteSpec spec) {
        var remote = new RCloneRemote();
        remote.setMetadata(new ObjectMetaBuilder().withName("remote").build());
        remote.setSpec(spec);
        return remote;
    }

    private static RCloneClusterRemote cluster(RCloneRemoteSpec spec) {
        var remote = new RCloneClusterRemote();
        remote.setMetadata(new ObjectMetaBuilder().withGenerateName("remote-").build());
        remote.setSpec(spec);
        return remote;
    }

    private static RCloneRemoteSpec spec(BackendType type) {
        var spec = new RCloneRemoteSpec();
        spec.setType(type);
        return spec;
    }

    private static RCloneRemoteSpec with(RCloneRemoteSpec spec, Consumer<RCloneRemoteSpec> change) {
        change.accept(spec);
        return spec;
    }

    private static RCloneRemoteSpec template(String template) {
        return templateWithInputs(template, null);
    }

    private static RCloneRemoteSpec templateWithInputs(String template, Map<String, SecretKeyRef> inputs) {
        return with(spec(BackendType.TEMPLATE), s -> s.setTemplate(new TemplateBackend(template, inputs)));
    }

    private static RCloneRemoteSpec sftp(Consumer<SftpBackend> change) {
        var sftp = new SftpBackend();
        sftp.setHost("host");
        sftp.setUser("user");
        change.accept(sftp);
        return with(spec(BackendType.SFTP), s -> s.setSftp(sftp));
    }

    private static S3Backend s3Backend(String provider, String endpoint) {
        var s3 = new S3Backend();
        s3.setProvider(provider);
        s3.setEndpoint(endpoint);
        s3.setAccessKeyIDRef(ref("s3", "id"));
        s3.setSecretAccessKeyRef(ref("s3", "secret"));
        return s3;
    }

    private static RCloneRemoteSpec s3(String provider, String endpoint) {
        return with(spec(BackendType.S3), s -> s.setS3(s3Backend(provider, endpoint)));
    }

    private static RCloneRemoteSpec crypt(RemoteRef.Kind kind) {
        var crypt = new CryptBackend();
        crypt.setRemoteRef(new RemoteRef(kind, "wrapped"));
        crypt.setPasswordRef(ref("crypt", "password"));
        return with(spec(BackendType.CRYPT), s -> s.setCrypt(crypt));
    }

    private static SecretKeyRef ref(String name, String key) {
        return new SecretKeyRef(name, key);
    }
}
