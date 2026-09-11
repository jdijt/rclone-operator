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

package controller

import (
	"testing"

	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	logf "sigs.k8s.io/controller-runtime/pkg/log"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
)

func TestRCloneRemoteReconciler_Reconcile(t *testing.T) {

}

// Just the cluster-wide specific cases
func TestRCloneRemoteReconciler_Reconcile_clusterwide(t *testing.T) {
	log := logf.FromContext(t.Context())
	tests := []struct {
		name     string
		object   *rcofrozenbitssev1alpha1.RCloneRemote
		expected interface{} // ???
	}{
		{
			name:     "Valid Record",
			object:   nil,
			expected: nil,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			ns := &v1.Namespace{ObjectMeta: metav1.ObjectMeta{GenerateName: "rcloneremote-test-"}}
			if err := k8sClient.Create(t.Context(), ns); err != nil {
				t.Fatal("Cannot setup namespace for testcase")
			}
			test.object.Namespace = ns.Name
			k8sClient.Create(t.Context(), test.object)

			// No need to clean up, we generate unique namespace names
			// And after testing the api server is cleared.
		})
	}
}
