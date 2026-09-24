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

import com.fasterxml.jackson.annotation.JsonProperty;

/** The type of rclone backend a remote uses. Values mirror rclone's backend names. */
public enum BackendType {
    @JsonProperty("sftp")
    SFTP,
    @JsonProperty("s3")
    S3,
    @JsonProperty("crypt")
    CRYPT,
    @JsonProperty("template")
    TEMPLATE;

    /** The field name of this backend's variant in {@link RCloneRemoteSpec}. */
    public String fieldName() {
        return switch (this) {
            case SFTP -> "sftp";
            case S3 -> "s3";
            case CRYPT -> "crypt";
            case TEMPLATE -> "template";
        };
    }
}
