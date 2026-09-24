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
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Required;

/** Maps to rclone's crypt backend, which encrypts another remote. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CryptBackend {

    /** The rclone crypt filename_encryption mode. */
    public enum FilenameEncryption {
        @JsonProperty("standard")
        STANDARD,
        @JsonProperty("obfuscate")
        OBFUSCATE,
        @JsonProperty("off")
        OFF
    }

    /** The rclone crypt filename_encoding. */
    public enum FilenameEncoding {
        @JsonProperty("base32")
        BASE32,
        @JsonProperty("base64")
        BASE64,
        @JsonProperty("base32768")
        BASE32768
    }

    /**
     * References the remote to wrap (rclone option: remote).
     *
     * <p>On an RCloneRemote (namespace scoped) this can refer to other RCloneRemotes in the same namespace and to
     * RCloneClusterRemotes. On an RCloneClusterRemote this can only refer to other RCloneClusterRemotes.
     */
    @Required
    private RemoteRef remoteRef;

    /** Path within the wrapped remote that holds the encrypted data (rclone option: remote, the part after the colon). */
    private String path;

    /** Selects the Secret key holding the encryption password (rclone option: password). */
    @Required
    private SecretKeyRef passwordRef;

    /** Selects the Secret key holding the optional salt (rclone option: password2). */
    private SecretKeyRef saltRef;

    /** Selects how file names are encrypted (rclone option: filename_encryption). */
    @Default("standard")
    private FilenameEncryption filenameEncryption;

    /** Enables encryption of directory names (rclone option: directory_name_encryption). */
    @Default("true")
    private Boolean directoryNameEncryption;

    /** Selects the encoding of encrypted file names (rclone option: filename_encoding). */
    @Default("base32")
    private FilenameEncoding filenameEncoding;

    public RemoteRef getRemoteRef() {
        return remoteRef;
    }

    public void setRemoteRef(RemoteRef remoteRef) {
        this.remoteRef = remoteRef;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public SecretKeyRef getPasswordRef() {
        return passwordRef;
    }

    public void setPasswordRef(SecretKeyRef passwordRef) {
        this.passwordRef = passwordRef;
    }

    public SecretKeyRef getSaltRef() {
        return saltRef;
    }

    public void setSaltRef(SecretKeyRef saltRef) {
        this.saltRef = saltRef;
    }

    public FilenameEncryption getFilenameEncryption() {
        return filenameEncryption;
    }

    public void setFilenameEncryption(FilenameEncryption filenameEncryption) {
        this.filenameEncryption = filenameEncryption;
    }

    public Boolean getDirectoryNameEncryption() {
        return directoryNameEncryption;
    }

    public void setDirectoryNameEncryption(Boolean directoryNameEncryption) {
        this.directoryNameEncryption = directoryNameEncryption;
    }

    public FilenameEncoding getFilenameEncoding() {
        return filenameEncoding;
    }

    public void setFilenameEncoding(FilenameEncoding filenameEncoding) {
        this.filenameEncoding = filenameEncoding;
    }
}
