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
	"fmt"
	"sort"
	"strconv"
	"strings"
)

const (
	unownedLabel = "(unowned)"
	// number of package names shown for each layer in the plan
	maxLabels = 6
)

// atom is the smallest unit placed in a layer: a regular file with all its
// hardlinks, a symlink, an empty directory or any other non-directory path
type atom struct {
	nodes []*node // sorted by path
	size  int64   // counted once for all the hardlinks
}

func (a *atom) path() string { return a.nodes[0].rel }

// unit is a package, or a chunk of a package, that is never split across layers
type unit struct {
	label string
	atoms []*atom
	size  int64
}

type layer struct {
	labels []string
	atoms  []*atom
	size   int64
	files  int
	merged bool
}

func (l *layer) add(u unit) {
	l.labels = append(l.labels, u.label)
	l.atoms = append(l.atoms, u.atoms...)
	l.size += u.size
	for _, a := range u.atoms {
		l.files += len(a.nodes)
	}
}

func (l *layer) merge(o *layer) {
	l.labels = append(l.labels, o.labels...)
	l.atoms = append(l.atoms, o.atoms...)
	l.size += o.size
	l.files += o.files
	l.merged = true
}

type oversized struct {
	path string
	size int64
}

type plan struct {
	layers    []*layer
	files     int
	size      int64
	packages  int
	clobbers  int
	unowned   int64
	oversized []oversized
	merges    int
}

// makeAtoms groups the regular files by (dev, inode), so that hardlinks are never split
func makeAtoms(inv *inventory) []*atom {
	var atoms []*atom
	inodes := map[[2]uint64]*atom{}
	for _, n := range inv.nodes {
		if !n.mode.IsRegular() {
			atoms = append(atoms, &atom{nodes: []*node{n}})
			continue
		}
		key := [2]uint64{n.dev, n.ino}
		if a := inodes[key]; a != nil {
			a.nodes = append(a.nodes, n)
			continue
		}
		a := &atom{nodes: []*node{n}, size: n.size}
		inodes[key] = a
		atoms = append(atoms, a)
	}
	// nodes are sorted by path, so are the atoms by their first path
	return atoms
}

// atomOwner returns the owner of the first owned path of the atom, or an empty string
func atomOwner(a *atom, owner map[string]string) string {
	for _, n := range a.nodes {
		if name, found := owner[n.rel]; found {
			return name
		}
	}
	return ""
}

func makePlan(inv *inventory, own *ownership, cfg config) *plan {
	p := &plan{packages: own.packages, clobbers: own.clobbers}
	byPackage := map[string][]*atom{}
	packageSize := map[string]int64{}
	var rest []*atom
	for _, a := range makeAtoms(inv) {
		p.files += len(a.nodes)
		p.size += a.size
		name := atomOwner(a, own.owner)
		if name == "" {
			rest = append(rest, a)
			p.unowned += a.size
			continue
		}
		byPackage[name] = append(byPackage[name], a)
		packageSize[name] += a.size
	}
	names := make([]string, 0, len(byPackage))
	for name := range byPackage {
		names = append(names, name)
	}
	sort.Strings(names)

	// packages at least own-layer-size get a layer for each of their chunks,
	// the others are packed together
	var alone, shared []unit
	for _, name := range names {
		chunks := p.chunk(name, byPackage[name], cfg.maxLayer)
		if packageSize[name] >= cfg.ownLayer {
			alone = append(alone, chunks...)
		} else {
			shared = append(shared, chunks...)
		}
	}
	sortUnits(alone)
	sortUnits(shared)
	for _, u := range alone {
		p.layers = append(p.layers, newLayer(u))
	}
	// first-fit decreasing
	var bins []*layer
	for _, u := range shared {
		placed := false
		for _, b := range bins {
			if b.size+u.size <= cfg.maxLayer {
				b.add(u)
				placed = true
				break
			}
		}
		if !placed {
			bins = append(bins, newLayer(u))
		}
	}
	p.layers = append(p.layers, bins...)
	for _, u := range p.chunk(unownedLabel, rest, cfg.maxLayer) {
		p.layers = append(p.layers, newLayer(u))
	}
	p.fitSlots(cfg.slots)
	return p
}

func newLayer(u unit) *layer {
	l := &layer{}
	l.add(u)
	return l
}

// sortUnits orders units by size, largest first, then by label
func sortUnits(units []unit) {
	sort.Slice(units, func(i, j int) bool {
		if units[i].size != units[j].size {
			return units[i].size > units[j].size
		}
		return units[i].label < units[j].label
	})
}

// chunk cuts a list of atoms, in path order, into units of at most limit bytes.
// An atom larger than limit becomes a unit on its own and is reported as oversized
func (p *plan) chunk(label string, atoms []*atom, limit int64) []unit {
	var units []unit
	var cur unit
	flush := func() {
		if len(cur.atoms) > 0 {
			units = append(units, cur)
			cur = unit{}
		}
	}
	for _, a := range atoms {
		if a.size > limit {
			units = append(units, unit{atoms: []*atom{a}, size: a.size})
			p.oversized = append(p.oversized, oversized{a.path(), a.size})
			continue
		}
		if len(cur.atoms) > 0 && cur.size+a.size > limit {
			flush()
		}
		cur.atoms = append(cur.atoms, a)
		cur.size += a.size
	}
	flush()
	for i := range units {
		units[i].label = label
		if len(units) > 1 {
			units[i].label = fmt.Sprintf("%s#%d", label, i)
		}
	}
	return units
}

// fitSlots merges the two smallest layers until the layers fit the slots
func (p *plan) fitSlots(slots int) {
	for len(p.layers) > slots {
		order := make([]int, len(p.layers))
		for i := range order {
			order[i] = i
		}
		sort.SliceStable(order, func(i, j int) bool { return p.layers[order[i]].size < p.layers[order[j]].size })
		a, b := order[0], order[1]
		if a > b {
			a, b = b, a
		}
		p.layers[a].merge(p.layers[b])
		p.layers = append(p.layers[:b], p.layers[b+1:]...)
		p.merges++
	}
}

// format renders the layer plan printed in the build log
func (p *plan) format(cfg config) string {
	var sb strings.Builder
	sb.WriteString(">> CONDA_LAYERS_START\n")
	fmt.Fprintf(&sb, "slots=%d/%d files=%d size=%.1fMB packages=%d clobbers=%d unowned=%.1fMB max-layer=%sMB own-layer=%sMB\n",
		len(p.layers), cfg.slots, p.files, mb(p.size), p.packages, p.clobbers, mb(p.unowned), limitMB(cfg.maxLayer), limitMB(cfg.ownLayer))
	for i, l := range p.layers {
		labels := l.labels
		more := ""
		if len(labels) > maxLabels {
			more = fmt.Sprintf(" +%d", len(labels)-maxLabels)
			labels = labels[:maxLabels]
		}
		fmt.Fprintf(&sb, "%s %6.1fMB %6d  %s%s\n", slotName(i, cfg.slots), mb(l.size), l.files, strings.Join(labels, " "), more)
	}
	for _, o := range p.oversized {
		fmt.Fprintf(&sb, "WARN oversized file %s %.1fMB\n", o.path, mb(o.size))
	}
	if p.merges > 0 {
		fmt.Fprintf(&sb, "WARN slots exceeded: merged %d layers\n", p.merges)
	}
	for i, l := range p.layers {
		if l.merged && l.size > cfg.maxLayer {
			fmt.Fprintf(&sb, "WARN merged layer %s %.1fMB exceeds max-layer %sMB\n", slotName(i, cfg.slots), mb(l.size), limitMB(cfg.maxLayer))
		}
	}
	sb.WriteString("<< CONDA_LAYERS_END\n")
	return sb.String()
}

// slotName is the zero padded name of the slot directory, 00 ... N-1
func slotName(i, slots int) string {
	width := len(strconv.Itoa(slots - 1))
	if width < 2 {
		width = 2
	}
	return fmt.Sprintf("%0*d", width, i)
}

// mb converts bytes to decimal megabytes
func mb(n int64) float64 { return float64(n) / 1e6 }

func limitMB(n int64) string { return strconv.FormatFloat(mb(n), 'f', -1, 64) }
