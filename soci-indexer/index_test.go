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
	"archive/tar"
	"compress/gzip"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"

	"github.com/awslabs/soci-snapshotter/ztoc"
	"github.com/google/go-containerregistry/pkg/crane"
	"github.com/google/go-containerregistry/pkg/registry"
)

const gzipLayer = "application/vnd.oci.image.layer.v1.tar+gzip"

// writeLayer writes a gzip tar with the given files, each one filled with random (incompressible) bytes
func writeLayer(t *testing.T, files map[string]int) (string, []string) {
	t.Helper()
	path := filepath.Join(t.TempDir(), "layer.tar.gz")
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	gz := gzip.NewWriter(f)
	tw := tar.NewWriter(gz)
	names := make([]string, 0, len(files))
	for n := range files {
		names = append(names, n)
	}
	sort.Strings(names)
	for _, n := range names {
		data := make([]byte, files[n])
		_, _ = rand.Read(data)
		if err := tw.WriteHeader(&tar.Header{Name: n, Mode: 0644, Size: int64(len(data)), Typeflag: tar.TypeReg}); err != nil {
			t.Fatal(err)
		}
		if _, err := tw.Write(data); err != nil {
			t.Fatal(err)
		}
	}
	_ = tw.Close()
	_ = gz.Close()
	_ = f.Close()
	return path, names
}

func testRegistry(t *testing.T) string {
	t.Helper()
	srv := httptest.NewServer(registry.New())
	t.Cleanup(srv.Close)
	return strings.TrimPrefix(srv.URL, "http://")
}

func TestZtocTag(t *testing.T) {
	got := ZtocTag("sha256:6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5", DefaultSpanSize)
	expected := "ztoc-4194304-6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5"
	if got != expected {
		t.Errorf("got %s, expected %s", got, expected)
	}
}

func TestIndexGzipLayer(t *testing.T) {
	layer, names := writeLayer(t, map[string]int{"bin/tool": 8 << 20, "lib/data.bin": 4 << 20, "etc/conf": 100})
	host := testRegistry(t)
	// upload the layer into an image repository, the indexer downloads it from there
	data, _ := os.ReadFile(layer)
	digest := sha256Digest(data)
	src, _ := newRegistry(host + "/library/app")
	if err := src.putBlob(strings.NewReader(string(data)), digest, int64(len(data))); err != nil {
		t.Fatal(err)
	}
	repo := host + "/wave/soci-ztoc"

	result, err := IndexLayer(IndexRequest{Source: host + "/library/app@" + digest, LayerDigest: digest, LayerMediaType: gzipLayer,
		SpanSize: DefaultSpanSize, Repository: repo})
	if err != nil {
		t.Fatal(err)
	}
	if result.ZtocDigest == "" || result.ZtocSize == 0 {
		t.Fatalf("unexpected result %+v", result)
	}

	// the artifact is resolvable by tag and its annotations describe the zTOC
	raw, err := crane.Manifest(repo+":"+ZtocTag(digest, DefaultSpanSize), crane.Insecure)
	if err != nil {
		t.Fatal(err)
	}
	var m struct {
		ArtifactType string            `json:"artifactType"`
		Annotations  map[string]string `json:"annotations"`
		Layers       []struct {
			Digest string `json:"digest"`
		} `json:"layers"`
	}
	_ = json.Unmarshal(raw, &m)
	if m.ArtifactType != ZtocArtifactType || m.Annotations[AnnotationZtocDigest] != result.ZtocDigest ||
		m.Annotations[AnnotationLayerDigest] != digest || m.Layers[0].Digest != result.ZtocDigest {
		t.Fatalf("unexpected artifact manifest %s", raw)
	}

	// the zTOC blob lists the files of the layer
	blob, err := crane.PullLayer(repo+"@"+result.ZtocDigest, crane.Insecure)
	if err != nil {
		t.Fatal(err)
	}
	rc, _ := blob.Compressed()
	toc, err := ztoc.Unmarshal(rc)
	if err != nil {
		t.Fatal(err)
	}
	var got []string
	for _, e := range toc.TOC.FileMetadata {
		got = append(got, e.Name)
	}
	sort.Strings(got)
	if strings.Join(got, ",") != strings.Join(names, ",") {
		t.Fatalf("ztoc files %v, expected %v", got, names)
	}
	if toc.BuildToolIdentifier != BuildToolIdentifier {
		t.Fatalf("unexpected build tool %s", toc.BuildToolIdentifier)
	}
}

func TestPushPlan(t *testing.T) {
	host := testRegistry(t)
	// a source blob in another repository of the same registry
	src := host + "/wave/soci-ztoc"
	srcData := []byte("ztoc-bytes")
	srcDigest := sha256Digest(srcData)
	reg, _ := newRegistry(src)
	if err := reg.putBlob(strings.NewReader(string(srcData)), srcDigest, int64(len(srcData))); err != nil {
		t.Fatal(err)
	}

	manifest := []byte(`{"schemaVersion":2,"mediaType":"application/vnd.oci.image.manifest.v1+json","config":{"mediaType":"application/vnd.amazon.soci.index.v2+json","digest":"` + EmptyJSONDigest + `","size":2},"layers":[{"mediaType":"application/octet-stream","digest":"` + srcDigest + `","size":10}]}`)
	plan := PushPlan{
		Blobs: []PushBlob{
			{Digest: EmptyJSONDigest, Size: 2, Source: "base64:" + base64.StdEncoding.EncodeToString(EmptyJSON)},
			{Digest: srcDigest, Size: int64(len(srcData)), Source: "docker://" + src + "@" + srcDigest},
		},
		Manifests: []PushManifest{
			{Reference: sha256Digest(manifest), MediaType: OCIManifestMediaType, Body: base64.StdEncoding.EncodeToString(manifest)},
			{Reference: "1.0-soci", MediaType: OCIManifestMediaType, Body: base64.StdEncoding.EncodeToString(manifest)},
		},
	}
	target := host + "/library/app"
	if err := Push(target, plan); err != nil {
		t.Fatal(err)
	}
	got, err := crane.Manifest(target+":1.0-soci", crane.Insecure)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(manifest) {
		t.Fatalf("manifest bytes not preserved: %s", got)
	}
	if _, err := crane.PullLayer(target+"@"+srcDigest, crane.Insecure); err != nil {
		t.Fatalf("blob not copied: %v", err)
	}
}

func TestPushRejectsDigestMismatch(t *testing.T) {
	host := testRegistry(t)
	plan := PushPlan{Manifests: []PushManifest{{Reference: "sha256:0000000000000000000000000000000000000000000000000000000000000000",
		MediaType: OCIManifestMediaType, Body: base64.StdEncoding.EncodeToString([]byte("{}"))}}}
	if err := Push(host+"/library/app", plan); err == nil || !strings.Contains(err.Error(), "does not match digest") {
		t.Fatalf("expected digest mismatch error, got %v", err)
	}
}
