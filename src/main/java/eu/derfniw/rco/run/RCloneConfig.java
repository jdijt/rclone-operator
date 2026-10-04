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

import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.S3Backend;
import eu.derfniw.rco.api.v1alpha1.SftpBackend;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import eu.derfniw.rco.remote.Templates;
import java.util.HashMap;
import java.util.Locale;

/**
 * Renders the rclone configuration file of a sync: the sections {@value #SOURCE} and {@value #DESTINATION}, plus one
 * section per remote a crypt remote wraps ({@code source_1}, {@code source_2}, ...).
 *
 * <p>Every value must fit on one line: a line break would end the option and let the rest of the value add options of
 * its own.
 */
public final class RCloneConfig {

    public static final String SOURCE = "source";
    public static final String DESTINATION = "destination";

    /** A value that can't be written to the configuration file. The message never contains the value. */
    public static final class InvalidValueException extends Exception {
        public InvalidValueException(String message) {
            super(message);
        }
    }

    private RCloneConfig() {}

    /** Renders the configuration, with password options obscured by {@code obscure}. */
    public static String render(ResolvedRemote source, ResolvedRemote destination, Obscure obscure)
            throws InvalidValueException {
        var out = new StringBuilder();
        appendChain(out, SOURCE, source, obscure);
        out.append('\n');
        appendChain(out, DESTINATION, destination, obscure);
        return out.toString();
    }

    /** The remote, then each remote it wraps, in a section of its own. */
    private static void appendChain(StringBuilder out, String name, ResolvedRemote remote, Obscure obscure)
            throws InvalidValueException {
        var section = name;
        int depth = 0;
        for (var current = remote; current != null; current = current.wrapped()) {
            if (depth > 0) {
                out.append('\n');
            }
            depth++;
            var wrappedSection = name + "_" + depth;
            new Section(out, section, current, obscure).render(wrappedSection);
            section = wrappedSection;
        }
    }

    /** Writes one section, checking each value. */
    private static final class Section {

        private final StringBuilder out;
        private final String name;
        private final ResolvedRemote remote;
        private final Obscure obscure;

        Section(StringBuilder out, String name, ResolvedRemote remote, Obscure obscure) {
            this.out = out;
            this.name = name;
            this.remote = remote;
            this.obscure = obscure;
        }

        void render(String wrappedSection) throws InvalidValueException {
            out.append('[').append(name).append("]\n");
            var spec = remote.spec();
            switch (spec.getType()) {
                case SFTP -> sftp(spec.getSftp());
                case S3 -> s3(spec.getS3());
                case CRYPT -> crypt(spec.getCrypt(), wrappedSection);
                case TEMPLATE -> template(spec.getTemplate());
            }
        }

        private void sftp(SftpBackend sftp) throws InvalidValueException {
            option("type", "sftp");
            option("host", "sftp.host", sftp.getHost());
            option("port", "sftp.port", sftp.getPort());
            option("user", "sftp.user", sftp.getUser());
            obscuredSecret("pass", "sftp.passwordRef");
            var key = secret("sftp.privateKeyRef", true);
            if (key != null) {
                // rclone reads key_pem from one line, with its line breaks written as \n.
                option("key_pem", key.replace("\r", "").replace("\n", "\\n"));
            }
            obscuredSecret("key_file_pass", "sftp.privateKeyPassphraseRef");
            option("connections", "sftp.connections", sftp.getConnections());
        }

        private void s3(S3Backend s3) throws InvalidValueException {
            option("type", "s3");
            option("provider", "s3.provider", s3.getProvider());
            option("endpoint", "s3.endpoint", s3.getEndpoint());
            option("region", "s3.region", s3.getRegion());
            option("access_key_id", secret("s3.accessKeyIDRef", false));
            option("secret_access_key", secret("s3.secretAccessKeyRef", false));
            option("force_path_style", "s3.forcePathStyle", s3.getForcePathStyle());
            option("storage_class", "s3.storageClass", s3.getStorageClass());
            option("acl", "s3.acl", s3.getAcl());
            option("no_check_bucket", "s3.noCheckBucket", s3.getNoCheckBucket());
        }

        private void crypt(CryptBackend crypt, String wrappedSection) throws InvalidValueException {
            option("type", "crypt");
            checked("crypt.path", crypt.getPath());
            option("remote", wrappedSection + ":" + (crypt.getPath() == null ? "" : crypt.getPath()));
            obscuredSecret("password", "crypt.passwordRef");
            obscuredSecret("password2", "crypt.saltRef");
            option("filename_encryption", lowerCase(crypt.getFilenameEncryption()));
            option("directory_name_encryption", "crypt.directoryNameEncryption", crypt.getDirectoryNameEncryption());
            option("filename_encoding", lowerCase(crypt.getFilenameEncoding()));
        }

        /** An alias of the connection string, since a section can't hold one directly. */
        private void template(TemplateBackend template) throws InvalidValueException {
            var values = new HashMap<String, String>();
            if (template.getInputs() != null) {
                for (var name : template.getInputs().keySet()) {
                    values.put(name, secret("template.inputs[" + name + "]", false));
                }
            }
            option("type", "alias");
            checked("template.template", template.getTemplate());
            option("remote", Templates.fill(template.getTemplate(), values));
        }

        /** The value of a Secret reference, or null if the reference is unset. */
        private String secret(String field, boolean multiline) throws InvalidValueException {
            var ref = remote.spec().secretKeyRefs().get(field);
            if (ref == null) {
                return null;
            }
            var value = remote.secrets().get(field);
            if (value == null) {
                throw new IllegalArgumentException("no value for " + field + " of " + name);
            }
            if (!multiline && hasLineBreak(value)) {
                throw new InvalidValueException("the value of Secret " + ref.getName() + " key " + ref.getKey()
                        + ", used for " + field + " of the " + name + " remote, contains a line break");
            }
            return value;
        }

        private void obscuredSecret(String key, String field) throws InvalidValueException {
            var value = secret(field, false);
            if (value != null) {
                option(key, obscure.obscure(value));
            }
        }

        /** An option from a spec field, left out if unset. */
        private void option(String key, String field, Object value) throws InvalidValueException {
            if (value != null) {
                var text = value.toString();
                checked(field, text);
                option(key, text);
            }
        }

        private void checked(String field, String value) throws InvalidValueException {
            if (value != null && hasLineBreak(value)) {
                throw new InvalidValueException(field + " of the " + name + " remote contains a line break");
            }
        }

        /** An option whose value is known to fit on a line, left out if null. */
        private void option(String key, String value) {
            if (value != null) {
                out.append(key).append(" = ").append(value).append('\n');
            }
        }

        private static String lowerCase(Enum<?> value) {
            return value == null ? null : value.name().toLowerCase(Locale.ROOT);
        }

        private static boolean hasLineBreak(String value) {
            return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        }
    }
}
