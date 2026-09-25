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
package eu.derfniw.rco.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import eu.derfniw.rco.api.v1alpha1.BackendType;
import eu.derfniw.rco.api.v1alpha1.CryptBackend;
import eu.derfniw.rco.api.v1alpha1.RCloneRemote;
import eu.derfniw.rco.api.v1alpha1.RCloneRemoteSpec;
import eu.derfniw.rco.api.v1alpha1.S3Backend;
import eu.derfniw.rco.api.v1alpha1.SecretKeyRef;
import eu.derfniw.rco.api.v1alpha1.SftpBackend;
import eu.derfniw.rco.api.v1alpha1.TemplateBackend;
import eu.derfniw.rco.testsupport.KubeApiServerResource;
import eu.derfniw.rco.validation.FieldError;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@WithTestResource(KubeApiServerResource.class)
class RemoteValidatorTest {

    private static final String TEMPLATE_FIELD = "spec.template.template";
    private static final Map<String, SecretKeyRef> USED_INPUT = Map.of("used", new SecretKeyRef("completely", "fake"));

    @Inject
    RemoteValidator validator;

    /**
     * Expected error: compared on field, type and offending value, and the detail must contain {@code detailSubstring}.
     */
    record Expected(String field, FieldError.Type type, Object value, String detailSubstring) {}

    static Expected required(String field, String detail) {
        return new Expected(field, FieldError.Type.REQUIRED, null, detail);
    }

    static Expected invalid(String value, String detail) {
        return new Expected(TEMPLATE_FIELD, FieldError.Type.INVALID, value, detail);
    }

    static Stream<Arguments> templateCases() {
        return Stream.of(
                argumentSet("no references", new TemplateBackend("A template with no references", null), List.of()),
                argumentSet(
                        "unused input",
                        new TemplateBackend(
                                "A template with no references, unused keys are fine.",
                                Map.of("not-used", new SecretKeyRef("completely", "fake"))),
                        List.of()),
                argumentSet(
                        "declared reference",
                        new TemplateBackend("a template that has a ${used} key.", USED_INPUT),
                        List.of()),
                argumentSet(
                        "undeclared reference",
                        new TemplateBackend("a template referencing a ${undeclared} key", null),
                        List.of(invalid("${undeclared}", "undeclared field undeclared"))),
                argumentSet(
                        "empty template",
                        new TemplateBackend("", null),
                        List.of(required(TEMPLATE_FIELD, "specified and not blank"))),
                argumentSet(
                        "whitespace template",
                        new TemplateBackend("    ", null),
                        List.of(required(TEMPLATE_FIELD, "specified and not blank"))),
                argumentSet(
                        "null template",
                        new TemplateBackend(null, null),
                        List.of(required(TEMPLATE_FIELD, "specified and not blank"))),
                argumentSet(
                        "nested name", new TemplateBackend("${a.b}", null), List.of(invalid("${a.b}", "malformed"))),
                argumentSet(
                        "empty placeholder", new TemplateBackend("x=${}", null), List.of(invalid("${}", "malformed"))),
                argumentSet(
                        "whitespace inside placeholder",
                        new TemplateBackend("${ used }", USED_INPUT),
                        List.of(invalid("${ used }", "malformed"))),
                argumentSet(
                        "name starting with a digit",
                        new TemplateBackend("${1used}", null),
                        List.of(invalid("${1used}", "malformed"))),
                argumentSet(
                        "unterminated placeholder",
                        new TemplateBackend("an unterminated ${used", USED_INPUT),
                        List.of(invalid("${used", "malformed placeholder at offset 16"))),
                argumentSet(
                        "two undeclared references",
                        new TemplateBackend("${one} and ${two}", null),
                        List.of(invalid("${one}", "undeclared field one"), invalid("${two}", "undeclared field two"))),
                argumentSet(
                        "valid reference beside a malformed one",
                        new TemplateBackend("host=${used},pass=${used.x}", USED_INPUT),
                        List.of(invalid("${used.x}", "malformed"))),
                argumentSet(
                        "dollar without brace is literal",
                        new TemplateBackend("price=$5,name=$used,end=$", null),
                        List.of()),
                argumentSet(
                        "underscores and digits in names",
                        new TemplateBackend("${_a1}", Map.of("_a1", new SecretKeyRef("s", "k"))),
                        List.of()),
                // A valid template as a YAML block scalar would deliver it: content intact, newlines around it.
                argumentSet(
                        "block scalar whitespace",
                        new TemplateBackend("\nhost=${used},user=${used}\n", USED_INPUT),
                        List.of()));
    }

    @ParameterizedTest
    @MethodSource("templateCases")
    void templateBackend(TemplateBackend in, List<Expected> expected) {
        assertMatches(validate(with(spec(BackendType.TEMPLATE), s -> s.setTemplate(in))), expected);
    }

    static Stream<Arguments> specCases() {
        return Stream.of(
                argumentSet(
                        "crypt undefined", spec(BackendType.CRYPT), List.of(required("spec.crypt", "type is crypt"))),
                argumentSet("s3 undefined", spec(BackendType.S3), List.of(required("spec.s3", "type is s3"))),
                argumentSet("sftp undefined", spec(BackendType.SFTP), List.of(required("spec.sftp", "type is sftp"))),
                argumentSet(
                        "template undefined",
                        spec(BackendType.TEMPLATE),
                        List.of(required("spec.template", "type is template"))),
                // Nothing beyond the presence check runs for these three variants, so empty ones suffice.
                argumentSet(
                        "crypt defined", with(spec(BackendType.CRYPT), s -> s.setCrypt(new CryptBackend())), List.of()),
                argumentSet("s3 defined", with(spec(BackendType.S3), s -> s.setS3(new S3Backend())), List.of()),
                argumentSet("sftp defined", with(spec(BackendType.SFTP), s -> s.setSftp(new SftpBackend())), List.of()),
                argumentSet(
                        "template defined",
                        with(
                                spec(BackendType.TEMPLATE),
                                s -> s.setTemplate(new TemplateBackend("host=${used}", USED_INPUT))),
                        List.of()),
                // The template variant is the only one that delegates; its errors must keep the nested path.
                argumentSet(
                        "template errors propagate",
                        with(
                                spec(BackendType.TEMPLATE),
                                s -> s.setTemplate(new TemplateBackend("host=${missing}", null))),
                        List.of(invalid("${missing}", "undeclared field missing"))),
                // Only the selected variant is consulted: a populated non-selected variant neither satisfies the
                // selected one nor reports on its own.
                argumentSet(
                        "crypt selected while s3 populated",
                        with(spec(BackendType.CRYPT), s -> s.setS3(new S3Backend())),
                        List.of(required("spec.crypt", "type is crypt"))),
                argumentSet(
                        "missing type",
                        new RCloneRemoteSpec(),
                        List.of(new Expected(
                                "spec.type", FieldError.Type.NOT_SUPPORTED, null, "sftp, s3, crypt, template"))));
    }

    @ParameterizedTest
    @MethodSource("specCases")
    void spec(RCloneRemoteSpec in, List<Expected> expected) {
        assertMatches(validate(in), expected);
    }

    /** The message on a missing type is a literal, so it must be kept in step with the enum. */
    @Test
    void missingTypeListsAllBackendTypes() {
        var supported =
                Arrays.stream(BackendType.values()).map(BackendType::value).collect(Collectors.joining(", "));
        assertThat(validate(new RCloneRemoteSpec())).singleElement().satisfies(e -> assertThat(e.detail())
                .isEqualTo("supported values: " + supported));
    }

    private List<FieldError> validate(RCloneRemoteSpec spec) {
        var remote = new RCloneRemote();
        remote.setSpec(spec);
        return validator.validate(remote);
    }

    private static RCloneRemoteSpec spec(BackendType type) {
        var spec = new RCloneRemoteSpec();
        spec.setType(type);
        return spec;
    }

    private static RCloneRemoteSpec with(RCloneRemoteSpec spec, Consumer<RCloneRemoteSpec> change) {
        change.accept(spec);
        return spec;
    }

    private static void assertMatches(List<FieldError> actual, List<Expected> expected) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            assertThat(got.field()).as("field of %s", got).isEqualTo(want.field());
            assertThat(got.type()).as("type of %s", got).isEqualTo(want.type());
            assertThat(got.value()).as("value of %s", got).isEqualTo(want.value());
            assertThat(got.detail()).as("detail of %s", got).contains(want.detailSubstring());
        }
    }
}
