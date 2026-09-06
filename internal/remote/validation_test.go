/*
Copyright 2026.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package remote

import (
	"testing"

	"github.com/jdijt/rclone-operator/api/v1alpha1"
	"k8s.io/apimachinery/pkg/util/validation/field"
)

var fakeSecretKeyRef = v1alpha1.SecretKeyRef{Name: "completely", Key: "fake"}
var templatePath = field.NewPath("spec", "template")
var usedInput = map[string]v1alpha1.SecretKeyRef{"used": fakeSecretKeyRef}
var templateTests = []struct {
	name           string
	inputs         map[string]v1alpha1.SecretKeyRef
	template       string
	expectedErrors field.ErrorList
}{
	{
		"no references",
		nil, "A template with no references",
		field.ErrorList{},
	},
	{
		"unused input",
		map[string]v1alpha1.SecretKeyRef{
			"not-used": fakeSecretKeyRef,
		},
		"A template with no references, unused keys are fine.",
		field.ErrorList{},
	},
	{
		"declared reference",
		usedInput,
		"a template that has a {{ .used }} key.",
		field.ErrorList{},
	},
	{
		"undeclared reference",
		nil,
		"a template referencing a {{ .undeclared }} key",
		field.ErrorList{field.Invalid(templatePath, "{{.undeclared}}", "undeclared")},
	},
	{
		"empty template",
		nil, "",
		field.ErrorList{field.Required(templatePath, "specified and not blank")},
	},
	{
		"whitespace template",
		nil, "    ",
		field.ErrorList{field.Required(templatePath, "specified and not blank")},
	},
	// comments emit no node at all, and a 100% comment template should be considered "empty" and invalid.
	{
		"comment only",
		nil, "{{/* just a comment */}}  ",
		field.ErrorList{field.Invalid(templatePath, "{{/* just a comment */}}", "must produce output")},
	},
	// CLAUDE GENERATED -> Invalid constructs
	{
		"nested field ident",
		nil, "{{ .a.b }}",
		field.ErrorList{field.Invalid(templatePath, "{{.a.b}}", "unsupported")},
	},
	{
		"bare dot",
		nil, "{{ . }}",
		field.ErrorList{field.Invalid(templatePath, "{{.}}", "unsupported")},
	},
	{
		"bare root variable",
		nil, "{{ $ }}",
		field.ErrorList{field.Invalid(templatePath, "{{$}}", "unsupported")},
	},
	{
		"string literal",
		nil, `{{ "literal" }}`,
		field.ErrorList{field.Invalid(templatePath, `{{"literal"}}`, "unsupported")},
	},
	{
		"number literal",
		nil, "{{ 42 }}",
		field.ErrorList{field.Invalid(templatePath, "{{42}}", "unsupported")},
	},
	{
		"variable declaration",
		nil, "{{ $x := .a }}",
		field.ErrorList{field.Invalid(templatePath, "{{$x := .a}}", "unsupported")},
	},
	{
		"pipeline with multiple commands",
		usedInput,
		`{{ .used | printf "%s" }}`,
		field.ErrorList{field.Invalid(templatePath, `{{.used | printf "%s"}}`, "unsupported")},
	},
	{
		"command with multiple arguments",
		usedInput,
		`{{ printf "%s" .used }}`,
		field.ErrorList{field.Invalid(templatePath, `{{printf "%s" .used}}`, "unsupported")},
	},
	{
		"if branch",
		usedInput,
		"{{ if .used }}x{{ end }}",
		field.ErrorList{field.Invalid(templatePath, "{{if .used}}x{{end}}", "unsupported")},
	},
	{
		"range branch",
		usedInput,
		"{{ range .used }}x{{ end }}",
		field.ErrorList{field.Invalid(templatePath, "{{range .used}}x{{end}}", "unsupported")},
	},
	{
		"with branch",
		usedInput,
		"{{ with .used }}x{{ end }}",
		field.ErrorList{field.Invalid(templatePath, "{{with .used}}x{{end}}", "unsupported")},
	},
	{
		"template invocation",
		nil, `{{ template "x" }}`,
		field.ErrorList{field.Invalid(templatePath, `{{template "x"}}`, "unsupported")},
	},
	{
		"unparseable template",
		nil, "an unterminated action {{ .a }",
		field.ErrorList{field.Invalid(templatePath, "an unterminated action {{ .a }", "error parsing template")},
	},
	// Distinct idents: two identical errors would trip ErrorMatcher's duplicate check.
	{
		"two undeclared references",
		nil, "{{ .one }} and {{ .two }}",
		field.ErrorList{
			field.Invalid(templatePath, "{{.one}}", "undeclared"),
			field.Invalid(templatePath, "{{.two}}", "undeclared"),
		},
	},
	// A rejected construct alongside a valid reference: only the former is reported.
	{
		"valid reference beside a rejected construct",
		usedInput,
		"host={{ .used }},pass={{ if .used }}x{{ end }}",
		field.ErrorList{field.Invalid(templatePath, "{{if .used}}x{{end}}", "unsupported")},
	},
	// Expected-valid, kept here because they are about parser edge behaviour:
	{
		"trim markers",
		usedInput,
		"pre {{- .used -}} post",
		field.ErrorList{},
	},
	// A valid template as a YAML block scalar would deliver it: content intact,
	// newlines around it. The trim must not disturb this.
	{
		"block scalar whitespace",
		usedInput,
		"\nhost={{ .used }},user={{ .used }}\n",
		field.ErrorList{},
	},
	// END CLAUDE GENERATED
}

func TestTemplateSpec(t *testing.T) {
	for _, tt := range templateTests {
		t.Run(tt.name, func(t *testing.T) {
			testInstance := &v1alpha1.TemplateBackend{
				Template: tt.template,
				Inputs:   tt.inputs,
			}
			errors := validateTemplateBackend(testInstance, field.NewPath("spec"))

			field.ErrorMatcher{}.ByDetailSubstring().ByValue().ByField().ByType().Test(t, tt.expectedErrors, errors)
		})
	}
}

// Type is always set as this is CEL-enforced.
var validateRCloneRemoteSpecTests = []struct {
	name string
	in   *v1alpha1.RCloneRemoteSpec
	out  field.ErrorList
}{
	// Claude generated: All fields defined (positive), undefined (negative),
	// And a few completely invalid cases
	{
		"Crypt undefined", &v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeCrypt},
		field.ErrorList{field.Required(field.NewPath("spec", "crypt"), string(v1alpha1.RCBackendTypeCrypt))},
	},

	{
		"S3 undefined", &v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeS3},
		field.ErrorList{field.Required(field.NewPath("spec", "s3"), string(v1alpha1.RCBackendTypeS3))},
	},
	{
		"SFTP undefined", &v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeSFTP},
		field.ErrorList{field.Required(field.NewPath("spec", "sftp"), string(v1alpha1.RCBackendTypeSFTP))},
	},
	{
		"Template undefined", &v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeTemplate},
		field.ErrorList{field.Required(field.NewPath("spec", "template"), string(v1alpha1.RCBackendTypeTemplate))},
	},
	// The variants are empty structs: nothing beyond the nil check runs for these
	// three arms, so populating them would only pin fields no one reads.
	{
		"Crypt defined",
		&v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeCrypt, Crypt: &v1alpha1.CryptBackend{}},
		field.ErrorList{},
	},
	{
		"S3 defined",
		&v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeS3, S3: &v1alpha1.S3Backend{}},
		field.ErrorList{},
	},
	{
		"SFTP defined",
		&v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeSFTP, SFTP: &v1alpha1.SftpBackend{}},
		field.ErrorList{},
	},
	{
		"Template defined",
		&v1alpha1.RCloneRemoteSpec{
			Type:     v1alpha1.RCBackendTypeTemplate,
			Template: &v1alpha1.TemplateBackend{Template: "host={{ .used }}", Inputs: usedInput},
		},
		field.ErrorList{},
	},
	// The template arm is the only one that delegates; this pins that its errors
	// reach the caller with the nested path intact.
	{
		"Template errors propagate",
		&v1alpha1.RCloneRemoteSpec{
			Type:     v1alpha1.RCBackendTypeTemplate,
			Template: &v1alpha1.TemplateBackend{Template: "host={{ .missing }}"},
		},
		field.ErrorList{field.Invalid(
			field.NewPath("spec", "template", "template"), "{{.missing}}", "undeclared")},
	},
	// Only the selected variant is consulted: a populated non-selected variant
	// neither satisfies the selected arm nor reports on its own.
	{
		"Crypt selected while S3 populated",
		&v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeCrypt, S3: &v1alpha1.S3Backend{}},
		field.ErrorList{field.Required(field.NewPath("spec", "crypt"), string(v1alpha1.RCBackendTypeCrypt))},
	},
	{
		"Unknown type", &v1alpha1.RCloneRemoteSpec{Type: "ftp"},
		field.ErrorList{field.NotSupported(
			field.NewPath("spec", "type"), v1alpha1.RCBackendType("ftp"), v1alpha1.SupportedRCBackendTypes)},
	},
	{
		"Empty type", &v1alpha1.RCloneRemoteSpec{},
		field.ErrorList{field.NotSupported(
			field.NewPath("spec", "type"), v1alpha1.RCBackendType(""), v1alpha1.SupportedRCBackendTypes)},
	},

	// end claude generated.
}

func TestValidateRCloneRemoteSpec(t *testing.T) {
	for _, vt := range validateRCloneRemoteSpecTests {
		t.Run(vt.name, func(t *testing.T) {
			errors := ValidateRCloneRemoteSpec(vt.in)
			field.ErrorMatcher{}.ByDetailSubstring().ByType().ByField().Test(t, vt.out, errors)
		})
	}
}
