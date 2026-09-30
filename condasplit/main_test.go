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
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRun(t *testing.T) {
	p := newPrefix(t)
	p.pkg("a", 60*MB)
	p.pkg("b", 1*MB)
	p.file("pkgs/b-1.0-0/lib/b.so", 10)
	p.file("share/tmp/cache", 10)

	var stdout, stderr bytes.Buffer
	args := []string{"--src", p.src, "--out", p.out, "--slots", "32", "--exclude", "pkgs", "--exclude", "share/tmp/cache"}
	if code := run(args, &stdout, &stderr); code != 0 {
		t.Fatalf("expected exit 0, got %d: %s", code, stderr.String())
	}
	if stderr.Len() != 0 {
		t.Errorf("unexpected stderr: %s", stderr.String())
	}
	// the own layers, then the shared layers, then the unowned leftovers
	rows := planRows(t, stdout.String())
	expected := []string{
		"00   60.0MB      2  a",
		"01    1.0MB      2  b",
		"02    0.0MB      1  (unowned)",
	}
	if !strings.HasPrefix(rows[0], "slots=3/32 files=5 ") || strings.Join(rows[1:], "\n") != strings.Join(expected, "\n") {
		t.Errorf("unexpected plan:\n%s", stdout.String())
	}
	// one directory for each layer, none for the unused slots
	entries, err := os.ReadDir(p.out)
	if err != nil || len(entries) != 3 || entries[0].Name() != "00" || entries[2].Name() != "02" {
		t.Fatalf("expected 3 layer directories: %v %v", entries, err)
	}
	if !lstat(t, p.path("pkgs/b-1.0-0/lib/b.so")).Mode().IsRegular() || !lstat(t, p.path("share/tmp/cache")).Mode().IsRegular() {
		t.Errorf("excluded paths not left in place")
	}
}

func TestParseArgs(t *testing.T) {
	cfg, err := parseArgs([]string{"--src", "/opt/conda/", "--out", "/layers", "--slots", "32", "--exclude", "pkgs", "--exclude", "./share/x/"})
	if err != nil {
		t.Fatal(err)
	}
	if cfg.src != "/opt/conda" || cfg.out != "/layers" || cfg.slots != 32 {
		t.Errorf("unexpected config: %+v", cfg)
	}
	if cfg.maxLayer != 500_000_000 || cfg.ownLayer != 50_000_000 {
		t.Errorf("unexpected defaults: %d %d", cfg.maxLayer, cfg.ownLayer)
	}
	if len(cfg.excludes) != 2 || !cfg.excludes["pkgs"] || !cfg.excludes["share/x"] {
		t.Errorf("unexpected excludes: %v", cfg.excludes)
	}
	cfg, err = parseArgs([]string{"--src", "/opt/conda", "--out", "/layers", "--slots", "8", "--max-layer-size", "1000", "--own-layer-size", "100"})
	if err != nil || cfg.maxLayer != 1000 || cfg.ownLayer != 100 {
		t.Errorf("unexpected config: %+v %v", cfg, err)
	}
}

func TestErrors(t *testing.T) {
	p := newPrefix(t)
	p.pkg("a", 1000)
	noMeta, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(noMeta, "file"), nil, 0o644); err != nil {
		t.Fatal(err)
	}
	bad := newPrefix(t)
	bad.pkg("a", 1000)
	if err := os.WriteFile(bad.path("conda-meta/b-1.0-0.json"), []byte(`{"name": "b", "files": [`), 0o644); err != nil {
		t.Fatal(err)
	}

	tests := []struct {
		name  string
		args  []string
		error string
	}{
		{"relative src", []string{"--src", "opt/conda", "--out", p.out, "--slots", "4"}, "--src must be an absolute path: opt/conda"},
		{"missing src option", []string{"--out", p.out, "--slots", "4"}, "--src must be an absolute path"},
		{"missing src", []string{"--src", filepath.Join(noMeta, "nope"), "--out", p.out, "--slots", "4"}, "no such file or directory"},
		{"src is a file", []string{"--src", filepath.Join(noMeta, "file"), "--out", p.out, "--slots", "4"}, "missing conda-meta directory in " + filepath.Join(noMeta, "file")},
		{"missing conda-meta", []string{"--src", noMeta, "--out", p.out, "--slots", "4"}, "missing conda-meta directory in " + noMeta},
		{"malformed json", []string{"--src", bad.src, "--out", bad.out, "--slots", "4"}, "cannot parse conda-meta/b-1.0-0.json"},
		{"missing out", []string{"--src", p.src, "--slots", "4"}, "missing required option --out"},
		{"missing slots", []string{"--src", p.src, "--out", p.out}, "--slots"},
		{"unknown option", []string{"--src", p.src, "--out", p.out, "--slots", "4", "--foo"}, "flag provided but not defined: -foo"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			if code := run(tt.args, &stdout, &stderr); code == 0 {
				t.Fatalf("expected a non-zero exit")
			}
			msg := stderr.String()
			if strings.Count(msg, "\n") != 1 || !strings.HasPrefix(msg, "condasplit: ") || !strings.Contains(msg, tt.error) {
				t.Errorf("expected one line error containing %q, got %q", tt.error, msg)
			}
			if stdout.Len() != 0 {
				t.Errorf("unexpected stdout: %s", stdout.String())
			}
		})
	}

	// nothing is moved when the plan cannot be made
	if !lstat(t, bad.path("lib/a.so")).Mode().IsRegular() {
		t.Errorf("prefix modified after an error")
	}
	for _, out := range []string{p.out, bad.out} {
		if _, err := os.Lstat(out); !errors.Is(err, fs.ErrNotExist) {
			t.Errorf("output created after an error: %s", out)
		}
	}
}

func TestHelp(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run([]string{"--help"}, &stdout, &stderr); code != 0 || !strings.HasPrefix(stdout.String(), "Usage: condasplit") {
		t.Errorf("unexpected help output: %d %q", code, stdout.String())
	}
}
