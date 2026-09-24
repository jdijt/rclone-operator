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

import io.fabric8.kubernetes.api.model.Condition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Helpers for status conditions, following apimachinery's {@code meta.SetStatusCondition}. */
final class Conditions {

    private Conditions() {}

    /**
     * Sets {@code wanted} in {@code conditions}, replacing the condition of the same type. The last transition time
     * is only updated when the status changes (or the condition is new).
     *
     * @return whether {@code conditions} changed
     */
    static boolean set(List<Condition> conditions, Condition wanted) {
        var existing = conditions.stream()
                .filter(c -> c.getType().equals(wanted.getType()))
                .findFirst();
        if (existing.isEmpty()) {
            wanted.setLastTransitionTime(now());
            conditions.add(wanted);
            return true;
        }

        var current = existing.get();
        boolean changed = false;
        if (!Objects.equals(current.getStatus(), wanted.getStatus())) {
            current.setStatus(wanted.getStatus());
            current.setLastTransitionTime(now());
            changed = true;
        }
        if (!Objects.equals(current.getReason(), wanted.getReason())) {
            current.setReason(wanted.getReason());
            changed = true;
        }
        if (!Objects.equals(current.getMessage(), wanted.getMessage())) {
            current.setMessage(wanted.getMessage());
            changed = true;
        }
        if (!Objects.equals(current.getObservedGeneration(), wanted.getObservedGeneration())) {
            current.setObservedGeneration(wanted.getObservedGeneration());
            changed = true;
        }
        return changed;
    }

    // The API server stores condition timestamps with second precision.
    private static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
