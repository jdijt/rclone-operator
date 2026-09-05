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

// RCloneRemoteSpec defines the desired state of RCloneRemote.
//
// It is a discriminated union: type selects which of the backend structs must be set.
//
// +kubebuilder:validation:XValidation:rule="self.type == 'sftp' ? has(self.sftp) : !has(self.sftp)",message="sftp must be set if and only if type is sftp"
// +kubebuilder:validation:XValidation:rule="self.type == 'crypt' ? has(self.crypt) : !has(self.crypt)",message="crypt must be set if and only if type is crypt"
// +kubebuilder:validation:XValidation:rule="self.type == 's3' ? has(self.s3) : !has(self.s3)",message="s3 must be set if and only if type is s3"
// +kubebuilder:validation:XValidation:rule="self.type == 'template' ? has(self.template) : !has(self.template)",message="template must be set if and only if type is template"
type RCloneRemoteSpec struct {
	// type selects the rclone backend. Exactly the matching backend field must be set.
	// +required
	Type RCBackendType `json:"type"`

	// sftp configures an rclone sftp backend.
	// +optional
	SFTP *SftpBackend `json:"sftp,omitempty"`

	// crypt configures an rclone crypt backend wrapping another remote.
	// +optional
	Crypt *CryptBackend `json:"crypt,omitempty"`

	// s3 configures an rclone s3 backend.
	// +optional
	S3 *S3Backend `json:"s3,omitempty"`

	// template configures a remote from a free-form rclone connection string
	// template with secret inputs.
	// +optional
	Template *TemplateBackend `json:"template,omitempty"`
}

// RCBackendType defines the type of backend this is. Values mirror rclone's backend names.
//
// +kubebuilder:validation:Enum=sftp;s3;crypt;template
type RCBackendType string

const (
	RCBackendTypeSFTP     RCBackendType = "sftp"
	RCBackendTypeS3       RCBackendType = "s3"
	RCBackendTypeCrypt    RCBackendType = "crypt"
	RCBackendTypeTemplate RCBackendType = "template"
)

// SecretKeyRef selects a single key of a Secret. For a namespaced RCloneRemote the
// Secret is looked up in the remote's namespace; for an RCloneClusterRemote it is
// looked up in the operator's namespace.
type SecretKeyRef struct {
	// name of the Secret.
	// +required
	// +kubebuilder:validation:MinLength=1
	Name string `json:"name"`

	// key within the Secret's data.
	// +required
	// +kubebuilder:validation:MinLength=1
	Key string `json:"key"`
}

// RemoteRefKind is the kind of an RCloneRemote-like object referenced by name.
//
// +kubebuilder:validation:Enum=RCloneRemote;RCloneClusterRemote
type RemoteRefKind string

const (
	RemoteRefKindRemote        RemoteRefKind = "RCloneRemote"
	RemoteRefKindClusterRemote RemoteRefKind = "RCloneClusterRemote"
)

// RemoteRef references an RCloneRemote in the same namespace or an RCloneClusterRemote.
type RemoteRef struct {
	// kind of the referenced object. Defaults to RCloneRemote.
	// +optional
	// +kubebuilder:default=RCloneRemote
	Kind RemoteRefKind `json:"kind,omitempty"`

	// name of the referenced object.
	// +required
	// +kubebuilder:validation:MinLength=1
	Name string `json:"name"`
}

// SftpBackend maps to rclone's sftp backend. At least one of passwordRef or
// privateKeyRef must be set.
//
// +kubebuilder:validation:XValidation:rule="has(self.passwordRef) || has(self.privateKeyRef)",message="one of passwordRef or privateKeyRef must be set"
type SftpBackend struct {
	// host to connect to (rclone option: host).
	// +required
	// +kubebuilder:validation:MinLength=1
	Host string `json:"host"`

	// port to connect to (rclone option: port).
	// +optional
	// +kubebuilder:default=22
	// +kubebuilder:validation:Minimum=1
	// +kubebuilder:validation:Maximum=65535
	Port int32 `json:"port,omitempty"`

	// user to log in as (rclone option: user).
	// +required
	// +kubebuilder:validation:MinLength=1
	User string `json:"user"`

	// passwordRef selects the Secret key holding the SSH password (rclone option: pass).
	// +optional
	PasswordRef *SecretKeyRef `json:"passwordRef,omitempty"`

	// privateKeyRef selects the Secret key holding a PEM-encoded private key
	// (rclone option: key_pem).
	// +optional
	PrivateKeyRef *SecretKeyRef `json:"privateKeyRef,omitempty"`

	// privateKeyPassphraseRef selects the Secret key holding the passphrase for
	// privateKeyRef, if the key is encrypted (rclone option: key_file_pass).
	// +optional
	PrivateKeyPassphraseRef *SecretKeyRef `json:"privateKeyPassphraseRef,omitempty"`

	// connections is the maximum number of concurrent SSH connections rclone may
	// open to this host (rclone option: connections). Unset means rclone's default
	// of unlimited.
	// +optional
	// +kubebuilder:validation:Minimum=1
	Connections *int32 `json:"connections,omitempty"`
}

// S3Backend maps to rclone's s3 backend. The bucket is not part of the remote; it is
// the first path element on the sync endpoint, as in rclone.
type S3Backend struct {
	// provider is the rclone s3 provider name, e.g. AWS, Minio, Ceph, Wasabi, Other
	// (rclone option: provider).
	// +required
	// +kubebuilder:validation:MinLength=1
	Provider string `json:"provider"`

	// endpoint of the S3 API, for non-AWS providers (rclone option: endpoint).
	// +optional
	Endpoint string `json:"endpoint,omitempty"`

	// region to connect to (rclone option: region).
	// +optional
	Region string `json:"region,omitempty"`

	// accessKeyIDRef selects the Secret key holding the access key ID
	// (rclone option: access_key_id).
	// +required
	AccessKeyIDRef SecretKeyRef `json:"accessKeyIDRef"`

	// secretAccessKeyRef selects the Secret key holding the secret access key
	// (rclone option: secret_access_key).
	// +required
	SecretAccessKeyRef SecretKeyRef `json:"secretAccessKeyRef"`

	// forcePathStyle uses path-style bucket addressing; required by most
	// non-AWS providers (rclone option: force_path_style).
	// +optional
	ForcePathStyle *bool `json:"forcePathStyle,omitempty"`

	// storageClass to use when storing new objects (rclone option: storage_class).
	// +optional
	StorageClass string `json:"storageClass,omitempty"`

	// acl is the canned ACL applied to created objects and buckets (rclone option: acl).
	// +optional
	ACL string `json:"acl,omitempty"`

	// noCheckBucket skips checking for and creating the bucket, for credentials
	// without bucket-level permissions (rclone option: no_check_bucket).
	// +optional
	NoCheckBucket bool `json:"noCheckBucket,omitempty"`
}

// CryptFilenameEncryption is the rclone crypt filename_encryption mode.
//
// +kubebuilder:validation:Enum=standard;obfuscate;off
type CryptFilenameEncryption string

const (
	CryptFilenameEncryptionStandard  CryptFilenameEncryption = "standard"
	CryptFilenameEncryptionObfuscate CryptFilenameEncryption = "obfuscate"
	CryptFilenameEncryptionOff       CryptFilenameEncryption = "off"
)

// CryptFilenameEncoding is the rclone crypt filename_encoding.
//
// +kubebuilder:validation:Enum=base32;base64;base32768
type CryptFilenameEncoding string

const (
	CryptFilenameEncodingBase32    CryptFilenameEncoding = "base32"
	CryptFilenameEncodingBase64    CryptFilenameEncoding = "base64"
	CryptFilenameEncodingBase32768 CryptFilenameEncoding = "base32768"
)

// CryptBackend maps to rclone's crypt backend, which encrypts another remote.
type CryptBackend struct {
	// remoteRef references the remote to wrap (rclone option: remote).
	// +required
	RemoteRef RemoteRef `json:"remoteRef"`

	// path within the wrapped remote that holds the encrypted data
	// (rclone option: remote, the part after the colon).
	// +optional
	Path string `json:"path,omitempty"`

	// passwordRef selects the Secret key holding the encryption password
	// (rclone option: password).
	// +required
	PasswordRef SecretKeyRef `json:"passwordRef"`

	// saltRef selects the Secret key holding the optional salt
	// (rclone option: password2).
	// +optional
	SaltRef *SecretKeyRef `json:"saltRef,omitempty"`

	// filenameEncryption selects how file names are encrypted
	// (rclone option: filename_encryption).
	// +optional
	// +kubebuilder:default=standard
	FilenameEncryption CryptFilenameEncryption `json:"filenameEncryption,omitempty"`

	// directoryNameEncryption enables encryption of directory names
	// (rclone option: directory_name_encryption).
	// +optional
	// +kubebuilder:default=true
	DirectoryNameEncryption *bool `json:"directoryNameEncryption,omitempty"`

	// filenameEncoding selects the encoding of encrypted file names
	// (rclone option: filename_encoding).
	// +optional
	// +kubebuilder:default=base32
	FilenameEncoding CryptFilenameEncoding `json:"filenameEncoding,omitempty"`
}

// TemplateBackend builds a remote from an rclone connection string template
// (e.g. ":webdav,url=https://example.com,user=me,pass={{ .password }}:") whose
// placeholders are filled from Secret keys. The author of the template is
// responsible for rclone's connection string quoting of the substituted values.
type TemplateBackend struct {
	// template is the rclone connection string with placeholders for inputs.
	// +required
	// +kubebuilder:validation:MinLength=1
	Template string `json:"template"`

	// inputs maps placeholder names to the Secret keys that fill them.
	// +optional
	Inputs map[string]SecretKeyRef `json:"inputs,omitempty"`
}

// RCloneRemoteStatus defines the observed state of RCloneRemote.
type RCloneRemoteStatus struct {
	// conditions represent the current state of the RCloneRemote resource.
	// Each condition has a unique type and reflects the status of a specific aspect of the resource.
	//
	// Standard condition types include:
	// - "Available": the resource is fully functional
	// - "Progressing": the resource is being created or updated
	// - "Degraded": the resource failed to reach or maintain its desired state
	//
	// The status of each condition is one of True, False, or Unknown.
	// +listType=map
	// +listMapKey=type
	// +optional
	Conditions []metav1.Condition `json:"conditions,omitempty"`
}

// +kubebuilder:object:root=true
// +kubebuilder:subresource:status

// RCloneRemote is the Schema for the rcloneremotes API
type RCloneRemote struct {
	metav1.TypeMeta `json:",inline"`

	// metadata is a standard object metadata
	// +optional
	metav1.ObjectMeta `json:"metadata,omitzero"`

	// spec defines the desired state of RCloneRemote
	// +required
	Spec RCloneRemoteSpec `json:"spec"`

	// status defines the observed state of RCloneRemote
	// +optional
	Status RCloneRemoteStatus `json:"status,omitzero"`
}

// +kubebuilder:object:root=true

// RCloneRemoteList contains a list of RCloneRemote
type RCloneRemoteList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitzero"`
	Items           []RCloneRemote `json:"items"`
}

func init() {
	SchemeBuilder.Register(func(s *runtime.Scheme) error {
		s.AddKnownTypes(SchemeGroupVersion, &RCloneRemote{}, &RCloneRemoteList{})
		return nil
	})
}
