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

import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;

/**
 * Selects a single key of a Secret. For a namespaced RCloneRemote the Secret is looked up in the remote's
 * namespace; for an RCloneClusterRemote it is looked up in the operator's namespace.
 */
public class SecretKeyRef {

    /** Name of the Secret. */
    @Required
    @Size(min = 1)
    private String name;

    /** Key within the Secret's data. */
    @Required
    @Size(min = 1)
    private String key;

    public SecretKeyRef() {}

    public SecretKeyRef(String name, String key) {
        this.name = name;
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
