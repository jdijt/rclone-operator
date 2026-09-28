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

/** One rclone filter rule: includes or excludes the paths matching a pattern. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilterRule {

    /** What happens to the paths the pattern matches. */
    public enum Action {
        @JsonProperty("include")
        INCLUDE,
        @JsonProperty("exclude")
        EXCLUDE
    }

    /** What happens to the paths the pattern matches. */
    @Required
    private Action action;

    /** An rclone filter pattern (e.g. {@code *.tmp}, {@code /photos/**}), see https://rclone.org/filtering/. */
    @Required
    @Size(min = 1)
    private String pattern;

    public FilterRule() {}

    public FilterRule(Action action, String pattern) {
        this.action = action;
        this.pattern = pattern;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
