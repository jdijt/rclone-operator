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

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.ANNOTATIONS;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.fabric8.kubernetes.client.CustomResource;
import jakarta.validation.Payload;

/** The package layering and where framework and Kubernetes types may be used, on production classes only. */
@AnalyzeClasses(packages = "eu.derfniw.rco", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Controllers and webhooks are entry points, called only by the framework, and the domain serves them. The model
     * may use the domain's constraint annotations and their payloads (the error types): using an annotation isn't
     * calling the code behind it. Every class must be in a layer, so a new package has to be placed here.
     */
    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .ensureAllClassesAreContainedInArchitecture()
            .layer("Config")
            .definedBy("eu.derfniw.rco")
            .layer("Model")
            .definedBy("eu.derfniw.rco.api..")
            .layer("Domain")
            .definedBy("eu.derfniw.rco.sync..", "eu.derfniw.rco.remote..", "eu.derfniw.rco.validation..")
            .layer("Entry")
            .definedBy("eu.derfniw.rco.controller..", "eu.derfniw.rco.webhook..")
            .whereLayer("Entry")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Entry")
            .ignoreDependency(resideInAPackage("eu.derfniw.rco.api.."), ANNOTATIONS)
            .ignoreDependency(resideInAPackage("eu.derfniw.rco.api.."), assignableTo(Payload.class));

    /** Only reconcilers deal with the operator framework. */
    @ArchTest
    static final ArchRule operatorFramework = noClasses()
            .that()
            .resideOutsideOfPackage("eu.derfniw.rco.controller..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.javaoperatorsdk..", "io.quarkiverse.operatorsdk..");

    /** Only the entry points talk to the API server; elsewhere CustomResource is the one client type allowed. */
    @ArchTest
    static final ArchRule kubernetesClient = noClasses()
            .that()
            .resideOutsideOfPackages("eu.derfniw.rco.controller..", "eu.derfniw.rco.webhook..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage("io.fabric8.kubernetes.client..").and(not(equivalentTo(CustomResource.class))));

    /** The Kubernetes API model belongs to the CRD model and the entry points, not the domain. */
    @ArchTest
    static final ArchRule kubernetesModel = noClasses()
            .that()
            .resideOutsideOfPackages("eu.derfniw.rco.api..", "eu.derfniw.rco.controller..", "eu.derfniw.rco.webhook..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.fabric8.kubernetes.api.model..");

    /** CRD generator annotations only describe the CRD model. */
    @ArchTest
    static final ArchRule crdAnnotations = noClasses()
            .that()
            .resideOutsideOfPackage("eu.derfniw.rco.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.fabric8.generator.annotation..",
                    "io.fabric8.crd.generator.annotation..",
                    "io.fabric8.kubernetes.model.annotation..");
}
