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
	"context"
	"time"

	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	logf "sigs.k8s.io/controller-runtime/pkg/log"

	rcov1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
)

// RCloneRemoteReconciler reconciles a RCloneRemote (Either cluster or namespace scoped) object
type RCloneRemoteReconciler struct {
	Client               client.Client
	APIReader            client.Reader
	OperatorNamespace    string
	RevalidationInterval time.Duration

	newObject func() rcov1alpha1.RCloneRemoteInstance
}

func NewRCloneRemoteReconciler(mgrClient client.Client, reader client.Reader, operatorNamespace string, revalidationInterval time.Duration) *RCloneRemoteReconciler {
	return &RCloneRemoteReconciler{
		Client: mgrClient, APIReader: reader, OperatorNamespace: operatorNamespace, RevalidationInterval: revalidationInterval,
		newObject: func() rcov1alpha1.RCloneRemoteInstance { return &rcov1alpha1.RCloneRemote{} },
	}
}

func NewRCloneClusterRemoteReconciler(mgrClient client.Client, reader client.Reader, operatorNamespace string, revalidationInterval time.Duration) *RCloneRemoteReconciler {
	return &RCloneRemoteReconciler{
		Client: mgrClient, APIReader: reader, OperatorNamespace: operatorNamespace, RevalidationInterval: revalidationInterval,
		newObject: func() rcov1alpha1.RCloneRemoteInstance { return &rcov1alpha1.RCloneClusterRemote{} },
	}
}

// +kubebuilder:rbac:groups=rco.frozenbits.se,resources=rcloneremotes,verbs=get;list;watch
// +kubebuilder:rbac:groups=rco.frozenbits.se,resources=rcloneremotes/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=rco.frozenbits.se,resources=rcloneclusterremotes,verbs=get;list;watch
// +kubebuilder:rbac:groups=rco.frozenbits.se,resources=rcloneclusterremotes/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=core,resources=secrets,verbs=get

func (r *RCloneRemoteReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	_ = logf.FromContext(ctx)

	_ = r.newObject()

	// TODO(user): your logic here

	return ctrl.Result{}, nil
}

// SetupWithManager sets up the controller with the Manager.
func (r *RCloneRemoteReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(r.newObject()).
		Complete(r)
}
