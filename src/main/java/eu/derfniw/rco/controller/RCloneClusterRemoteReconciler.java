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
package eu.derfniw.rco.controller;

import eu.derfniw.rco.OperatorConfig;
import eu.derfniw.rco.api.v1alpha1.RCloneClusterRemote;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import jakarta.inject.Singleton;

@Singleton
@ControllerConfiguration(name = "rcloneclusterremote")
public class RCloneClusterRemoteReconciler extends AbstractRemoteReconciler<RCloneClusterRemote> {

    public RCloneClusterRemoteReconciler(OperatorConfig config) {
        super(config);
    }
}
