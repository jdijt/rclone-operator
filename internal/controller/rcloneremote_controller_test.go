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
	"time"

	. "github.com/onsi/gomega"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	controllerruntime "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

// AI-generated: the restructure into a case table, reconciler scopes and helpers.
// The case rows and the status-update test body are the author's, moved here unchanged
// where possible.

const testRevalidationInterval = 10 * time.Second

type statusUpdateCase struct {
	name          string
	in            rcofrozenbitssev1alpha1.RCloneRemoteSpec
	wantResult    controllerruntime.Result
	wantCondition metav1.Condition
}

// statusUpdateCases run against both RCloneRemote and RCloneClusterRemote.
var statusUpdateCases = []statusUpdateCase{
	{
		name: "Valid Record",
		in: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
			Type: "template",
			Template: &rcofrozenbitssev1alpha1.TemplateBackend{
				Template: "A valid template",
				Inputs:   nil,
			}},
		wantResult: controllerruntime.Result{
			RequeueAfter: testRevalidationInterval,
		},
		wantCondition: metav1.Condition{
			Type:   rcofrozenbitssev1alpha1.ReadyCondition,
			Status: metav1.ConditionTrue,
			Reason: rcofrozenbitssev1alpha1.ReasonValid,
		},
	},
	{
		name: "Invalid Template",
		in: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
			Type: "template",
			Template: &rcofrozenbitssev1alpha1.TemplateBackend{
				Template: "With a {{ .field }} that has no input",
				Inputs:   nil,
			},
		},
		wantResult: controllerruntime.Result{},
		wantCondition: metav1.Condition{
			Type:   rcofrozenbitssev1alpha1.ReadyCondition,
			Status: metav1.ConditionFalse,
			Reason: rcofrozenbitssev1alpha1.ReasonInvalid,
		},
	},
}

// createAndReconcile creates an object with the given spec, reconciles it once and returns it
// re-fetched, so its status and resourceVersion are current.
// AI-generated
func createAndReconcile(t *testing.T, g *WithT, scope reconcilerScope, spec rcofrozenbitssev1alpha1.RCloneRemoteSpec) rcofrozenbitssev1alpha1.RCloneRemoteInstance {
	t.Helper()
	obj := scope.newObject(t, g, spec)
	g.Expect(k8sClient.Create(t.Context(), obj)).To(Succeed())

	key := client.ObjectKeyFromObject(obj)
	_, err := scope.reconciler.Reconcile(t.Context(), controllerruntime.Request{NamespacedName: key})
	g.Expect(err).To(Succeed())
	g.Expect(k8sClient.Get(t.Context(), key, obj)).To(Succeed())
	return obj
}

// newTestObjectFunc returns an unsaved object with the given spec, creating any
// prerequisites (such as a namespace) it needs.
type newTestObjectFunc func(t *testing.T, g *WithT, spec rcofrozenbitssev1alpha1.RCloneRemoteSpec) rcofrozenbitssev1alpha1.RCloneRemoteInstance

// newTestRCloneRemote creates a unique namespace and returns an unsaved RCloneRemote in it.
func newTestRCloneRemote(t *testing.T, g *WithT, spec rcofrozenbitssev1alpha1.RCloneRemoteSpec) rcofrozenbitssev1alpha1.RCloneRemoteInstance {
	ns := &v1.Namespace{GenerateName: "rcloneremote-test-"}
	g.Expect(k8sClient.Create(t.Context(), ns)).To(Succeed())
	return &rcofrozenbitssev1alpha1.RCloneRemote{
		Namespace: ns.Name,
		Name:      "test-rcloneremote",
		Spec:      spec,
	}
}

// newTestRCloneClusterRemote returns an unsaved RCloneClusterRemote. Cluster-scoped names are
// shared by all subtests (and -count runs), so the name is generated.
func newTestRCloneClusterRemote(_ *testing.T, _ *WithT, spec rcofrozenbitssev1alpha1.RCloneRemoteSpec) rcofrozenbitssev1alpha1.RCloneRemoteInstance {
	return &rcofrozenbitssev1alpha1.RCloneClusterRemote{
		GenerateName: "test-rcloneclusterremote-",
		Spec:         spec,
	}
}

// reconcilerScope bundles what differs between the namespaced and cluster-scoped reconciler,
// for tests that run the same behaviour against both.
type reconcilerScope struct {
	name       string
	reconciler *RCloneRemoteReconciler
	newObject  newTestObjectFunc
	namespaced bool
}

func reconcilerScopes() []reconcilerScope {
	return []reconcilerScope{
		{
			name:       "RCloneRemote",
			reconciler: NewRCloneRemoteReconciler(k8sClient, k8sClient, "rclone-operator", testRevalidationInterval),
			newObject:  newTestRCloneRemote,
			namespaced: true,
		},
		{
			name:       "RCloneClusterRemote",
			reconciler: NewRCloneClusterRemoteReconciler(k8sClient, k8sClient, "rclone-operator", testRevalidationInterval),
			newObject:  newTestRCloneClusterRemote,
			namespaced: false,
		},
	}
}

func TestRCloneRemoteReconciler_StatusUpdate(t *testing.T) {
	for _, scope := range reconcilerScopes() {
		for _, tt := range statusUpdateCases {
			t.Run(scope.name+"/"+tt.name, func(t *testing.T) {
				t.Parallel()
				g := NewWithT(t)

				obj := scope.newObject(t, g, tt.in)

				// ApiServer LastTransitionTime is whole seconds.
				creationTime := time.Now().Truncate(time.Second)

				g.Expect(k8sClient.Create(t.Context(), obj)).To(Succeed())

				objectKey := client.ObjectKeyFromObject(obj)

				result, err := scope.reconciler.Reconcile(t.Context(), controllerruntime.Request{NamespacedName: objectKey})
				g.Expect(err).To(Succeed())
				g.Expect(result).To(Equal(tt.wantResult))

				g.Expect(k8sClient.Get(t.Context(), objectKey, obj)).To(Succeed())

				status := obj.GetRCloneRemoteStatus()
				g.Expect(status.Conditions).To(HaveLen(1))
				cond := status.Conditions[0]
				g.Expect(cond.Type).To(Equal(tt.wantCondition.Type))
				g.Expect(cond.Status).To(Equal(tt.wantCondition.Status))
				g.Expect(cond.Reason).To(Equal(tt.wantCondition.Reason))
				// Should have transitioned after the create && should apply to current generation of object.
				g.Expect(cond.LastTransitionTime.Time).To(BeTemporally(">=", creationTime))
				g.Expect(cond.ObservedGeneration).To(BeNumerically("==", obj.GetGeneration()))
			})
		}
	}
}

// AI-generated
func TestRCloneRemoteReconciler_NotFound(t *testing.T) {
	for _, scope := range reconcilerScopes() {
		t.Run(scope.name, func(t *testing.T) {
			t.Parallel()
			g := NewWithT(t)

			key := client.ObjectKey{Name: "does-not-exist"}
			if scope.namespaced {
				key.Namespace = "default"
			}

			result, err := scope.reconciler.Reconcile(t.Context(), controllerruntime.Request{NamespacedName: key})
			g.Expect(err).To(Succeed())
			g.Expect(result).To(Equal(controllerruntime.Result{}))
		})
	}
}

// A second reconcile with an unchanged spec must not write status again.
// AI-generated
func TestRCloneRemoteReconciler_Idempotent(t *testing.T) {
	for _, scope := range reconcilerScopes() {
		for _, tt := range statusUpdateCases {
			t.Run(scope.name+"/"+tt.name, func(t *testing.T) {
				t.Parallel()
				g := NewWithT(t)

				obj := createAndReconcile(t, g, scope, tt.in)
				key := client.ObjectKeyFromObject(obj)
				firstVersion := obj.GetResourceVersion()
				firstCond := obj.GetRCloneRemoteStatus().Conditions[0]

				result, err := scope.reconciler.Reconcile(t.Context(), controllerruntime.Request{NamespacedName: key})
				g.Expect(err).To(Succeed())
				g.Expect(result).To(Equal(tt.wantResult))

				g.Expect(k8sClient.Get(t.Context(), key, obj)).To(Succeed())
				g.Expect(obj.GetResourceVersion()).To(Equal(firstVersion), "second reconcile wrote status")
				conds := obj.GetRCloneRemoteStatus().Conditions
				g.Expect(conds).To(HaveLen(1))
				g.Expect(conds[0].LastTransitionTime.Time).To(BeTemporally("==", firstCond.LastTransitionTime.Time))
			})
		}
	}
}

// Changing the spec bumps the generation; the condition must follow it.
// AI-generated
func TestRCloneRemoteReconciler_SpecUpdate(t *testing.T) {
	validSpec := rcofrozenbitssev1alpha1.RCloneRemoteSpec{
		Type:     "template",
		Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: "A valid template"},
	}
	invalidSpec := rcofrozenbitssev1alpha1.RCloneRemoteSpec{
		Type:     "template",
		Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: "With a {{ .field }} that has no input"},
	}

	for _, scope := range reconcilerScopes() {
		t.Run(scope.name, func(t *testing.T) {
			t.Parallel()
			g := NewWithT(t)

			obj := createAndReconcile(t, g, scope, validSpec)
			key := client.ObjectKeyFromObject(obj)
			g.Expect(obj.GetRCloneRemoteStatus().Conditions[0].Status).To(Equal(metav1.ConditionTrue))

			*obj.GetRCloneRemoteSpec() = invalidSpec
			g.Expect(k8sClient.Update(t.Context(), obj)).To(Succeed())
			// Guards the premise of this test: a spec change must bump the generation.
			g.Expect(obj.GetGeneration()).To(BeNumerically("==", 2))

			result, err := scope.reconciler.Reconcile(t.Context(), controllerruntime.Request{NamespacedName: key})
			g.Expect(err).To(Succeed())
			g.Expect(result).To(Equal(controllerruntime.Result{}))

			g.Expect(k8sClient.Get(t.Context(), key, obj)).To(Succeed())
			conds := obj.GetRCloneRemoteStatus().Conditions
			g.Expect(conds).To(HaveLen(1))
			g.Expect(conds[0].Status).To(Equal(metav1.ConditionFalse))
			g.Expect(conds[0].Reason).To(Equal(rcofrozenbitssev1alpha1.ReasonInvalid))
			g.Expect(conds[0].ObservedGeneration).To(Equal(obj.GetGeneration()))
		})
	}
}
