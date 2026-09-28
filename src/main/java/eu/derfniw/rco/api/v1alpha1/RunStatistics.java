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

/** Statistics of one run of an RCloneSync, as reported by rclone. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunStatistics {

    /** Bytes transferred to the destination. */
    private Long bytesTransferred;

    /** Files transferred to the destination. */
    private Long filesTransferred;

    /** Files deleted from the destination because they are not in the source. */
    private Long filesDeleted;

    /** Average transfer speed over the run, in bytes per second. */
    private Long bytesPerSecond;

    /** Number of errors rclone reported, e.g. files that failed to transfer. */
    private Long errors;

    public Long getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(Long bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    public Long getFilesTransferred() {
        return filesTransferred;
    }

    public void setFilesTransferred(Long filesTransferred) {
        this.filesTransferred = filesTransferred;
    }

    public Long getFilesDeleted() {
        return filesDeleted;
    }

    public void setFilesDeleted(Long filesDeleted) {
        this.filesDeleted = filesDeleted;
    }

    public Long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public void setBytesPerSecond(Long bytesPerSecond) {
        this.bytesPerSecond = bytesPerSecond;
    }

    public Long getErrors() {
        return errors;
    }

    public void setErrors(Long errors) {
        this.errors = errors;
    }
}
