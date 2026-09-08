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
	rcov1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	"github.com/jdijt/rclone-operator/internal/remote"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime/schema"
)

// validateRCloneRemoteInstance wraps the validator to return a webhook compatible response.
func validateRCloneRemoteInstance(inst rcov1alpha1.RCloneRemoteInstance, gk schema.GroupKind) error {
	if errs := remote.ValidateRCloneRemoteSpec(inst.GetRCloneRemoteSpec()); len(errs) > 0 {
		return apierrors.NewInvalid(gk, inst.GetName(), errs)
	}
	return nil
}
