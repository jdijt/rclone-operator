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

// Common snippets
var fakeSecretKeyRef = v1alpha1.SecretKeyRef{Name: "completely", Key: "fake"}
var usedInput = map[string]v1alpha1.SecretKeyRef{"used": fakeSecretKeyRef}

func TestTemplateSpec(t *testing.T) {
	templatePath := field.NewPath("spec", "template")
	templateTests := []struct {
		name string
		in   *v1alpha1.TemplateBackend
		out  field.ErrorList
	}{
		{
			"no references",
			&v1alpha1.TemplateBackend{Template: "A template with no references"},
			field.ErrorList{},
		},
		{
			"unused input",
			&v1alpha1.TemplateBackend{
				Template: "A template with no references, unused keys are fine.",
				Inputs:   map[string]v1alpha1.SecretKeyRef{"not-used": fakeSecretKeyRef},
			},
			field.ErrorList{},
		},
		{
			"declared reference",
			&v1alpha1.TemplateBackend{Template: "a template that has a {{ .used }} key.", Inputs: usedInput},
			field.ErrorList{},
		},
		{
			"undeclared reference",
			&v1alpha1.TemplateBackend{Template: "a template referencing a {{ .undeclared }} key"},
			field.ErrorList{field.Invalid(templatePath, "{{.undeclared}}", "undeclared")},
		},
		{
			"empty template",
			&v1alpha1.TemplateBackend{Template: ""},
			field.ErrorList{field.Required(templatePath, "specified and not blank")},
		},
		{
			"whitespace template",
			&v1alpha1.TemplateBackend{Template: "    "},
			field.ErrorList{field.Required(templatePath, "specified and not blank")},
		},
		// comments emit no node at all, and a 100% comment template should be considered "empty" and invalid.
		{
			"comment only",
			&v1alpha1.TemplateBackend{Template: "{{/* just a comment */}}  "},
			field.ErrorList{field.Invalid(templatePath, "{{/* just a comment */}}", "must produce output")},
		},
		// CLAUDE GENERATED -> Invalid constructs
		{
			"nested field ident",
			&v1alpha1.TemplateBackend{Template: "{{ .a.b }}"},
			field.ErrorList{field.Invalid(templatePath, "{{.a.b}}", "unsupported")},
		},
		{
			"bare dot",
			&v1alpha1.TemplateBackend{Template: "{{ . }}"},
			field.ErrorList{field.Invalid(templatePath, "{{.}}", "unsupported")},
		},
		{
			"bare root variable",
			&v1alpha1.TemplateBackend{Template: "{{ $ }}"},
			field.ErrorList{field.Invalid(templatePath, "{{$}}", "unsupported")},
		},
		{
			"string literal",
			&v1alpha1.TemplateBackend{Template: `{{ "literal" }}`},
			field.ErrorList{field.Invalid(templatePath, `{{"literal"}}`, "unsupported")},
		},
		{
			"number literal",
			&v1alpha1.TemplateBackend{Template: "{{ 42 }}"},
			field.ErrorList{field.Invalid(templatePath, "{{42}}", "unsupported")},
		},
		{
			"variable declaration",
			&v1alpha1.TemplateBackend{Template: "{{ $x := .a }}"},
			field.ErrorList{field.Invalid(templatePath, "{{$x := .a}}", "unsupported")},
		},
		{
			"pipeline with multiple commands",
			&v1alpha1.TemplateBackend{Template: `{{ .used | printf "%s" }}`, Inputs: usedInput},
			field.ErrorList{field.Invalid(templatePath, `{{.used | printf "%s"}}`, "unsupported")},
		},
		{
			"command with multiple arguments",
			&v1alpha1.TemplateBackend{Template: `{{ printf "%s" .used }}`, Inputs: usedInput},
			field.ErrorList{field.Invalid(templatePath, `{{printf "%s" .used}}`, "unsupported")},
		},
		{
			"if branch",
			&v1alpha1.TemplateBackend{Template: "{{ if .used }}x{{ end }}", Inputs: usedInput},
			field.ErrorList{field.Invalid(templatePath, "{{if .used}}x{{end}}", "unsupported")},
		},
		{
			"range branch",
			&v1alpha1.TemplateBackend{Template: "{{ range .used }}x{{ end }}", Inputs: usedInput},
			field.ErrorList{field.Invalid(templatePath, "{{range .used}}x{{end}}", "unsupported")},
		},
		{
			"with branch",
			&v1alpha1.TemplateBackend{Template: "{{ with .used }}x{{ end }}", Inputs: usedInput},
			field.ErrorList{field.Invalid(templatePath, "{{with .used}}x{{end}}", "unsupported")},
		},
		{
			"template invocation",
			&v1alpha1.TemplateBackend{Template: `{{ template "x" }}`},
			field.ErrorList{field.Invalid(templatePath, `{{template "x"}}`, "unsupported")},
		},
		{
			"unparseable template",
			&v1alpha1.TemplateBackend{Template: "an unterminated action {{ .a }"},
			field.ErrorList{field.Invalid(templatePath, "an unterminated action {{ .a }", "error parsing template")},
		},
		// Distinct idents: two identical errors would trip ErrorMatcher's duplicate check.
		{
			"two undeclared references",
			&v1alpha1.TemplateBackend{Template: "{{ .one }} and {{ .two }}"},
			field.ErrorList{
				field.Invalid(templatePath, "{{.one}}", "undeclared"),
				field.Invalid(templatePath, "{{.two}}", "undeclared"),
			},
		},
		// A rejected construct alongside a valid reference: only the former is reported.
		{
			"valid reference beside a rejected construct",
			&v1alpha1.TemplateBackend{
				Template: "host={{ .used }},pass={{ if .used }}x{{ end }}",
				Inputs:   usedInput,
			},
			field.ErrorList{field.Invalid(templatePath, "{{if .used}}x{{end}}", "unsupported")},
		},
		// Expected-valid, kept here because they are about parser edge behaviour:
		{
			"trim markers",
			&v1alpha1.TemplateBackend{Template: "pre {{- .used -}} post", Inputs: usedInput},
			field.ErrorList{},
		},
		// A valid template as a YAML block scalar would deliver it: content intact,
		// newlines around it. The trim must not disturb this.
		{
			"block scalar whitespace",
			&v1alpha1.TemplateBackend{Template: "\nhost={{ .used }},user={{ .used }}\n", Inputs: usedInput},
			field.ErrorList{},
		},
		// END CLAUDE GENERATED
	}

	for _, tt := range templateTests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			errors := validateTemplateBackend(tt.in, field.NewPath("spec"))

			field.ErrorMatcher{}.ByDetailSubstring().ByValue().ByField().ByType().Test(t, tt.out, errors)
		})
	}
}

func TestValidateRCloneRemoteSpec(t *testing.T) {
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
			&v1alpha1.RCloneRemoteSpec{Type: v1alpha1.RCBackendTypeSFTP, SFTP: &v1alpha1.SFTPBackend{}},
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

	for _, vt := range validateRCloneRemoteSpecTests {
		t.Run(vt.name, func(t *testing.T) {
			t.Parallel()
			errors := ValidateRCloneRemoteSpec(vt.in)
			field.ErrorMatcher{}.ByDetailSubstring().ByType().ByField().Test(t, vt.out, errors)
		})
	}
}
