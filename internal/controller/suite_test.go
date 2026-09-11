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
	"fmt"
	"log"
	"os"
	"path/filepath"
	"testing"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	"k8s.io/client-go/kubernetes/scheme"
	"k8s.io/client-go/rest"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/envtest"
	// +kubebuilder:scaffold:imports
)

var k8sClient client.Client

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	testEnv, cfg, err := startEnv()
	if err != nil {
		log.Printf("Failure starting test environment: %v", err)
		return 1
	}
	defer teardown(testEnv)

	k8sClient, err = client.New(cfg, client.Options{Scheme: scheme.Scheme})
	if err != nil {
		log.Printf("Failure creating k8s client: %v", err)
		return 1
	}

	return m.Run()
}

func startEnv() (*envtest.Environment, *rest.Config, error) {

	if err := rcofrozenbitssev1alpha1.AddToScheme(scheme.Scheme); err != nil {
		return nil, nil, fmt.Errorf("adding types to scheme: %w", err)
	}

	// +kubebuilder:scaffold:scheme
	// Note: this will insert AddToScheme + a gomega assertion, the latter needs to be removed manually.

	testEnv := &envtest.Environment{
		CRDDirectoryPaths:     []string{filepath.Join("..", "..", "config", "crd", "bases")},
		ErrorIfCRDPathMissing: true,
	}
	if binaryDir := getFirstFoundEnvTestBinaryDir(); binaryDir != "" {
		testEnv.BinaryAssetsDirectory = binaryDir
	}

	cfg, err := testEnv.Start()
	if err != nil {
		return nil, nil, fmt.Errorf("starting test environment: %w", err)
	}
	return testEnv, cfg, err
}

func teardown(testEnv *envtest.Environment) {
	if err := testEnv.Stop(); err != nil {
		log.Printf("Failure stopping test environment: %v", err)
	}
}

// getFirstFoundEnvTestBinaryDir locates the first binary in the specified path.
// ENVTEST-based tests depend on specific binaries, usually located in paths set by
// controller-runtime. When running tests directly (e.g., via an IDE) without using
// Makefile targets, the 'BinaryAssetsDirectory' must be explicitly configured.
//
// This function streamlines the process by finding the required binaries, similar to
// setting the 'KUBEBUILDER_ASSETS' environment variable. To ensure the binaries are
// properly set up, run 'make setup-envtest' beforehand.
func getFirstFoundEnvTestBinaryDir() string {
	basePath := filepath.Join("..", "..", "bin", "k8s")
	entries, err := os.ReadDir(basePath)
	if err != nil {
		log.Printf("Failed to read directory: path: %v, err: %v", basePath, err)
		return ""
	}
	for _, entry := range entries {
		if entry.IsDir() {
			return filepath.Join(basePath, entry.Name())
		}
	}
	return ""
}
