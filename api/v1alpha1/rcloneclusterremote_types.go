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
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
)

// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:resource:scope=Cluster

// RCloneClusterRemote is the Schema for the rcloneclusterremotes API
type RCloneClusterRemote struct {
	metav1.TypeMeta `json:",inline"`

	// metadata is a standard object metadata
	// +optional
	metav1.ObjectMeta `json:"metadata,omitzero"`

	// spec defines the desired state of RCloneClusterRemote
	// +required
	Spec RCloneRemoteSpec `json:"spec"`

	// status defines the observed state of RCloneClusterRemote
	// +optional
	Status RCloneRemoteStatus `json:"status,omitzero"`
}

func (r *RCloneClusterRemote) GetRCloneRemoteSpec() *RCloneRemoteSpec {
	return &r.Spec
}
func (r *RCloneClusterRemote) GetRCloneRemoteStatus() *RCloneRemoteStatus {
	return &r.Status
}

// +kubebuilder:object:root=true

// RCloneClusterRemoteList contains a list of RCloneClusterRemote
type RCloneClusterRemoteList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitzero"`
	Items           []RCloneClusterRemote `json:"items"`
}

func init() {
	SchemeBuilder.Register(func(s *runtime.Scheme) error {
		s.AddKnownTypes(SchemeGroupVersion, &RCloneClusterRemote{}, &RCloneClusterRemoteList{})
		return nil
	})
}
