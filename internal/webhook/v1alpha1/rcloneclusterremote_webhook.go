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

// nolint:unused
// log is for logging in this package.
var rcloneclusterremotelog = logf.Log.WithName("rcloneclusterremote-resource")

// SetupRCloneClusterRemoteWebhookWithManager registers the webhook for RCloneClusterRemote in the manager.
func SetupRCloneClusterRemoteWebhookWithManager(mgr ctrl.Manager) error {
	return ctrl.NewWebhookManagedBy(mgr, &rcofrozenbitssev1alpha1.RCloneClusterRemote{}).
		WithValidator(&RCloneClusterRemoteCustomValidator{}).
		Complete()
}

// NOTE: If you want to customise the 'path', use the flags '--defaulting-path' or '--validation-path'.
// +kubebuilder:webhook:path=/validate-rco-frozenbits-se-v1alpha1-rcloneclusterremote,mutating=false,failurePolicy=fail,sideEffects=None,groups=rco.frozenbits.se,resources=rcloneclusterremotes,verbs=create;update,versions=v1alpha1,name=vrcloneclusterremote-v1alpha1.kb.io,admissionReviewVersions=v1

// RCloneClusterRemoteCustomValidator struct is responsible for validating the RCloneClusterRemote resource
// when it is created, updated, or deleted.
//
// NOTE: The +kubebuilder:object:generate=false marker prevents controller-gen from generating DeepCopy methods,
// as this struct is used only for temporary operations and does not need to be deeply copied.
type RCloneClusterRemoteCustomValidator struct {
	// TODO(user): Add more fields as needed for validation
}

// ValidateCreate implements webhook.CustomValidator so a webhook will be registered for the type RCloneClusterRemote.
func (v *RCloneClusterRemoteCustomValidator) ValidateCreate(_ context.Context, obj *rcofrozenbitssev1alpha1.RCloneClusterRemote) (admission.Warnings, error) {
	rcloneclusterremotelog.Info("Validation for RCloneClusterRemote upon creation", "name", obj.GetName())

	return nil, validateRCloneRemoteInstance(obj, obj.GroupVersionKind().GroupKind())
}

// ValidateUpdate implements webhook.CustomValidator so a webhook will be registered for the type RCloneClusterRemote.
func (v *RCloneClusterRemoteCustomValidator) ValidateUpdate(_ context.Context, _, newObj *rcofrozenbitssev1alpha1.RCloneClusterRemote) (admission.Warnings, error) {
	rcloneclusterremotelog.Info("Validation for RCloneClusterRemote upon update", "name", newObj.GetName())

	return nil, validateRCloneRemoteInstance(newObj, newObj.GroupVersionKind().GroupKind())
}

// ValidateDelete implements webhook.CustomValidator so a webhook will be registered for the type RCloneClusterRemote.
// No-op
func (v *RCloneClusterRemoteCustomValidator) ValidateDelete(_ context.Context, obj *rcofrozenbitssev1alpha1.RCloneClusterRemote) (admission.Warnings, error) {
	rcloneclusterremotelog.Info("Validation for RCloneClusterRemote upon deletion", "name", obj.GetName())
	return nil, nil
}
