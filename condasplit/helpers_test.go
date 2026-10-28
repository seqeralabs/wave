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
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const MB = 1_000_000

// prefix is a synthetic conda prefix created in a temporary directory
type prefix struct {
	t   *testing.T
	src string
	out string
}

func newPrefix(t *testing.T) *prefix {
	t.Helper()
	// resolve symlinks, e.g. /var -> /private/var on macOS
	dir, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	p := &prefix{t: t, src: filepath.Join(dir, "conda"), out: filepath.Join(dir, "layers")}
	p.dir("conda-meta")
	return p
}

func (p *prefix) path(rel string) string { return filepath.Join(p.src, rel) }

func (p *prefix) dir(rel string) string {
	p.t.Helper()
	d := p.path(rel)
	if err := os.MkdirAll(d, 0o755); err != nil {
		p.t.Fatal(err)
	}
	return d
}

// file creates a file of the given size. Large files are sparse, so tests can use
// realistic sizes without writing them
func (p *prefix) file(rel string, size int64) string {
	p.t.Helper()
	p.dir(filepath.Dir(rel))
	f, err := os.Create(p.path(rel))
	if err != nil {
		p.t.Fatal(err)
	}
	defer f.Close()
	if size > MB {
		err = f.Truncate(size)
	} else {
		data := make([]byte, size)
		for i := range data {
			data[i] = byte(len(rel) + i)
		}
		_, err = f.Write(data)
	}
	if err != nil {
		p.t.Fatal(err)
	}
	return p.path(rel)
}

func (p *prefix) symlink(rel, target string) {
	p.t.Helper()
	p.dir(filepath.Dir(rel))
	if err := os.Symlink(target, p.path(rel)); err != nil {
		p.t.Fatal(err)
	}
}

func (p *prefix) hardlink(existing, rel string) {
	p.t.Helper()
	p.dir(filepath.Dir(rel))
	if err := os.Link(p.path(existing), p.path(rel)); err != nil {
		p.t.Fatal(err)
	}
}

// meta writes the conda-meta record of a package and returns its size
func (p *prefix) meta(dist, name string, files ...string) int64 {
	p.t.Helper()
	rec := map[string]any{"files": files}
	if name != "" {
		rec["name"] = name
	}
	data, err := json.Marshal(rec)
	if err != nil {
		p.t.Fatal(err)
	}
	if err := os.WriteFile(p.path("conda-meta/"+dist+".json"), data, 0o644); err != nil {
		p.t.Fatal(err)
	}
	return int64(len(data))
}

// pkg writes one file and the conda-meta record owning it, so that the whole
// package is exactly size bytes
func (p *prefix) pkg(name string, size int64) {
	p.t.Helper()
	rel := "lib/" + name + ".so"
	n := p.meta(name+"-1.0-0", name, rel)
	p.file(rel, size-n)
}

func (p *prefix) config(slots int, excludes ...string) config {
	cfg := config{src: p.src, out: p.out, slots: slots, maxLayer: defaultMaxLayerSize, ownLayer: defaultOwnLayerSize, excludes: map[string]bool{}}
	for _, e := range excludes {
		cfg.excludes[e] = true
	}
	return cfg
}

// layerize runs the tool and returns the layer plan
func (p *prefix) layerize(cfg config) string {
	p.t.Helper()
	var out bytes.Buffer
	if err := layerize(cfg, &out); err != nil {
		p.t.Fatal(err)
	}
	return out.String()
}

// layered is the path of rel in a slot directory
func (p *prefix) layered(slot, rel string) string {
	return filepath.Join(p.out, slot, p.src, rel)
}

// slotOf returns the slot containing rel, failing if rel is not in exactly one slot
func (p *prefix) slotOf(rel string) string {
	p.t.Helper()
	entries, err := os.ReadDir(p.out)
	if err != nil {
		p.t.Fatal(err)
	}
	var found []string
	for _, e := range entries {
		if _, err := os.Lstat(p.layered(e.Name(), rel)); err == nil {
			found = append(found, e.Name())
		}
	}
	if len(found) != 1 {
		p.t.Fatalf("expected %s in one slot, found in %v", rel, found)
	}
	return found[0]
}

func (p *prefix) assertAbsent(rel string) {
	p.t.Helper()
	entries, err := os.ReadDir(p.out)
	if err != nil {
		p.t.Fatal(err)
	}
	for _, e := range entries {
		if _, err := os.Lstat(p.layered(e.Name(), rel)); err == nil {
			p.t.Fatalf("expected %s in no slot, found in %s", rel, e.Name())
		}
	}
}

// planRows returns the lines of the plan between the markers
func planRows(t *testing.T, out string) []string {
	t.Helper()
	lines := strings.Split(strings.TrimSuffix(out, "\n"), "\n")
	if lines[0] != ">> CONDA_LAYERS_START" || lines[len(lines)-1] != "<< CONDA_LAYERS_END" {
		t.Fatalf("missing plan markers:\n%s", out)
	}
	return lines[1 : len(lines)-1]
}

func lstat(t *testing.T, path string) fs.FileInfo {
	t.Helper()
	fi, err := os.Lstat(path)
	if err != nil {
		t.Fatal(err)
	}
	return fi
}

func readlink(t *testing.T, path string) string {
	t.Helper()
	target, err := os.Readlink(path)
	if err != nil {
		t.Fatal(err)
	}
	return target
}

func chtimes(t *testing.T, path string, mtime time.Time) {
	t.Helper()
	if err := os.Chtimes(path, mtime, mtime); err != nil {
		t.Fatal(err)
	}
}

func chmod(t *testing.T, path string, mode fs.FileMode) {
	t.Helper()
	if err := os.Chmod(path, mode); err != nil {
		t.Fatal(err)
	}
}

func requireRoot(t *testing.T) {
	t.Helper()
	if os.Geteuid() != 0 {
		t.Skip("requires root")
	}
}
