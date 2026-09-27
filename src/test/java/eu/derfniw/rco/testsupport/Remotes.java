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

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.S3Backend;
import eu.derfniw.rco.api.v1alpha1.SecretKeyRef;
import eu.derfniw.rco.api.v1alpha1.SftpBackend;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.function.Consumer;

/**
 * Builds RCloneRemotes, RCloneClusterRemotes and their specs for test cases.
 *
 * <p>Each backend builder starts from a backend that passes the CRD schema and CEL rules for both kinds of remote,
 * then applies {@code change}: a test case states only what it varies. Secret references point at placeholder Secrets.
 */
public final class Remotes {

    private Remotes() {}

    /** An RCloneRemote named "remote" without a namespace. */
    public static RCloneRemote namespaced(RCloneRemoteSpec spec) {
        var remote = new RCloneRemote();
        remote.setMetadata(new ObjectMetaBuilder().withName("remote").build());
        remote.setSpec(spec);
        return remote;
    }

    /** An RCloneClusterRemote with a generated name, since cluster-scoped names are shared by all tests. */
    public static RCloneClusterRemote cluster(RCloneRemoteSpec spec) {
        var remote = new RCloneClusterRemote();
        remote.setMetadata(new ObjectMetaBuilder().withGenerateName("remote-").build());
        remote.setSpec(spec);
        return remote;
    }

    /** A spec with only the type set. */
    public static RCloneRemoteSpec spec(BackendType type) {
        var spec = new RCloneRemoteSpec();
        spec.setType(type);
        return spec;
    }

    public static RCloneRemoteSpec with(RCloneRemoteSpec spec, Consumer<RCloneRemoteSpec> change) {
        change.accept(spec);
        return spec;
    }

    /** Defaults: a template without placeholders and no inputs. */
    public static RCloneRemoteSpec template(Consumer<TemplateBackend> change) {
        var template = new TemplateBackend(":memory:", null);
        change.accept(template);
        return with(spec(BackendType.TEMPLATE), s -> s.setTemplate(template));
    }

    /** Defaults: host, user and a password. */
    public static RCloneRemoteSpec sftp(Consumer<SftpBackend> change) {
        var sftp = new SftpBackend();
        sftp.setHost("host");
        sftp.setUser("user");
        sftp.setPasswordRef(ref("sftp", "password"));
        change.accept(sftp);
        return with(spec(BackendType.SFTP), s -> s.setSftp(sftp));
    }

    /** Defaults: provider AWS (so no endpoint) and both key references. */
    public static RCloneRemoteSpec s3(Consumer<S3Backend> change) {
        var s3 = new S3Backend();
        s3.setProvider("AWS");
        s3.setAccessKeyIDRef(ref("s3", "id"));
        s3.setSecretAccessKeyRef(ref("s3", "secret"));
        change.accept(s3);
        return with(spec(BackendType.S3), s -> s.setS3(s3));
    }

    /**
     * Defaults: wraps an RCloneClusterRemote named "wrapped" (the one kind both remotes may refer to) and a password.
     */
    public static RCloneRemoteSpec crypt(Consumer<CryptBackend> change) {
        var crypt = new CryptBackend();
        crypt.setRemoteRef(new RemoteRef(RemoteRef.Kind.CLUSTER_REMOTE, "wrapped"));
        crypt.setPasswordRef(ref("crypt", "password"));
        change.accept(crypt);
        return with(spec(BackendType.CRYPT), s -> s.setCrypt(crypt));
    }

    public static SecretKeyRef ref(String name, String key) {
        return new SecretKeyRef(name, key);
    }
}
