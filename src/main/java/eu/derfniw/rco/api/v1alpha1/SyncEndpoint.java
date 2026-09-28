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
import io.fabric8.generator.annotation.Required;

/** A location on a remote: the {@code remote:path} of an rclone command line. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncEndpoint {

    /** The remote. */
    @Required
    private RemoteRef remoteRef;

    /**
     * Path within the remote. Unset means the remote's root. For s3 remotes the first path element is the bucket.
     */
    private String path;

    public SyncEndpoint() {}

    public SyncEndpoint(RemoteRef remoteRef, String path) {
        this.remoteRef = remoteRef;
        this.path = path;
    }

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
}
