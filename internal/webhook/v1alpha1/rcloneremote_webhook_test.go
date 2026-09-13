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
	"testing"

	rcofrozenbitssev1alpha1 "github.com/jdijt/rclone-operator/api/v1alpha1"
	. "github.com/onsi/gomega"
	v1 "k8s.io/api/core/v1"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

func TestRCloneRemoteAdmission(t *testing.T) {
	operatorNs := "rclone-operator"
	tests := []struct {
		name    string
		in      rcofrozenbitssev1alpha1.RCloneRemoteInstance
		wantErr string
	}{
		{
			name: "Valid NameSpaced Object",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "validremote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{
						Template: "valid",
					},
				},
			},
		},
		{
			name: "Valid ClusterWide Object",
			in: &rcofrozenbitssev1alpha1.RCloneClusterRemote{
				GenerateName: "validremote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{
						Template: "valid",
					},
				},
			},
		},
		// CRD structural (OpenAPI) validation: one row per kind of marker.
		{ // AI-generated test case
			name: "Enum rejects unknown type",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{Type: "ftp"},
			},
			wantErr: `Unsupported value: "ftp"`,
		},
		{ // AI-generated test case
			name: "MinLength rejects empty template",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type:     rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: ""},
				},
			},
			wantErr: "should be at least 1 chars long",
		},
		{ // AI-generated test case
			name: "Maximum rejects out of range sftp port",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP,
					SFTP: &rcofrozenbitssev1alpha1.SFTPBackend{
						Host: "host", User: "user", Port: 70000,
						PasswordRef: &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "password"},
					},
				},
			},
			wantErr: "should be less than or equal to 65535",
		},

		// CEL: type <=> matching backend set.
		{ // AI-generated test case
			name: "CEL rejects type sftp without sftp",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP},
			},
			wantErr: "sftp must be set if and only if type is sftp",
		},
		{ // AI-generated test case
			name: "CEL rejects type s3 without s3",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{Type: rcofrozenbitssev1alpha1.RCBackendTypeS3},
			},
			wantErr: "s3 must be set if and only if type is s3",
		},
		{ // AI-generated test case
			name: "CEL rejects type crypt without crypt",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt},
			},
			wantErr: "crypt must be set if and only if type is crypt",
		},
		{ // AI-generated test case
			name: "CEL rejects type template without template",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{Type: rcofrozenbitssev1alpha1.RCBackendTypeTemplate},
			},
			wantErr: "template must be set if and only if type is template",
		},
		{ // AI-generated test case
			name: "CEL rejects a second backend besides the selected one",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type:     rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: "valid"},
					S3: &rcofrozenbitssev1alpha1.S3Backend{
						Provider:           "AWS",
						AccessKeyIDRef:     rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "id"},
						SecretAccessKeyRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "secret"},
					},
				},
			},
			wantErr: "s3 must be set if and only if type is s3",
		},

		// CEL: sftp credentials.
		{ // AI-generated test case
			name: "Valid sftp with password",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP,
					SFTP: &rcofrozenbitssev1alpha1.SFTPBackend{
						Host: "host", User: "user",
						PasswordRef: &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "password"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "Valid sftp with private key and passphrase",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP,
					SFTP: &rcofrozenbitssev1alpha1.SFTPBackend{
						Host: "host", User: "user",
						PrivateKeyRef:           &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "key"},
						PrivateKeyPassphraseRef: &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "passphrase"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "CEL rejects sftp without password or private key",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP,
					SFTP: &rcofrozenbitssev1alpha1.SFTPBackend{Host: "host", User: "user"},
				},
			},
			wantErr: "one of passwordRef or privateKeyRef must be set",
		},
		{ // AI-generated test case
			name: "CEL rejects sftp passphrase without private key",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeSFTP,
					SFTP: &rcofrozenbitssev1alpha1.SFTPBackend{
						Host: "host", User: "user",
						PasswordRef:             &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "password"},
						PrivateKeyPassphraseRef: &rcofrozenbitssev1alpha1.SecretKeyRef{Name: "sftp", Key: "passphrase"},
					},
				},
			},
			wantErr: "privateKeyPassphrase supplied but no private key used",
		},

		// CEL: s3 endpoint.
		{ // AI-generated test case
			name: "Valid s3 on AWS without endpoint",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeS3,
					S3: &rcofrozenbitssev1alpha1.S3Backend{
						Provider:           "AWS",
						AccessKeyIDRef:     rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "id"},
						SecretAccessKeyRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "secret"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "Valid s3 on Ceph with https endpoint",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeS3,
					S3: &rcofrozenbitssev1alpha1.S3Backend{
						Provider:           "Ceph",
						Endpoint:           "https://s3.example.com",
						AccessKeyIDRef:     rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "id"},
						SecretAccessKeyRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "secret"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "CEL rejects s3 on non-AWS without endpoint",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeS3,
					S3: &rcofrozenbitssev1alpha1.S3Backend{
						Provider:           "Ceph",
						AccessKeyIDRef:     rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "id"},
						SecretAccessKeyRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "secret"},
					},
				},
			},
			wantErr: "Endpoint must be specified for non-aws providers",
		},
		{ // AI-generated test case
			name: "CEL rejects s3 endpoint with non-http scheme",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeS3,
					S3: &rcofrozenbitssev1alpha1.S3Backend{
						Provider:           "Ceph",
						Endpoint:           "ftp://s3.example.com",
						AccessKeyIDRef:     rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "id"},
						SecretAccessKeyRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "s3", Key: "secret"},
					},
				},
			},
			wantErr: "endpoint must be a valid http/https URL",
		},

		// CEL: crypt remoteRef kind. RCloneRemote may refer to either kind; RCloneClusterRemote
		// only to RCloneClusterRemote (remoteRef.kind defaults to RCloneRemote).
		{ // AI-generated test case
			name: "Valid namespaced crypt referring to RCloneRemote by default",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt,
					Crypt: &rcofrozenbitssev1alpha1.CryptBackend{
						RemoteRef:   rcofrozenbitssev1alpha1.RemoteRef{Name: "wrapped"},
						PasswordRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "crypt", Key: "password"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "Valid namespaced crypt referring to RCloneClusterRemote",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt,
					Crypt: &rcofrozenbitssev1alpha1.CryptBackend{
						RemoteRef:   rcofrozenbitssev1alpha1.RemoteRef{Kind: rcofrozenbitssev1alpha1.RemoteRefKindClusterRemote, Name: "wrapped"},
						PasswordRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "crypt", Key: "password"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "Valid cluster crypt referring to RCloneClusterRemote",
			in: &rcofrozenbitssev1alpha1.RCloneClusterRemote{
				GenerateName: "remote-",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt,
					Crypt: &rcofrozenbitssev1alpha1.CryptBackend{
						RemoteRef:   rcofrozenbitssev1alpha1.RemoteRef{Kind: rcofrozenbitssev1alpha1.RemoteRefKindClusterRemote, Name: "wrapped"},
						PasswordRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "crypt", Key: "password"},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "CEL rejects cluster crypt referring to RCloneRemote",
			in: &rcofrozenbitssev1alpha1.RCloneClusterRemote{
				GenerateName: "remote-",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt,
					Crypt: &rcofrozenbitssev1alpha1.CryptBackend{
						RemoteRef:   rcofrozenbitssev1alpha1.RemoteRef{Kind: rcofrozenbitssev1alpha1.RemoteRefKindRemote, Name: "wrapped"},
						PasswordRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "crypt", Key: "password"},
					},
				},
			},
			wantErr: "RCloneClusterRemote can only refer to other RCloneClusterRemotes.",
		},
		{ // AI-generated test case
			name: "CEL rejects cluster crypt with defaulted kind",
			in: &rcofrozenbitssev1alpha1.RCloneClusterRemote{
				GenerateName: "remote-",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeCrypt,
					Crypt: &rcofrozenbitssev1alpha1.CryptBackend{
						RemoteRef:   rcofrozenbitssev1alpha1.RemoteRef{Name: "wrapped"},
						PasswordRef: rcofrozenbitssev1alpha1.SecretKeyRef{Name: "crypt", Key: "password"},
					},
				},
			},
			wantErr: "RCloneClusterRemote can only refer to other RCloneClusterRemotes.",
		},

		// Webhook: one rejection per kind proves each webhook is wired; the rules themselves
		// are covered by unit tests in internal/remote.
		{ // AI-generated test case
			name: "Valid template with declared input",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type: rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{
						Template: ":webdav,pass={{ .password }}:",
						Inputs: map[string]rcofrozenbitssev1alpha1.SecretKeyRef{
							"password": {Name: "webdav", Key: "password"},
						},
					},
				},
			},
		},
		{ // AI-generated test case
			name: "Webhook rejects namespaced template with undeclared input",
			in: &rcofrozenbitssev1alpha1.RCloneRemote{
				Name: "remote",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type:     rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: "{{ .missing }}"},
				},
			},
			wantErr: `admission webhook "vrcloneremote-v1alpha1.kb.io" denied the request`,
		},
		{ // AI-generated test case
			name: "Webhook rejects cluster template with undeclared input",
			in: &rcofrozenbitssev1alpha1.RCloneClusterRemote{
				GenerateName: "remote-",
				Spec: rcofrozenbitssev1alpha1.RCloneRemoteSpec{
					Type:     rcofrozenbitssev1alpha1.RCBackendTypeTemplate,
					Template: &rcofrozenbitssev1alpha1.TemplateBackend{Template: "{{ .missing }}"},
				},
			},
			wantErr: `admission webhook "vrcloneclusterremote-v1alpha1.kb.io" denied the request`,
		},
	}

	// Ensure operatorNs exists
	NewWithT(t).
		Expect(client.IgnoreAlreadyExists(k8sClient.Create(t.Context(), &v1.Namespace{Name: operatorNs}))).
		To(Succeed())

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			g := NewWithT(t)
			// Set test NS on namespaced object.
			if obj, isNamespaced := tt.in.(*rcofrozenbitssev1alpha1.RCloneRemote); isNamespaced {
				ns := v1.Namespace{GenerateName: "webhook-test-"}
				g.Expect(k8sClient.Create(t.Context(), &ns)).To(Succeed())
				obj.Namespace = ns.Name
			}

			err := k8sClient.Create(t.Context(), tt.in)

			if tt.wantErr == "" {
				g.Expect(err).To(Succeed())
			} else {
				g.Expect(err).To(MatchError(ContainSubstring(tt.wantErr)))
			}
		})
	}
}
