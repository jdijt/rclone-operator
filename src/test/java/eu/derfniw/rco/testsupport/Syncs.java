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
package eu.derfniw.rco.testsupport;

import eu.derfniw.rco.api.v1alpha1.RCloneSync;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRun;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncRunSpec;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec;
import eu.derfniw.rco.api.v1alpha1.RemoteRef;
import eu.derfniw.rco.api.v1alpha1.SyncEndpoint;
import eu.derfniw.rco.api.v1alpha1.SyncRef;
import eu.derfniw.rco.api.v1alpha1.SyncTrigger;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.function.Consumer;

/**
 * Builds RCloneSyncs and RCloneSyncRuns for test cases.
 *
 * <p>Each builder starts from a resource that passes the CRD schema and CEL rules, then applies {@code change}: a test
 * case states only what it varies. Referenced objects need not exist.
 */
public final class Syncs {

    private Syncs() {}

    /**
     * An RCloneSync named "sync" without a namespace. Defaults: from the RCloneRemote "source" to the
     * RCloneClusterRemote "destination", on the cron schedule "0 3 * * *" in UTC, without options.
     */
    public static RCloneSync sync(Consumer<RCloneSyncSpec> change) {
        var spec = new RCloneSyncSpec();
        spec.setSource(new SyncEndpoint(new RemoteRef(RemoteRef.Kind.REMOTE, "source"), null));
        spec.setDestination(new SyncEndpoint(new RemoteRef(RemoteRef.Kind.CLUSTER_REMOTE, "destination"), null));
        spec.setTrigger(SyncTrigger.cron("0 3 * * *"));
        change.accept(spec);

        var sync = new RCloneSync();
        sync.setMetadata(new ObjectMetaBuilder().withName("sync").build());
        sync.setSpec(spec);
        return sync;
    }

    /** An RCloneSyncRun named "run" without a namespace. Defaults: runs the RCloneSync "sync". */
    public static RCloneSyncRun run(Consumer<RCloneSyncRunSpec> change) {
        var spec = new RCloneSyncRunSpec(new SyncRef("sync"));
        change.accept(spec);

        var run = new RCloneSyncRun();
        run.setMetadata(new ObjectMetaBuilder().withName("run").build());
        run.setSpec(spec);
        return run;
    }
}
