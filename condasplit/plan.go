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
	"cmp"
	"fmt"
	"maps"
	"slices"
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

// unit is a package, or a chunk of a package, that is never split across layers.
// A layer is a unit made of one or more of them
type unit struct {
	labels []string
	atoms  []*atom
	size   int64
}

func (u *unit) add(o unit) {
	u.labels = append(u.labels, o.labels...)
	u.atoms = append(u.atoms, o.atoms...)
	u.size += o.size
}

func (u *unit) files() (n int) {
	for _, a := range u.atoms {
		n += len(a.nodes)
	}
	return n
}

type plan struct {
	layers   []*unit
	files    int
	size     int64
	packages int
	clobbers int
	unowned  int64
	warnings []string
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

	// packages at least own-layer-size get a layer for each of their chunks,
	// the others are packed together
	var alone, shared []unit
	for _, name := range slices.Sorted(maps.Keys(byPackage)) {
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
		p.layers = append(p.layers, &u)
	}
	// first-fit decreasing
	var bins []*unit
	for _, u := range shared {
		i := slices.IndexFunc(bins, func(b *unit) bool { return b.size+u.size <= cfg.maxLayer })
		if i < 0 {
			bins = append(bins, &unit{})
			i = len(bins) - 1
		}
		bins[i].add(u)
	}
	p.layers = append(p.layers, bins...)
	for _, u := range p.chunk(unownedLabel, rest, cfg.maxLayer) {
		p.layers = append(p.layers, &u)
	}
	p.fitSlots(cfg.slots, cfg.maxLayer)
	return p
}

// sortUnits orders units by size, largest first, then by label
func sortUnits(units []unit) {
	slices.SortFunc(units, func(a, b unit) int {
		return cmp.Or(cmp.Compare(b.size, a.size), strings.Compare(a.labels[0], b.labels[0]))
	})
}

// chunk cuts a list of atoms, in path order, into units of at most limit bytes.
// An atom larger than limit becomes a unit on its own and is reported as oversized
func (p *plan) chunk(label string, atoms []*atom, limit int64) []unit {
	var units []unit
	var cur unit
	for _, a := range atoms {
		if a.size > limit {
			units = append(units, unit{atoms: []*atom{a}, size: a.size})
			p.warnings = append(p.warnings, fmt.Sprintf("WARN oversized file %s %.1fMB", a.path(), mb(a.size)))
			continue
		}
		if len(cur.atoms) > 0 && cur.size+a.size > limit {
			units = append(units, cur)
			cur = unit{}
		}
		cur.atoms = append(cur.atoms, a)
		cur.size += a.size
	}
	if len(cur.atoms) > 0 {
		units = append(units, cur)
	}
	for i := range units {
		units[i].labels = []string{label}
		if len(units) > 1 {
			units[i].labels[0] = fmt.Sprintf("%s#%d", label, i)
		}
	}
	return units
}

// fitSlots merges the two smallest layers until the layers fit the slots
func (p *plan) fitSlots(slots int, maxLayer int64) {
	merges := 0
	for ; len(p.layers) > slots; merges++ {
		bySize := slices.Clone(p.layers)
		slices.SortStableFunc(bySize, func(x, y *unit) int { return cmp.Compare(x.size, y.size) })
		a, b := slices.Index(p.layers, bySize[0]), slices.Index(p.layers, bySize[1])
		a, b = min(a, b), max(a, b)
		p.layers[a].add(*p.layers[b])
		p.layers = slices.Delete(p.layers, b, b+1)
	}
	if merges == 0 {
		return
	}
	p.warnings = append(p.warnings, fmt.Sprintf("WARN slots exceeded: merged %d layers", merges))
	for i, l := range p.layers {
		// packing never exceeds the cap, so a layer of several units over it was merged
		if len(l.labels) > 1 && l.size > maxLayer {
			p.warnings = append(p.warnings, fmt.Sprintf("WARN merged layer %s %.1fMB exceeds max-layer %sMB", slotName(i), mb(l.size), limitMB(maxLayer)))
		}
	}
}

// format renders the layer plan printed in the build log
func (p *plan) format(cfg config) string {
	var sb strings.Builder
	sb.WriteString(">> CONDA_LAYERS_START\n")
	fmt.Fprintf(&sb, "slots=%d/%d files=%d size=%.1fMB packages=%d clobbers=%d unowned=%.1fMB max-layer=%sMB own-layer=%sMB\n",
		len(p.layers), cfg.slots, p.files, mb(p.size), p.packages, p.clobbers, mb(p.unowned), limitMB(cfg.maxLayer), limitMB(cfg.ownLayer))
	for i, l := range p.layers {
		labels, more := l.labels, ""
		if len(labels) > maxLabels {
			labels, more = labels[:maxLabels], fmt.Sprintf(" +%d", len(labels)-maxLabels)
		}
		fmt.Fprintf(&sb, "%s %6.1fMB %6d  %s%s\n", slotName(i), mb(l.size), l.files(), strings.Join(labels, " "), more)
	}
	for _, w := range p.warnings {
		sb.WriteString(w + "\n")
	}
	sb.WriteString("<< CONDA_LAYERS_END\n")
	return sb.String()
}

// slotName is the zero padded name of the slot directory, 00, 01, ...
func slotName(i int) string { return fmt.Sprintf("%02d", i) }

// mb converts bytes to decimal megabytes
func mb(n int64) float64 { return float64(n) / 1e6 }

func limitMB(n int64) string { return strconv.FormatFloat(mb(n), 'f', -1, 64) }
