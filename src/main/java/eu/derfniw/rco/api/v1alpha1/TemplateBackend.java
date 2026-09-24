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
import io.fabric8.generator.annotation.Size;
import java.util.Map;

/**
 * Builds a remote from an rclone connection string template (e.g. {@code
 * :webdav,url=https://example.com,user=me,pass=${password}:}) whose {@code ${name}} placeholders are filled from
 * Secret keys. The author of the template is responsible for rclone's connection string quoting of the substituted
 * values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateBackend {

    /** The rclone connection string with {@code ${name}} placeholders for inputs. */
    @Required
    @Size(min = 1)
    private String template;

    /** Maps placeholder names to the Secret keys that fill them. */
    private Map<String, SecretKeyRef> inputs;

    public TemplateBackend() {}

    public TemplateBackend(String template, Map<String, SecretKeyRef> inputs) {
        this.template = template;
        this.inputs = inputs;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, SecretKeyRef> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, SecretKeyRef> inputs) {
        this.inputs = inputs;
    }
}
