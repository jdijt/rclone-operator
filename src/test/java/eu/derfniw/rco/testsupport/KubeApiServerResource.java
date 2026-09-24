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
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.certs.CertificateGenerator;
import io.smallrye.certs.CertificateRequest;
import io.smallrye.certs.Format;
import io.smallrye.certs.PemCertificateFiles;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            props.put("quarkus.kubernetes-client.devservices.enabled", "false");
            props.put("quarkus.kubernetes-client.api-server-url", kubeConfig.getMasterUrl());
            putFirst(props, "ca-cert", kubeConfig.getCaCertData(), kubeConfig.getCaCertFile());
            putFirst(props, "client-cert", kubeConfig.getClientCertData(), kubeConfig.getClientCertFile());
            putFirst(props, "client-key", kubeConfig.getClientKeyData(), kubeConfig.getClientKeyFile());
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

    private static void putFirst(Map<String, String> props, String name, String data, String file) {
        if (data != null) {
            props.put("quarkus.kubernetes-client." + name + "-data", data);
        } else if (file != null) {
            props.put("quarkus.kubernetes-client." + name + "-file", file);
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
