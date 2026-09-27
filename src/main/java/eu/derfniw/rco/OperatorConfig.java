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
package eu.derfniw.rco;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

@ConfigMapping(prefix = "rclone-operator")
public interface OperatorConfig {

    /** Namespace the operator runs in. Secrets of cluster-scoped remotes are resolved here. */
    String operatorNamespace();

    /**
     * How often a remote whose Secrets or keys don't exist yet is re-checked.
     * (An upper bound on how long a remote will stay non-ready even after its secrets are placed).
     */
    @WithDefault("1m")
    @DurationMin(seconds = 1)
    Duration secretRecheckInterval();
}
