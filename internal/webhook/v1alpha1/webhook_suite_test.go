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
	"fmt"
	"log"
	"os"
	"path/filepath"
	"testing"
	"time"

	"k8s.io/client-go/kubernetes/scheme"
	"k8s.io/client-go/rest"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/envtest"
	logf "sigs.k8s.io/controller-runtime/pkg/log"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"
	"sigs.k8s.io/controller-runtime/pkg/webhook"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	// +kubebuilder:scaffold:imports
)

// AI-generated: moved from the Ginkgo scaffold to TestMain, following internal/controller/suite_test.go.

var k8sClient client.Client

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	logf.SetLogger(zap.New(zap.WriteTo(os.Stderr), zap.UseDevMode(true)))

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

	stopWebhookServer, err := startWebhookServer(cfg, &testEnv.WebhookInstallOptions)
	if err != nil {
		log.Printf("Failure starting webhook server: %v", err)
		return 1
	}
	// Deferred after teardown, so it runs first: the API server outlives the webhook server.
	defer stopWebhookServer()

	return m.Run()
}

func startEnv() (*envtest.Environment, *rest.Config, error) {
	if err := rcofrozenbitssev1alpha1.AddToScheme(scheme.Scheme); err != nil {
		return nil, nil, fmt.Errorf("adding types to scheme: %w", err)
	}

	// +kubebuilder:scaffold:scheme
	// Note: this will insert AddToScheme + a gomega assertion, the latter needs to be removed manually.

	testEnv := &envtest.Environment{
		CRDDirectoryPaths:     []string{filepath.Join("..", "..", "..", "config", "crd", "bases")},
		ErrorIfCRDPathMissing: true,
		WebhookInstallOptions: envtest.WebhookInstallOptions{
			Paths: []string{filepath.Join("..", "..", "..", "config", "webhook")},
		},
	}
	if binaryDir := getFirstFoundEnvTestBinaryDir(); binaryDir != "" {
		testEnv.BinaryAssetsDirectory = binaryDir
	}

	cfg, err := testEnv.Start()
	if err != nil {
		return nil, nil, fmt.Errorf("starting test environment: %w", err)
	}
	return testEnv, cfg, nil
}

// startWebhookServer runs a manager serving the webhooks on the address envtest registered
// them under, and waits until it accepts connections. The returned func stops the manager.
func startWebhookServer(cfg *rest.Config, opts *envtest.WebhookInstallOptions) (func(), error) {
	mgr, err := ctrl.NewManager(cfg, ctrl.Options{
		Scheme: scheme.Scheme,
		WebhookServer: webhook.NewServer(webhook.Options{
			Host:    opts.LocalServingHost,
			Port:    opts.LocalServingPort,
			CertDir: opts.LocalServingCertDir,
		}),
		LeaderElection: false,
		Metrics:        metricsserver.Options{BindAddress: "0"},
	})
	if err != nil {
		return nil, fmt.Errorf("creating manager: %w", err)
	}

	if err := SetupRCloneRemoteWebhookWithManager(mgr); err != nil {
		return nil, fmt.Errorf("setting up RCloneRemote webhook: %w", err)
	}
	if err := SetupRCloneClusterRemoteWebhookWithManager(mgr); err != nil {
		return nil, fmt.Errorf("setting up RCloneClusterRemote webhook: %w", err)
	}

	// +kubebuilder:scaffold:webhook

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- mgr.Start(ctx) }()
	stop := func() {
		cancel()
		if err := <-done; err != nil {
			log.Printf("Failure running manager: %v", err)
		}
	}

	started := mgr.GetWebhookServer().StartedChecker()
	deadline := time.Now().Add(10 * time.Second)
	for err := started(nil); err != nil; err = started(nil) {
		if time.Now().After(deadline) {
			stop()
			return nil, fmt.Errorf("waiting for webhook server: %w", err)
		}
		time.Sleep(100 * time.Millisecond)
	}
	return stop, nil
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
	basePath := filepath.Join("..", "..", "..", "bin", "k8s")
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
