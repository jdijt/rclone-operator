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
package eu.derfniw.rco.run;

import static eu.derfniw.rco.testsupport.Remotes.crypt;
import static eu.derfniw.rco.testsupport.Remotes.ref;
import static eu.derfniw.rco.testsupport.Remotes.s3;
import static eu.derfniw.rco.testsupport.Remotes.sftp;
import static eu.derfniw.rco.testsupport.Remotes.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RCloneConfigTest {

    /** Always the same IV, so the obscured values are predictable. */
    private static final byte[] IV = "aaaaaaaaaaaaaaaa".getBytes(StandardCharsets.US_ASCII);

    private static final Obscure OBSCURE = new Obscure(() -> IV);

    private static String obscured(String value) {
        return OBSCURE.obscure(value);
    }

    /** The destination of every case: a template without placeholders. */
    private static final ResolvedRemote MEMORY = new ResolvedRemote(template(t -> {}), Map.of(), null);

    private static final String MEMORY_DESTINATION = """
            [destination]
            type = alias
            remote = :memory:
            """;

    private static ResolvedRemote remote(RCloneRemoteSpec spec, Map<String, String> secrets) {
        return new ResolvedRemote(spec, secrets, null);
    }

    private static String render(ResolvedRemote source) throws RCloneConfig.InvalidValueException {
        return RCloneConfig.render(source, MEMORY, OBSCURE);
    }

    /** Every option of every backend, as the source. */
    static Stream<Arguments> backendCases() {
        return Stream.of(BackendType.values()).map(type -> switch (type) {
            case SFTP ->
                argumentSet(
                        "sftp",
                        remote(
                                sftp(s -> {
                                    s.setPort(2222);
                                    s.setPrivateKeyRef(ref("sftp", "key"));
                                    s.setPrivateKeyPassphraseRef(ref("sftp", "passphrase"));
                                    s.setConnections(4);
                                }),
                                Map.of(
                                        "sftp.passwordRef", "pw",
                                        "sftp.privateKeyRef", "-----BEGIN KEY-----\nabc\n-----END KEY-----\n",
                                        "sftp.privateKeyPassphraseRef", "phrase")),
                        """
                        [source]
                        type = sftp
                        host = host
                        port = 2222
                        user = user
                        pass = %s
                        key_pem = -----BEGIN KEY-----\\nabc\\n-----END KEY-----\\n
                        key_file_pass = %s
                        connections = 4
                        """.formatted(obscured("pw"), obscured("phrase")));
            case S3 ->
                argumentSet(
                        "s3",
                        remote(
                                s3(s -> {
                                    s.setProvider("Minio");
                                    s.setEndpoint("https://minio.example.com");
                                    s.setRegion("eu-west-1");
                                    s.setForcePathStyle(true);
                                    s.setStorageClass("STANDARD");
                                    s.setAcl("private");
                                    s.setNoCheckBucket(true);
                                }),
                                Map.of("s3.accessKeyIDRef", "id", "s3.secretAccessKeyRef", "secret")),
                        """
                        [source]
                        type = s3
                        provider = Minio
                        endpoint = https://minio.example.com
                        region = eu-west-1
                        access_key_id = id
                        secret_access_key = secret
                        force_path_style = true
                        storage_class = STANDARD
                        acl = private
                        no_check_bucket = true
                        """);
            case CRYPT ->
                argumentSet(
                        "crypt, with the remote it wraps in a section of its own",
                        new ResolvedRemote(
                                crypt(c -> {
                                    c.setPath("encrypted");
                                    c.setSaltRef(ref("crypt", "salt"));
                                    c.setFilenameEncryption(CryptBackend.FilenameEncryption.OBFUSCATE);
                                    c.setDirectoryNameEncryption(false);
                                    c.setFilenameEncoding(CryptBackend.FilenameEncoding.BASE32768);
                                }),
                                Map.of("crypt.passwordRef", "pw", "crypt.saltRef", "salt"),
                                MEMORY),
                        """
                        [source]
                        type = crypt
                        remote = source_1:encrypted
                        password = %s
                        password2 = %s
                        filename_encryption = obfuscate
                        directory_name_encryption = false
                        filename_encoding = base32768

                        [source_1]
                        type = alias
                        remote = :memory:
                        """.formatted(obscured("pw"), obscured("salt")));
            case TEMPLATE ->
                argumentSet(
                        "template, as an alias of the filled connection string",
                        remote(
                                template(t -> {
                                    t.setTemplate(":webdav,url='https://dav.example.com',user=${user},pass=${pass}:");
                                    t.setInputs(Map.of("user", ref("dav", "user"), "pass", ref("dav", "pass")));
                                }),
                                Map.of("template.inputs[user]", "me", "template.inputs[pass]", "already-obscured")),
                        """
                        [source]
                        type = alias
                        remote = :webdav,url='https://dav.example.com',user=me,pass=already-obscured:
                        """);
        });
    }

    @ParameterizedTest
    @MethodSource("backendCases")
    void rendersEveryOption(ResolvedRemote source, String sourceSections) throws Exception {
        assertThat(render(source)).isEqualTo(sourceSections + "\n" + MEMORY_DESTINATION);
    }

    @Test
    void unsetOptionsAreLeftOut() throws Exception {
        assertThat(render(remote(sftp(s -> {}), Map.of("sftp.passwordRef", "pw"))))
                .isEqualTo("""
                        [source]
                        type = sftp
                        host = host
                        user = user
                        pass = %s

                        """.formatted(obscured("pw")) + MEMORY_DESTINATION);
    }

    @Test
    void wrappedRemotesAreNumberedPerEndpoint() throws Exception {
        var inner = new ResolvedRemote(crypt(c -> {}), Map.of("crypt.passwordRef", "inner"), MEMORY);
        var outer = new ResolvedRemote(crypt(c -> {}), Map.of("crypt.passwordRef", "outer"), inner);

        assertThat(RCloneConfig.render(MEMORY, outer, OBSCURE))
                .isEqualTo("""
                        [source]
                        type = alias
                        remote = :memory:

                        [destination]
                        type = crypt
                        remote = destination_1:
                        password = %s

                        [destination_1]
                        type = crypt
                        remote = destination_2:
                        password = %s

                        [destination_2]
                        type = alias
                        remote = :memory:
                        """.formatted(obscured("outer"), obscured("inner")));
    }

    @Test
    void carriageReturnsInAPrivateKeyAreDropped() throws Exception {
        var source = remote(
                sftp(s -> {
                    s.setPasswordRef(null);
                    s.setPrivateKeyRef(ref("sftp", "key"));
                }),
                Map.of("sftp.privateKeyRef", "-----BEGIN KEY-----\r\nabc\r\n-----END KEY-----"));

        assertThat(render(source)).contains("key_pem = -----BEGIN KEY-----\\nabc\\n-----END KEY-----\n");
    }

    /** A line break would end the option and let the rest of the value add options of its own. */
    static Stream<Arguments> lineBreakCases() {
        return Stream.of(
                argumentSet(
                        "in a Secret value",
                        remote(sftp(s -> {}), Map.of("sftp.passwordRef", "pw\ntype = local")),
                        "Secret sftp key password"),
                argumentSet(
                        "in a template input",
                        remote(
                                template(t -> {
                                    t.setTemplate(":webdav,pass=${pass}:");
                                    t.setInputs(Map.of("pass", ref("dav", "pass")));
                                }),
                                Map.of("template.inputs[pass]", "pw\r")),
                        "Secret dav key pass"),
                argumentSet(
                        "in a spec field",
                        remote(sftp(s -> s.setHost("host\ntype = local")), Map.of("sftp.passwordRef", "pw")),
                        "sftp.host"));
    }

    @ParameterizedTest
    @MethodSource("lineBreakCases")
    void lineBreaksAreRejected(ResolvedRemote source, String names) {
        assertThatThrownBy(() -> render(source))
                .isInstanceOf(RCloneConfig.InvalidValueException.class)
                .hasMessageContaining(names)
                .hasMessageContaining("line break")
                .message()
                .doesNotContain("type = local", "pw");
    }
}
