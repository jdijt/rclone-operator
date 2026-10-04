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

import static eu.derfniw.rco.testsupport.Syncs.sync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.FilterRule;
import eu.derfniw.rco.api.v1alpha1.SyncOptions;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SyncCommandTest {

    private static final List<String> BASE =
            List.of("sync", "source:", "destination:", "--config=/etc/rclone/rclone.conf", "--cache-dir=/tmp/rclone");

    @Test
    void syncsTheSourceToTheDestination() {
        var spec = sync(s -> {
                    s.getSource().setPath("photos");
                    s.getDestination().setPath("backup/photos");
                })
                .getSpec();

        assertThat(SyncCommand.args(spec))
                .containsExactly(
                        "sync",
                        "source:photos",
                        "destination:backup/photos",
                        "--config=/etc/rclone/rclone.conf",
                        "--cache-dir=/tmp/rclone");
    }

    static Stream<Arguments> optionCases() {
        return Stream.of(
                argumentSet("none", (Consumer<SyncOptions>) o -> {}, List.of()),
                argumentSet("dry run", (Consumer<SyncOptions>) o -> o.setDryRun(true), List.of("--dry-run")),
                argumentSet("no dry run", (Consumer<SyncOptions>) o -> o.setDryRun(false), List.of()),
                argumentSet("transfers", (Consumer<SyncOptions>) o -> o.setTransfers(8), List.of("--transfers=8")),
                argumentSet("checkers", (Consumer<SyncOptions>) o -> o.setCheckers(16), List.of("--checkers=16")),
                argumentSet(
                        "delete before",
                        (Consumer<SyncOptions>) o -> o.setDeleteMode(SyncOptions.DeleteMode.BEFORE),
                        List.of("--delete-before")),
                argumentSet(
                        "delete during",
                        (Consumer<SyncOptions>) o -> o.setDeleteMode(SyncOptions.DeleteMode.DURING),
                        List.of("--delete-during")),
                argumentSet(
                        "delete after",
                        (Consumer<SyncOptions>) o -> o.setDeleteMode(SyncOptions.DeleteMode.AFTER),
                        List.of("--delete-after")),
                argumentSet(
                        "filters, in order",
                        (Consumer<SyncOptions>) o -> o.setFilters(List.of(
                                new FilterRule(FilterRule.Action.INCLUDE, "/photos/**"),
                                new FilterRule(FilterRule.Action.EXCLUDE, "*"))),
                        List.of("--filter=+ /photos/**", "--filter=- *")));
    }

    @ParameterizedTest
    @MethodSource("optionCases")
    void optionsBecomeFlags(Consumer<SyncOptions> change, List<String> flags) {
        var options = new SyncOptions();
        change.accept(options);
        var spec = sync(s -> s.setOptions(options)).getSpec();

        assertThat(SyncCommand.args(spec))
                .startsWith(BASE.toArray(String[]::new))
                .endsWith(flags.toArray(String[]::new))
                .hasSize(BASE.size() + flags.size());
    }
}
