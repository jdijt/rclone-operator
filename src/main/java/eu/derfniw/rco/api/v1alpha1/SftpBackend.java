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
package eu.derfniw.rco.api.v1alpha1;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;

/** Maps to rclone's sftp backend. At least one of passwordRef or privateKeyRef must be set. */
@ValidationRule(
        value = "has(self.passwordRef) || has(self.privateKeyRef)",
        message = "one of passwordRef or privateKeyRef must be set")
@ValidationRule(
        value = "has(self.privateKeyPassphraseRef) ? has(self.privateKeyRef) : true",
        message = "privateKeyPassphrase supplied but no private key used")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SftpBackend implements Backend {

    /** Host to connect to (rclone option: host). */
    @Required
    @Size(min = 1)
    private String host;

    /** Port to connect to (rclone option: port). */
    @Default("22")
    @Min(1)
    @Max(65535)
    private Integer port;

    /** User to log in as (rclone option: user). */
    @Required
    @Size(min = 1)
    private String user;

    /** Selects the Secret key holding the SSH password (rclone option: pass). */
    private SecretKeyRef passwordRef;

    /** Selects the Secret key holding a PEM-encoded private key (rclone option: key_pem). */
    private SecretKeyRef privateKeyRef;

    /**
     * Selects the Secret key holding the passphrase for privateKeyRef, if the key is encrypted (rclone option:
     * key_file_pass).
     */
    private SecretKeyRef privateKeyPassphraseRef;

    /**
     * Maximum number of concurrent SSH connections rclone may open to this host (rclone option: connections). Unset
     * means rclone's default of unlimited.
     */
    @Min(1)
    private Integer connections;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public SecretKeyRef getPasswordRef() {
        return passwordRef;
    }

    public void setPasswordRef(SecretKeyRef passwordRef) {
        this.passwordRef = passwordRef;
    }

    public SecretKeyRef getPrivateKeyRef() {
        return privateKeyRef;
    }

    public void setPrivateKeyRef(SecretKeyRef privateKeyRef) {
        this.privateKeyRef = privateKeyRef;
    }

    public SecretKeyRef getPrivateKeyPassphraseRef() {
        return privateKeyPassphraseRef;
    }

    public void setPrivateKeyPassphraseRef(SecretKeyRef privateKeyPassphraseRef) {
        this.privateKeyPassphraseRef = privateKeyPassphraseRef;
    }

    public Integer getConnections() {
        return connections;
    }

    public void setConnections(Integer connections) {
        this.connections = connections;
    }
}
