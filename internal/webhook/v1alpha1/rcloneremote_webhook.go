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

package v1alpha1

import (
	"context"

	ctrl "sigs.k8s.io/controller-runtime"
	logf "sigs.k8s.io/controller-runtime/pkg/log"
	"sigs.k8s.io/controller-runtime/pkg/webhook/admission"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
)

// SetupRCloneRemoteWebhookWithManager registers the webhook for RCloneRemote in the manager.
func SetupRCloneRemoteWebhookWithManager(mgr ctrl.Manager) error {
	return ctrl.NewWebhookManagedBy(mgr, &rcofrozenbitssev1alpha1.RCloneRemote{}).
		WithValidator(&RCloneRemoteCustomValidator{}).
		Complete()
}

// NOTE: If you want to customise the 'path', use the flags '--defaulting-path' or '--validation-path'.
// +kubebuilder:webhook:path=/validate-rco-frozenbits-se-v1alpha1-rcloneremote,mutating=false,failurePolicy=fail,sideEffects=None,groups=rco.frozenbits.se,resources=rcloneremotes,verbs=create;update,versions=v1alpha1,name=vrcloneremote-v1alpha1.kb.io,admissionReviewVersions=v1

// RCloneRemoteCustomValidator struct is responsible for validating the RCloneRemote resource
// when it is created, updated, or deleted.
//
// NOTE: The +kubebuilder:object:generate=false marker prevents controller-gen from generating DeepCopy methods,
// as this struct is used only for temporary operations and does not need to be deeply copied.
type RCloneRemoteCustomValidator struct {
	// TODO(user): Add more fields as needed for validation
}

// ValidateCreate implements webhook.CustomValidator so a webhook will be registered for the type RCloneRemote.
func (v *RCloneRemoteCustomValidator) ValidateCreate(ctx context.Context, obj *rcofrozenbitssev1alpha1.RCloneRemote) (admission.Warnings, error) {
	logf.FromContext(ctx).Info("Validation for RCloneRemote upon creation")
	return nil, validateRCloneRemoteInstance(obj)
}

// ValidateUpdate implements webhook.CustomValidator so a webhook will be registered for the type RCloneRemote.
func (v *RCloneRemoteCustomValidator) ValidateUpdate(ctx context.Context, _, newObj *rcofrozenbitssev1alpha1.RCloneRemote) (admission.Warnings, error) {
	logf.FromContext(ctx).Info("Validation for RCloneRemote upon update")
	return nil, validateRCloneRemoteInstance(newObj)
}

// ValidateDelete implements webhook.CustomValidator so a webhook will be registered for the type RCloneRemote.
// No-op
func (v *RCloneRemoteCustomValidator) ValidateDelete(ctx context.Context, _ *rcofrozenbitssev1alpha1.RCloneRemote) (admission.Warnings, error) {
	logf.FromContext(ctx).Info("Validation for RCloneRemote upon deletion")
	return nil, nil
}
