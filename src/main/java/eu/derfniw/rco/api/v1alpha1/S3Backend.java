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
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;

/**
 * Maps to rclone's s3 backend. The bucket is not part of the remote; it is the first path element on the sync
 * endpoint, as in rclone.
 */
@ValidationRule(
        value = "self.provider != 'AWS' ? has(self.endpoint) : true",
        message = "Endpoint must be specified for non-aws providers")
@ValidationRule(
        value = "!has(self.endpoint) || (isURL(self.endpoint) && url(self.endpoint).getScheme() in ['http', 'https'])",
        message = "endpoint must be a valid http/https URL")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class S3Backend implements Backend {

    /** The rclone s3 provider name, e.g. AWS, Minio, Ceph, Wasabi, Other (rclone option: provider). */
    @Required
    @Size(min = 1)
    private String provider;

    /** Endpoint of the S3 API, for non-AWS providers (rclone option: endpoint). */
    private String endpoint;

    /** Region to connect to (rclone option: region). */
    private String region;

    /** Selects the Secret key holding the access key ID (rclone option: access_key_id). */
    @Required
    @JsonProperty("accessKeyIDRef")
    private SecretKeyRef accessKeyIDRef;

    /** Selects the Secret key holding the secret access key (rclone option: secret_access_key). */
    @Required
    private SecretKeyRef secretAccessKeyRef;

    /** Use path-style bucket addressing; required by most non-AWS providers (rclone option: force_path_style). */
    private Boolean forcePathStyle;

    /** Storage class to use when storing new objects (rclone option: storage_class). */
    private String storageClass;

    /** Canned ACL applied to created objects and buckets (rclone option: acl). */
    private String acl;

    /**
     * Skip checking for and creating the bucket, for credentials without bucket-level permissions (rclone option:
     * no_check_bucket).
     */
    private Boolean noCheckBucket;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    @JsonProperty("accessKeyIDRef")
    public SecretKeyRef getAccessKeyIDRef() {
        return accessKeyIDRef;
    }

    @JsonProperty("accessKeyIDRef")
    public void setAccessKeyIDRef(SecretKeyRef accessKeyIDRef) {
        this.accessKeyIDRef = accessKeyIDRef;
    }

    public SecretKeyRef getSecretAccessKeyRef() {
        return secretAccessKeyRef;
    }

    public void setSecretAccessKeyRef(SecretKeyRef secretAccessKeyRef) {
        this.secretAccessKeyRef = secretAccessKeyRef;
    }

    public Boolean getForcePathStyle() {
        return forcePathStyle;
    }

    public void setForcePathStyle(Boolean forcePathStyle) {
        this.forcePathStyle = forcePathStyle;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public String getAcl() {
        return acl;
    }

    public void setAcl(String acl) {
        this.acl = acl;
    }

    public Boolean getNoCheckBucket() {
        return noCheckBucket;
    }

    public void setNoCheckBucket(Boolean noCheckBucket) {
        this.noCheckBucket = noCheckBucket;
    }
}
