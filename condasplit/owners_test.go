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
	"strings"
	"testing"
)

func TestOwnership(t *testing.T) {
	p := newPrefix(t)
	p.meta("a-1.0-0", "a", "bin/a", "share/common", "missing/file")
	p.meta("b-2.0-0", "b", "bin/b", "share/common", "./lib/b")
	p.meta("c-3.0-0", "", "bin/c")
	p.file("bin/a", 30)
	p.file("bin/b", 20)
	p.file("bin/c", 10)
	p.file("share/common", 5)
	p.file("lib/b", 5)
	p.file("conda-meta/history", 3)
	p.file(".messages.txt", 2)

	inv, err := scan(p.src, nil)
	if err != nil {
		t.Fatal(err)
	}
	own, err := readOwners(p.src, inv)
	if err != nil {
		t.Fatal(err)
	}
	expected := map[string]string{
		"bin/a":                   "a",
		"bin/b":                   "b",
		"bin/c":                   "c-3.0-0", // no name in the record: the dist name is used
		"share/common":            "a",       // first claim wins
		"lib/b":                   "b",
		"conda-meta/a-1.0-0.json": "a",
		"conda-meta/b-2.0-0.json": "b",
		"conda-meta/c-3.0-0.json": "c-3.0-0",
	}
	for rel, name := range expected {
		if own.owner[rel] != name {
			t.Errorf("owner of %s: expected %q, got %q", rel, name, own.owner[rel])
		}
	}
	if len(own.owner) != len(expected) {
		t.Errorf("unexpected owned paths: %v", own.owner)
	}
	if own.clobbers != 1 || own.packages != 3 {
		t.Errorf("expected 1 clobber and 3 packages, got %d and %d", own.clobbers, own.packages)
	}

	// every package above own-layer-size gets its layer, unowned paths go last
	cfg := p.config(8)
	cfg.ownLayer = 0
	out := p.layerize(cfg)
	if !strings.Contains(out, " packages=3 clobbers=1 ") {
		t.Errorf("unexpected plan header:\n%s", out)
	}
	rest := p.slotOf("conda-meta/history")
	if p.slotOf(".messages.txt") != rest || !strings.HasSuffix(planRows(t, out)[4], "(unowned)") || rest != "03" {
		t.Errorf("unowned paths not in the trailing layer:\n%s", out)
	}
	if p.slotOf("share/common") != p.slotOf("bin/a") || p.slotOf("conda-meta/a-1.0-0.json") != p.slotOf("bin/a") {
		t.Errorf("share/common and the record of a are not in the layer of a")
	}
	if p.slotOf("lib/b") != p.slotOf("bin/b") || p.slotOf("bin/b") == p.slotOf("bin/a") {
		t.Errorf("package b is not in its own layer")
	}
}

func TestUnownedIsCapped(t *testing.T) {
	p := newPrefix(t)
	p.pkg("a", 100*MB)
	p.file("share/x", 300*MB)
	p.file("share/y", 300*MB)
	p.file("share/z", 100*MB)

	rows := planRows(t, p.layerize(p.config(32)))
	expected := []string{
		"slots=3/32 files=5 size=800.0MB packages=1 clobbers=0 unowned=700.0MB max-layer=500MB own-layer=50MB",
		"00  100.0MB      2  a",
		"01  300.0MB      1  (unowned)#0",
		"02  400.0MB      2  (unowned)#1",
	}
	if strings.Join(rows, "\n") != strings.Join(expected, "\n") {
		t.Errorf("unexpected plan:\n%s", strings.Join(rows, "\n"))
	}
}
