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
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"

	"github.com/awslabs/soci-snapshotter/ztoc"
	"github.com/awslabs/soci-snapshotter/ztoc/compression"
	"github.com/google/go-containerregistry/pkg/authn"
	"github.com/google/go-containerregistry/pkg/name"
	"github.com/google/go-containerregistry/pkg/v1/remote"
)

const (
	// DefaultSpanSize is the soci default distance between two decompression checkpoints
	DefaultSpanSize = int64(1 << 22)

	// BuildToolIdentifier is written into every zTOC built by this tool
	BuildToolIdentifier = "Seqera Wave soci-indexer v0.1"

	// ZtocArtifactType is the artifact type of the OCI artifact holding one zTOC
	ZtocArtifactType = "application/vnd.seqera.wave.ztoc.v1+json"
	// ZtocMediaType is the media type of a zTOC blob, same as soci
	ZtocMediaType = "application/octet-stream"

	AnnotationLayerDigest    = "io.seqera.wave.ztoc.layer-digest"
	AnnotationLayerMediaType = "io.seqera.wave.ztoc.layer-mediaType"
	AnnotationZtocDigest     = "io.seqera.wave.ztoc.digest"
	AnnotationZtocSize       = "io.seqera.wave.ztoc.size"
	AnnotationSpanSize       = "io.seqera.wave.ztoc.span-size"
)

// IndexRequest holds the parameters to index one layer. Wave only requests gzip layers
// above the soci minimum size
type IndexRequest struct {
	// Source is the layer reference e.g. docker.io/library/ubuntu@sha256:…
	Source         string
	LayerDigest    string
	LayerMediaType string
	SpanSize       int64
	Repository     string
}

// IndexResult is printed on stdout as a single JSON line and parsed by Wave
type IndexResult struct {
	LayerDigest    string `json:"layerDigest"`
	LayerMediaType string `json:"layerMediaType"`
	ZtocDigest     string `json:"ztocDigest,omitempty"`
	ZtocSize       int64  `json:"ztocSize,omitempty"`
	SpanSize       int64  `json:"spanSize"`
}

// ZtocTag is the tag of the zTOC artifact for a layer digest and span size
func ZtocTag(layerDigest string, spanSize int64) string {
	return fmt.Sprintf("ztoc-%d-%s", spanSize, strings.TrimPrefix(layerDigest, "sha256:"))
}

// BuildZtoc builds and serializes the zTOC of a gzip layer file
func BuildZtoc(layerFile string, spanSize int64) ([]byte, error) {
	toc, err := ztoc.NewBuilder(BuildToolIdentifier).BuildZtoc(layerFile, spanSize, ztoc.WithCompression(compression.Gzip))
	if err != nil {
		return nil, fmt.Errorf("unable to build ztoc: %w", err)
	}
	reader, _, err := ztoc.Marshal(toc)
	if err != nil {
		return nil, fmt.Errorf("unable to marshal ztoc: %w", err)
	}
	return io.ReadAll(reader)
}

// IndexLayer builds the zTOC of a layer and pushes it as an OCI artifact to the zTOC repository
func IndexLayer(req IndexRequest) (*IndexResult, error) {
	file, err := downloadLayer(req.Source)
	if err != nil {
		return nil, err
	}
	defer os.Remove(file)
	result := &IndexResult{LayerDigest: req.LayerDigest, LayerMediaType: req.LayerMediaType, SpanSize: req.SpanSize}
	data, err := BuildZtoc(file, req.SpanSize)
	if err != nil {
		return nil, err
	}
	result.ZtocDigest = sha256Digest(data)
	result.ZtocSize = int64(len(data))
	manifest, err := ZtocArtifactManifest(*result)
	if err != nil {
		return nil, err
	}
	reg, err := newRegistry(req.Repository)
	if err != nil {
		return nil, err
	}
	// push the blobs first and the tag last, so that a resolvable tag means a complete entry
	if err := reg.putBlob(bytes.NewReader(data), result.ZtocDigest, result.ZtocSize); err != nil {
		return nil, err
	}
	if err := reg.putBlob(bytes.NewReader(EmptyJSON), EmptyJSONDigest, int64(len(EmptyJSON))); err != nil {
		return nil, err
	}
	if err := reg.putManifest(ZtocTag(req.LayerDigest, req.SpanSize), OCIManifestMediaType, manifest); err != nil {
		return nil, err
	}
	return result, nil
}

// ZtocArtifactManifest creates the OCI artifact manifest describing one zTOC
func ZtocArtifactManifest(r IndexResult) ([]byte, error) {
	m := map[string]any{
		"schemaVersion": 2,
		"mediaType":     OCIManifestMediaType,
		"artifactType":  ZtocArtifactType,
		"config": map[string]any{
			"mediaType": OCIEmptyMediaType,
			"digest":    EmptyJSONDigest,
			"size":      len(EmptyJSON),
		},
		"layers": []any{map[string]any{
			"mediaType": ZtocMediaType,
			"digest":    r.ZtocDigest,
			"size":      r.ZtocSize,
		}},
		"annotations": map[string]string{
			AnnotationLayerDigest:    r.LayerDigest,
			AnnotationLayerMediaType: r.LayerMediaType,
			AnnotationZtocDigest:     r.ZtocDigest,
			AnnotationZtocSize:       strconv.FormatInt(r.ZtocSize, 10),
			AnnotationSpanSize:       strconv.FormatInt(r.SpanSize, 10),
		},
	}
	return json.Marshal(m)
}

// downloadLayer saves a layer blob into a temporary file, verifying its digest
func downloadLayer(source string) (string, error) {
	ref, err := name.NewDigest(source)
	if err != nil {
		return "", fmt.Errorf("invalid layer source '%s': %w", source, err)
	}
	layer, err := remote.Layer(ref, remote.WithAuthFromKeychain(authn.DefaultKeychain))
	if err != nil {
		return "", fmt.Errorf("unable to access layer %s: %w", source, err)
	}
	content, err := layer.Compressed()
	if err != nil {
		return "", fmt.Errorf("unable to download layer %s: %w", source, err)
	}
	defer content.Close()
	file, err := os.CreateTemp("", "layer-*.tar.gz")
	if err != nil {
		return "", err
	}
	defer file.Close()
	// the remote layer reader verifies the digest when the content is fully read
	if _, err := io.Copy(file, content); err != nil {
		os.Remove(file.Name())
		return "", fmt.Errorf("unable to download layer %s: %w", source, err)
	}
	return file.Name(), nil
}
