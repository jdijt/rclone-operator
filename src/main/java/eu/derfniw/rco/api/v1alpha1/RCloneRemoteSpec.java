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
import eu.derfniw.rco.remote.SelectedBackendPresent;
import eu.derfniw.rco.validation.Reason;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Desired state of an RCloneRemote or RCloneClusterRemote.
 *
 * <p>It is a discriminated union: {@code type} selects which of the backend fields must be set.
 */
@ValidationRule(
        value = "self.type == 'sftp' ? has(self.sftp) : !has(self.sftp)",
        message = "sftp must be set if and only if type is sftp")
@ValidationRule(
        value = "self.type == 'crypt' ? has(self.crypt) : !has(self.crypt)",
        message = "crypt must be set if and only if type is crypt")
@ValidationRule(
        value = "self.type == 's3' ? has(self.s3) : !has(self.s3)",
        message = "s3 must be set if and only if type is s3")
@ValidationRule(
        value = "self.type == 'template' ? has(self.template) : !has(self.template)",
        message = "template must be set if and only if type is template")
@SelectedBackendPresent(payload = Reason.Required.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RCloneRemoteSpec {

    /** Selects the rclone backend. Exactly the matching backend field must be set. */
    @Required
    @NotNull(payload = Reason.NotSupported.class, message = "supported values: sftp, s3, crypt, template")
    private BackendType type;

    /** Configures an rclone sftp backend. */
    @Valid
    private SftpBackend sftp;

    /** Configures an rclone crypt backend wrapping another remote. */
    @Valid
    private CryptBackend crypt;

    /** Configures an rclone s3 backend. */
    @Valid
    private S3Backend s3;

    /** Configures a remote from a free-form rclone connection string template with secret inputs. */
    @Valid
    private TemplateBackend template;

    public BackendType getType() {
        return type;
    }

    public void setType(BackendType type) {
        this.type = type;
    }

    public SftpBackend getSftp() {
        return sftp;
    }

    public void setSftp(SftpBackend sftp) {
        this.sftp = sftp;
    }

    public CryptBackend getCrypt() {
        return crypt;
    }

    public void setCrypt(CryptBackend crypt) {
        this.crypt = crypt;
    }

    public S3Backend getS3() {
        return s3;
    }

    public void setS3(S3Backend s3) {
        this.s3 = s3;
    }

    public TemplateBackend getTemplate() {
        return template;
    }

    public void setTemplate(TemplateBackend template) {
        this.template = template;
    }

    /** The backend field selected by {@code type}, or {@code null} if the type or that field is unset. */
    public Backend selectedBackend() {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SFTP -> sftp;
            case S3 -> s3;
            case CRYPT -> crypt;
            case TEMPLATE -> template;
        };
    }
}
