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

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.ANNOTATIONS;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.validation.Payload;

/** The package layering, on production classes only. */
@AnalyzeClasses(packages = "eu.derfniw.rco", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Controllers and webhooks are entry points, called only by the framework. The logic packages serve them. The
     * model may use the logic packages' constraint annotations and their payloads (the error types): using an
     * annotation isn't calling the code behind it.
     */
    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Model")
            .definedBy("..api.v1alpha1..")
            .layer("Logic")
            .definedBy("..sync..", "..remote..", "..validation..")
            .layer("Entry")
            .definedBy("..controller..", "..webhook..")
            .whereLayer("Entry")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Logic")
            .mayOnlyBeAccessedByLayers("Entry")
            .ignoreDependency(resideInAPackage("..api.v1alpha1.."), ANNOTATIONS)
            .ignoreDependency(resideInAPackage("..api.v1alpha1.."), assignableTo(Payload.class));
}
