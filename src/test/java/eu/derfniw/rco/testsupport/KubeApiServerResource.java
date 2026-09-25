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
package eu.derfniw.rco.testsupport;

import io.fabric8.kubeapitest.KubeAPIServer;
import io.fabric8.kubeapitest.KubeAPIServerConfigBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhook;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.certs.CertificateGenerator;
import io.smallrye.certs.CertificateRequest;
import io.smallrye.certs.Format;
import io.smallrye.certs.PemCertificateFiles;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a real kube-apiserver and etcd (fabric8 kube-api-test) for {@code @QuarkusTest}s, and
 * registers the operator's validating webhooks against the Quarkus test HTTPS port.
 *
 * <p>The binaries are downloaded to {@code ~/.kubeapitest} on first use.
 */
public class KubeApiServerResource implements QuarkusTestResourceLifecycleManager {

    /** Name of the webhook configuration, shared with the production manifest. */
    public static final String WEBHOOK_CONFIGURATION = "rclone-operator-validating-webhook";

    /** Init arg: set to {@code "false"} to run without the validating webhooks registered. */
    public static final String WEBHOOKS = "webhooks";

    /** Where the fabric8 CRD generator writes the CRDs at build time. */
    private static final Path CRD_DIR = Path.of("target", "kubernetes");

    private KubeAPIServer apiServer;
    private boolean webhooks = true;

    @Override
    public void init(Map<String, String> initArgs) {
        webhooks = Boolean.parseBoolean(initArgs.getOrDefault(WEBHOOKS, "true"));
    }

    @Override
    public Map<String, String> start() {
        try {
            var certDir = Path.of("target", "webhook-certs");
            Files.createDirectories(certDir);
            var tls = (PemCertificateFiles) new CertificateGenerator(certDir, true)
                    .generate(new CertificateRequest()
                            .withName("webhook")
                            .withCN("localhost")
                            .withSubjectAlternativeName("DNS:localhost")
                            .withFormat(Format.PEM))
                    .getFirst();
            int httpsPort;
            try (var socket = new ServerSocket(0)) {
                httpsPort = socket.getLocalPort();
            }

            apiServer = new KubeAPIServer(KubeAPIServerConfigBuilder.anAPIServerConfig()
                    .withUpdateKubeConfig(false)
                    .build());
            apiServer.start();
            var kubeConfig = Config.fromKubeconfig(apiServer.getKubeConfigYaml());

            try (var client =
                    new KubernetesClientBuilder().withConfig(kubeConfig).build()) {
                applyCrds(client);
            }

            if (webhooks) {
                var caBundle = Base64.getEncoder().encodeToString(Files.readAllBytes(tls.certFile()));
                var baseUrl = "https://localhost:" + httpsPort + "/";
                ValidatingWebhookConfiguration configuration = new ValidatingWebhookConfigurationBuilder()
                        .withNewMetadata()
                        .withName(WEBHOOK_CONFIGURATION)
                        .endMetadata()
                        .withWebhooks(
                                webhook(
                                        "vrcloneremote.rco.frozenbits.se",
                                        "rcloneremotes",
                                        baseUrl + "webhooks/validate/rcloneremotes",
                                        caBundle),
                                webhook(
                                        "vrcloneclusterremote.rco.frozenbits.se",
                                        "rcloneclusterremotes",
                                        baseUrl + "webhooks/validate/rcloneclusterremotes",
                                        caBundle))
                        .build();
                try (var client =
                        new KubernetesClientBuilder().withConfig(kubeConfig).build()) {
                    client.resource(configuration).create();
                }
            }

            var props = new HashMap<String, String>();
            props.put("quarkus.kubernetes-client.api-server-url", kubeConfig.getMasterUrl());
            putEncodedData(props, "ca-cert", kubeConfig.getCaCertData(), kubeConfig.getCaCertFile());
            putEncodedData(props, "client-cert", kubeConfig.getClientCertData(), kubeConfig.getClientCertFile());
            putEncodedData(props, "client-key", kubeConfig.getClientKeyData(), kubeConfig.getClientKeyFile());
            props.put("quarkus.http.test-ssl-port", Integer.toString(httpsPort));
            props.put("quarkus.http.ssl.certificate.files", tls.certFile().toString());
            props.put("quarkus.http.ssl.certificate.key-files", tls.keyFile().toString());
            return props;
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Could not start the test API server", e);
        }
    }

    @Override
    public void stop() {
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    /**
     * Always passes the certificate as {@code -data}, so it overrides any {@code -data} value another
     * config source (such as a Dev Service) provides.
     */
    /**
     * Applies the generated CRDs and waits until they are served. The operator applies them too, but
     * starts its informers right away, which races the API server establishing a new CRD.
     */
    private static void applyCrds(KubernetesClient client) throws IOException {
        try (var files = Files.list(CRD_DIR)) {
            for (var file : files.filter(f -> f.toString().endsWith("-v1.yml")).toList()) {
                try (var in = Files.newInputStream(file)) {
                    var crd = client.apiextensions()
                            .v1()
                            .customResourceDefinitions()
                            .load(in)
                            .item();
                    client.resource(crd).serverSideApply();
                    client.resource(crd)
                            .waitUntilCondition(
                                    c -> c.getStatus() != null
                                            && c.getStatus().getConditions() != null
                                            && c.getStatus().getConditions().stream()
                                                    .anyMatch(cond -> "Established".equals(cond.getType())
                                                            && "True".equals(cond.getStatus())),
                                    30,
                                    TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void putEncodedData(Map<String, String> props, String name, String data, String file)
            throws IOException {
        if (data == null && file != null) {
            data = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(file)));
        }
        if (data != null) {
            props.put("quarkus.kubernetes-client." + name + "-data", data);
        }
    }

    private static ValidatingWebhook webhook(String name, String resource, String url, String caBundle) {
        return new ValidatingWebhookBuilder()
                .withName(name)
                .withAdmissionReviewVersions("v1")
                .withSideEffects("None")
                .withFailurePolicy("Fail")
                .withNewClientConfig()
                .withUrl(url)
                .withCaBundle(caBundle)
                .endClientConfig()
                .addNewRule()
                .withApiGroups("rco.frozenbits.se")
                .withApiVersions("v1alpha1")
                .withOperations(List.of("CREATE", "UPDATE"))
                .withResources(resource)
                .endRule()
                .build();
    }
}
