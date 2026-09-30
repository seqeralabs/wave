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
	"fmt"
	"strings"
	"testing"
)

func TestSplitLargePackage(t *testing.T) {
	p := newPrefix(t)
	var files []string
	for i := 0; i < 5; i++ {
		files = append(files, fmt.Sprintf("lib/f%d", i))
	}
	p.meta("big-1.0-0", "big", files...)
	for _, f := range files {
		p.file(f, 200*MB)
	}
	p.pkg("small", 1*MB)

	cfg := p.config(32)
	rows := planRows(t, p.layerize(cfg, nil))
	expected := []string{
		"00  400.0MB      3  big#0",
		"01  400.0MB      2  big#1",
		"02  200.0MB      1  big#2",
		"03    1.0MB      2  small",
	}
	if strings.Join(rows[1:], "\n") != strings.Join(expected, "\n") {
		t.Fatalf("unexpected plan:\n%s", strings.Join(rows, "\n"))
	}
	// chunks take the atoms in path order
	for rel, slot := range map[string]string{"conda-meta/big-1.0-0.json": "00", "lib/f0": "00", "lib/f1": "00", "lib/f2": "01", "lib/f3": "01", "lib/f4": "02"} {
		if got := p.slotOf(rel); got != slot {
			t.Errorf("%s: expected slot %s, got %s", rel, slot, got)
		}
	}
}

func TestOversizedFile(t *testing.T) {
	p := newPrefix(t)
	p.meta("gpu-1.0-0", "gpu", "lib/huge.so", "lib/small.so")
	p.file("lib/huge.so", 612_400_000)
	p.file("lib/small.so", 1*MB)

	var stdout, stderr bytes.Buffer
	code := run([]string{"--src", p.src, "--out", p.out, "--slots", "32"}, &stdout, &stderr)
	if code != 0 {
		t.Fatalf("expected exit 0 with warnings, got %d: %s", code, stderr.String())
	}
	rows := planRows(t, stdout.String())
	expected := []string{
		"00  612.4MB      1  gpu#0",
		"01    1.0MB      2  gpu#1",
		"WARN oversized file lib/huge.so 612.4MB",
	}
	if strings.Join(rows[1:], "\n") != strings.Join(expected, "\n") {
		t.Fatalf("unexpected plan:\n%s", strings.Join(rows, "\n"))
	}
	if p.slotOf("lib/huge.so") != "00" || p.slotOf("lib/small.so") != "01" {
		t.Errorf("the oversized file is not isolated")
	}
}

// packing builds a prefix with packages above and below the own-layer threshold
func packing(t *testing.T, reverse bool) *prefix {
	p := newPrefix(t)
	sizes := map[string]int64{"A": 120 * MB, "B": 60 * MB, "F": 50 * MB, "C": 49 * MB}
	names := []string{"A", "B", "F", "C"}
	for i := 1; i <= 12; i++ {
		name := fmt.Sprintf("p%02d", i)
		sizes[name] = 45 * MB
		names = append(names, name)
	}
	// the creation order changes the inode numbers, never the plan
	if reverse {
		for i, j := 0, len(names)-1; i < j; i, j = i+1, j-1 {
			names[i], names[j] = names[j], names[i]
		}
	}
	for _, name := range names {
		p.pkg(name, sizes[name])
	}
	return p
}

func TestPacking(t *testing.T) {
	p := packing(t, false)
	cfg := p.config(32)
	out := p.layerize(cfg, nil)
	expected := []string{
		"slots=5/32 files=32 size=819.0MB packages=16 clobbers=0 unowned=0.0MB max-layer=500MB own-layer=50MB",
		"00  120.0MB      2  A",
		"01   60.0MB      2  B",
		"02   50.0MB      2  F",
		"03  499.0MB     22  C p01 p02 p03 p04 p05 +5",
		"04   90.0MB      4  p11 p12",
	}
	if rows := planRows(t, out); strings.Join(rows, "\n") != strings.Join(expected, "\n") {
		t.Fatalf("unexpected plan:\n%s", out)
	}

	if p.slotOf("lib/C.so") != "03" || p.slotOf("lib/p12.so") != "04" {
		t.Errorf("packages not moved into their layer")
	}

	// the same content gives the same plan
	again := packing(t, true)
	if other := again.layerize(again.config(32), nil); other != out {
		t.Errorf("plan is not deterministic:\n%s\n---\n%s", out, other)
	}
}

func TestPackingRespectsCap(t *testing.T) {
	p := newPrefix(t)
	for i := 0; i < 40; i++ {
		p.pkg(fmt.Sprintf("p%02d", i), int64(30+i)*MB)
	}
	cfg := p.config(32)
	inv, err := scan(p.src, nil)
	if err != nil {
		t.Fatal(err)
	}
	own, err := readOwners(p.src, inv)
	if err != nil {
		t.Fatal(err)
	}
	plan := makePlan(inv, own, cfg)
	for i, l := range plan.layers {
		if l.size > cfg.maxLayer {
			t.Errorf("layer %d exceeds the cap: %d", i, l.size)
		}
		big := false
		for _, label := range l.labels {
			var n int
			fmt.Sscanf(label, "p%d", &n)
			big = big || 30+n >= 50
		}
		if big && len(l.labels) != 1 {
			t.Errorf("layer %d shares a package above own-layer-size: %v", i, l.labels)
		}
	}
	if plan.merges != 0 || plan.files != 80 {
		t.Errorf("unexpected plan: %d merges, %d files", plan.merges, plan.files)
	}
}

func TestSlotOverflow(t *testing.T) {
	tests := []struct {
		slots    int
		expected []string
	}{
		{4, []string{
			"00  450.0MB      2  P1",
			"01  400.0MB      2  P2",
			"02  300.0MB      2  P3",
			"03  350.0MB      4  P4 P5",
			"WARN slots exceeded: merged 1 layers",
		}},
		{3, []string{
			"00  450.0MB      2  P1",
			"01  400.0MB      2  P2",
			"02  650.0MB      6  P3 P4 P5",
			"WARN slots exceeded: merged 2 layers",
			"WARN merged layer 02 650.0MB exceeds max-layer 500MB",
		}},
	}
	for _, tt := range tests {
		t.Run(fmt.Sprintf("slots=%d", tt.slots), func(t *testing.T) {
			p := newPrefix(t)
			for i, size := range []int64{450, 400, 300, 200, 150} {
				p.pkg(fmt.Sprintf("P%d", i+1), size*MB)
			}
			rows := planRows(t, p.layerize(p.config(tt.slots), nil))
			if strings.Join(rows[1:], "\n") != strings.Join(tt.expected, "\n") {
				t.Fatalf("unexpected plan:\n%s", strings.Join(rows, "\n"))
			}
			if p.slotOf("lib/P5.so") != p.slotOf("lib/P4.so") {
				t.Errorf("merged packages are not in the same slot")
			}
		})
	}
}

// the two smallest layers are merged, also when they are not the last ones
func TestSlotOverflowMergesSmallest(t *testing.T) {
	p := newPrefix(t)
	p.pkg("P1", 450*MB)
	p.pkg("P2", 60*MB)
	p.pkg("P3", 55*MB)
	for i := 1; i <= 9; i++ {
		p.pkg(fmt.Sprintf("s%d", i), 45*MB)
	}
	p.file("share/x", 300*MB)

	rows := planRows(t, p.layerize(p.config(4), nil))
	expected := []string{
		"00  450.0MB      2  P1",
		"01  115.0MB      4  P2 P3",
		"02  405.0MB     18  s1 s2 s3 s4 s5 s6 +3",
		"03  300.0MB      1  (unowned)",
		"WARN slots exceeded: merged 1 layers",
	}
	if strings.Join(rows[1:], "\n") != strings.Join(expected, "\n") {
		t.Fatalf("unexpected plan:\n%s", strings.Join(rows, "\n"))
	}
}

func TestSlotNames(t *testing.T) {
	for _, tt := range []struct {
		i, slots int
		name     string
	}{{0, 1, "00"}, {7, 32, "07"}, {31, 32, "31"}, {5, 101, "005"}} {
		if got := slotName(tt.i, tt.slots); got != tt.name {
			t.Errorf("slotName(%d, %d): expected %s, got %s", tt.i, tt.slots, tt.name, got)
		}
	}
}
