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
package eu.derfniw.rco.run;

import eu.derfniw.rco.api.v1alpha1.FilterRule;
import eu.derfniw.rco.api.v1alpha1.RCloneSyncSpec;
import eu.derfniw.rco.api.v1alpha1.SyncEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The rclone command line of a sync, run against the configuration of {@link RCloneConfig}. */
public final class SyncCommand {

    /** Directory the configuration file is mounted in. */
    public static final String CONFIG_DIR = "/etc/rclone";
    /** Name of the configuration file, and its key in the Secret holding it. */
    public static final String CONFIG_FILE = "rclone.conf";
    /** rclone's cache directory; it must be writable. */
    public static final String CACHE_DIR = "/tmp/rclone";

    private SyncCommand() {}

    /** The arguments to rclone (the image's entrypoint). */
    public static List<String> args(RCloneSyncSpec spec) {
        var args = new ArrayList<String>();
        args.add("sync");
        args.add(location(RCloneConfig.SOURCE, spec.getSource()));
        args.add(location(RCloneConfig.DESTINATION, spec.getDestination()));
        args.add("--config=" + CONFIG_DIR + "/" + CONFIG_FILE);
        args.add("--cache-dir=" + CACHE_DIR);

        var options = spec.getOptions();
        if (options == null) {
            return args;
        }
        if (Boolean.TRUE.equals(options.getDryRun())) {
            args.add("--dry-run");
        }
        if (options.getTransfers() != null) {
            args.add("--transfers=" + options.getTransfers());
        }
        if (options.getCheckers() != null) {
            args.add("--checkers=" + options.getCheckers());
        }
        if (options.getDeleteMode() != null) {
            args.add("--delete-" + options.getDeleteMode().name().toLowerCase(Locale.ROOT));
        }
        if (options.getFilters() != null) {
            for (var rule : options.getFilters()) {
                var sign = rule.getAction() == FilterRule.Action.INCLUDE ? "+" : "-";
                args.add("--filter=" + sign + " " + rule.getPattern());
            }
        }
        return args;
    }

    private static String location(String section, SyncEndpoint endpoint) {
        return section + ":" + (endpoint.getPath() == null ? "" : endpoint.getPath());
    }
}
