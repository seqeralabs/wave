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
	"bytes"
	"encoding/base64"
	"fmt"
	"strings"
)

// PushPlan lists the blobs and manifests to upload, in order. Blobs are uploaded first,
// then manifests in the listed order, so a manifest is never pushed before what it references
type PushPlan struct {
	Blobs     []PushBlob     `json:"blobs"`
	Manifests []PushManifest `json:"manifests"`
}

// PushBlob is one blob to upload. Source is either "docker://<repo>@<digest>", copied
// (or mounted) from another repository, or "base64:<content>"
type PushBlob struct {
	Digest string `json:"digest"`
	Size   int64  `json:"size"`
	Source string `json:"source"`
}

// PushManifest is one manifest to upload, by tag or digest. Body is base64 encoded,
// so that its bytes, and therefore its digest, are preserved exactly
type PushManifest struct {
	Reference string `json:"reference"`
	MediaType string `json:"mediaType"`
	Body      string `json:"body"`
}

// Push uploads the plan blobs and manifests to the target repository
func Push(target string, plan PushPlan, insecure bool) error {
	reg, err := newRegistry(target, insecure)
	if err != nil {
		return err
	}
	for _, blob := range plan.Blobs {
		if err := pushBlob(reg, blob); err != nil {
			return err
		}
	}
	for _, m := range plan.Manifests {
		body, err := base64.StdEncoding.DecodeString(m.Body)
		if err != nil {
			return fmt.Errorf("invalid body for manifest %s: %w", m.Reference, err)
		}
		if strings.HasPrefix(m.Reference, "sha256:") && sha256Digest(body) != m.Reference {
			return fmt.Errorf("manifest body does not match digest %s", m.Reference)
		}
		if err := reg.putManifest(m.Reference, m.MediaType, body); err != nil {
			return err
		}
	}
	return nil
}

func pushBlob(reg *ociRegistry, blob PushBlob) error {
	switch {
	case strings.HasPrefix(blob.Source, "docker://"):
		return reg.copyBlob(strings.TrimPrefix(blob.Source, "docker://"))
	case strings.HasPrefix(blob.Source, "base64:"):
		data, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(blob.Source, "base64:"))
		if err != nil {
			return fmt.Errorf("invalid content for blob %s: %w", blob.Digest, err)
		}
		return reg.putBlob(bytes.NewReader(data), blob.Digest, blob.Size)
	default:
		return fmt.Errorf("unsupported source for blob %s: %s", blob.Digest, blob.Source)
	}
}
