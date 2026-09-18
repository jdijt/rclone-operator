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
	"context"
	"fmt"
	"text/template"
	"text/template/parse"

	rcov1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	"k8s.io/apimachinery/pkg/util/validation/field"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

func ValidateReferences(ctx *context.Context, client client.Client, ns string, spec rcov1alpha1.RCloneRemoteSpec) field.ErrorList {
	var secretRefs []*rcov1alpha1.SecretKeyRef
	switch spec.Type {
	case rcov1alpha1.RCBackendTypeCrypt:
		b := spec.Crypt
		secretRefs = append(secretRefs, &b.PasswordRef)
		if spec.Crypt.SaltRef != nil {
			secretRefs = append(secretRefs, b.SaltRef)
		}
	case rcov1alpha1.RCBackendTypeS3:
		b := spec.S3
		secretRefs = append(secretRefs, &b.AccessKeyIDRef, &b.SecretAccessKeyRef)
	case rcov1alpha1.RCBackendTypeSFTP:
		b := spec.SFTP
		if b.PasswordRef != nil {
			secretRefs = append(secretRefs, b.PasswordRef)
		}
		if b.PrivateKeyPassphraseRef != nil {
			secretRefs = append(secretRefs, b.PrivateKeyPassphraseRef)
		}
		if b.PrivateKeyRef != nil {
			secretRefs = append(secretRefs, b.PrivateKeyRef)
		}
	case rcov1alpha1.RCBackendTypeTemplate:
		b := spec.Template
		for _, ref := range b.Inputs {
			secretRefs = append(secretRefs, &ref)
		}
	}

}

// ValidateRCloneRemoteSpec statically validates an rclone remote,
// where this is not already handled by the APIServer via CEL rules.
func ValidateRCloneRemoteSpec(spec *rcov1alpha1.RCloneRemoteSpec) field.ErrorList {
	path := field.NewPath("spec")
	var allErrs field.ErrorList

	// Note only the specified type is validated, as content on fields other than the selected type is ignored.
	// Note also that CEL rules on the object should prevent this from being the case in the first place.
	switch spec.Type {
	case rcov1alpha1.RCBackendTypeCrypt:
		p := path.Child("crypt")
		if spec.Crypt == nil {
			allErrs = append(allErrs, missingVariantSpec(rcov1alpha1.RCBackendTypeCrypt, p))
		}
	case rcov1alpha1.RCBackendTypeS3:
		p := path.Child("s3")
		if spec.S3 == nil {
			allErrs = append(allErrs, missingVariantSpec(rcov1alpha1.RCBackendTypeS3, p))
		}
	case rcov1alpha1.RCBackendTypeSFTP:
		p := path.Child("sftp")
		if spec.SFTP == nil {
			allErrs = append(allErrs, missingVariantSpec(rcov1alpha1.RCBackendTypeSFTP, p))
		}
	case rcov1alpha1.RCBackendTypeTemplate:
		p := path.Child("template")
		if spec.Template == nil {
			allErrs = append(allErrs, missingVariantSpec(rcov1alpha1.RCBackendTypeTemplate, p))
		} else {
			allErrs = append(allErrs, validateTemplateBackend(spec.Template, p)...)
		}
	default:
		allErrs = append(
			allErrs, field.NotSupported(path.Child("type"), spec.Type, rcov1alpha1.SupportedRCBackendTypes))
	}

	return allErrs
}

// validateTemplateBackend validates if the template makes sense and does not contain any forbidden constructs.
func validateTemplateBackend(spec *rcov1alpha1.TemplateBackend, path *field.Path) field.ErrorList {
	var allErrs field.ErrorList

	normalizedTemplate := normalizeTemplate(spec.Template)
	if len(normalizedTemplate) == 0 {
		return append(allErrs, field.Required(path.Child("template"), "must be specified and not blank"))
	}

	t, err := template.New("template").Parse(normalizedTemplate)
	if err != nil {
		return append(allErrs, field.Invalid(
			path.Child("template"),
			spec.Template,
			fmt.Sprintf("error parsing template: %s", err.Error()),
		))
	}

	// This can be, for example, due to the template being one big comment.
	if len(t.Root.Nodes) == 0 {
		return append(allErrs, field.Invalid(path.Child("template"), normalizedTemplate, "must produce output (not only a comment, blank, etc..)"))
	}

	allErrs = append(allErrs, validateTemplateReferences(t.Root, spec.Inputs, t.Tree, path.Child("template"))...)

	return allErrs
}

// validateTemplateReferences validates that the referenced fields in the template are actually declared.
// It also limits the kind of templates we accept, essentially we only want to support simple field inserts
// i.e.: {{ .field }}.
//
// This is implemented on an allow-list principle.
// Only "ListNode", "TextNode" and a single specific shape of "ActionNode" are allowed.
func validateTemplateReferences(node parse.Node, declaredFields map[string]rcov1alpha1.SecretKeyRef, tree *parse.Tree, p *field.Path) field.ErrorList {
	switch node := node.(type) {
	case *parse.ListNode:
		//nolint:prealloc  // usually empty.
		var allErrs field.ErrorList
		for _, child := range node.Nodes {
			allErrs = append(allErrs, validateTemplateReferences(child, declaredFields, tree, p)...)
		}
		return allErrs
	case *parse.TextNode:
		return nil
	case *parse.ActionNode:
		// The single valid construction "{{ .field }}" looks as follows:
		// ActionNode -> PipeNode.Cmds (len 1) -> CommandNode.args (len 1) -> FieldNode.Ident (len 1)
		pipeNode := node.Pipe
		if pipeNode.IsAssign || len(pipeNode.Decl) > 0 || len(pipeNode.Cmds) != 1 {
			return unsupportedTemplateConstruct(node, tree, p)
		}
		cmdNode := pipeNode.Cmds[0]
		if len(cmdNode.Args) != 1 {
			return unsupportedTemplateConstruct(node, tree, p)
		}
		fieldNode, ok := cmdNode.Args[0].(*parse.FieldNode)
		if !ok {
			return unsupportedTemplateConstruct(node, tree, p)
		}
		if len(fieldNode.Ident) != 1 {
			return unsupportedTemplateConstruct(node, tree, p)
		}
		if _, fieldExists := declaredFields[fieldNode.Ident[0]]; !fieldExists {
			return field.ErrorList{field.Invalid(
				p, node.String(),
				fmt.Sprintf("reference to undeclared field %s", fieldNode.Ident[0]),
			)}
		}
		return nil
	default:
		return unsupportedTemplateConstruct(node, tree, p)
	}
}

func unsupportedTemplateConstruct(n parse.Node, tree *parse.Tree, p *field.Path) field.ErrorList {
	location, _ := tree.ErrorContext(n)
	return field.ErrorList{field.Invalid(p, n.String(), fmt.Sprintf("unsupported template construct at %s", location))}
}

func missingVariantSpec(t rcov1alpha1.RCBackendType, p *field.Path) *field.Error {
	return field.Required(p, fmt.Sprintf("must be specified when type is %s", t))
}
