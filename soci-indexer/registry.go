/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"

	"github.com/google/go-containerregistry/pkg/authn"
	"github.com/google/go-containerregistry/pkg/name"
	"github.com/google/go-containerregistry/pkg/v1/remote"
	"github.com/google/go-containerregistry/pkg/v1/static"
	"github.com/google/go-containerregistry/pkg/v1/types"
)

const (
	OCIManifestMediaType = "application/vnd.oci.image.manifest.v1+json"
	OCIEmptyMediaType    = "application/vnd.oci.empty.v1+json"
	// EmptyJSONDigest is the digest of the two bytes "{}", used as config of artifacts
	EmptyJSONDigest = "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a"
)

// EmptyJSON is the content of the empty config blob
var EmptyJSON = []byte("{}")

func sha256Digest(data []byte) string {
	sum := sha256.Sum256(data)
	return "sha256:" + hex.EncodeToString(sum[:])
}

type ociRegistry struct {
	repo    name.Repository
	options []remote.Option
}

// newRegistry creates a client of an OCI repository. Plain HTTP is used for local registries only
// e.g. localhost or 127.0.0.1, see name.Registry.Scheme
func newRegistry(repository string) (*ociRegistry, error) {
	repo, err := name.NewRepository(repository)
	if err != nil {
		return nil, fmt.Errorf("invalid repository '%s': %w", repository, err)
	}
	return &ociRegistry{repo: repo, options: []remote.Option{remote.WithAuthFromKeychain(authn.DefaultKeychain)}}, nil
}

// putBlob uploads a blob, checking its digest; the upload is skipped when the registry already has it
func (r *ociRegistry) putBlob(content io.Reader, digest string, size int64) error {
	data, err := io.ReadAll(content)
	if err != nil {
		return err
	}
	if int64(len(data)) != size || sha256Digest(data) != digest {
		return fmt.Errorf("blob content does not match digest %s and size %d", digest, size)
	}
	layer := static.NewLayer(data, types.MediaType("application/octet-stream"))
	if err := remote.WriteLayer(r.repo, layer, r.options...); err != nil {
		return fmt.Errorf("unable to upload blob %s to %s: %w", digest, r.repo, err)
	}
	return nil
}

type rawManifest struct {
	body      []byte
	mediaType string
}

func (m rawManifest) RawManifest() ([]byte, error) { return m.body, nil }

func (m rawManifest) MediaType() (types.MediaType, error) { return types.MediaType(m.mediaType), nil }

// putManifest uploads the manifest bytes as they are, by tag or by digest
func (r *ociRegistry) putManifest(reference string, mediaType string, body []byte) error {
	ref, err := name.ParseReference(r.repo.Name()+referenceSeparator(reference)+reference)
	if err != nil {
		return fmt.Errorf("invalid manifest reference '%s': %w", reference, err)
	}
	if err := remote.Put(ref, rawManifest{body: body, mediaType: mediaType}, r.options...); err != nil {
		return fmt.Errorf("unable to upload manifest %s: %w", ref, err)
	}
	return nil
}

func referenceSeparator(reference string) string {
	if len(reference) > 7 && reference[:7] == "sha256:" {
		return "@"
	}
	return ":"
}
