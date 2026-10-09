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
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"time"
)

// modeBits are the mode bits applied with chmod, including setuid, setgid and sticky
const modeBits = fs.ModePerm | fs.ModeSetuid | fs.ModeSetgid | fs.ModeSticky

type mover struct {
	src string
	out string
	inv *inventory
	// directories created in the layers: their final mode and times are
	// applied once everything has been moved into them
	dirs []createdDir
}

type createdDir struct {
	path string
	meta *node
}

// move moves every path of each layer into --out/NN/<src>/<relpath>, so that only
// the directories of the layers are created
func (m *mover) move(p *plan) error {
	if err := os.MkdirAll(m.out, 0o755); err != nil {
		return err
	}
	for i, l := range p.layers {
		if err := m.moveLayer(filepath.Join(m.out, slotName(i)), l); err != nil {
			return err
		}
	}
	// deepest directories first, so that a read-only parent never blocks its children
	for i := len(m.dirs) - 1; i >= 0; i-- {
		d := m.dirs[i]
		if err := os.Chmod(d.path, d.meta.mode&modeBits); err != nil {
			return err
		}
		// a zero access time is left unchanged
		if err := os.Chtimes(d.path, time.Time{}, d.meta.mtime); err != nil {
			return err
		}
	}
	return nil
}

// moveLayer renames each path of the layer into the slot. The files are all in the
// writable layer of the build step, so rename() never crosses a filesystem: overlayfs
// copies up a file of a lower layer, and directories are created, never renamed
func (m *mover) moveLayer(slot string, l *unit) error {
	// the prefix and its parents are 0755 and owned by the user running the tool,
	// root in a container build, as the COPY of v2 creates them: of the source
	// prefix (0777 in the mamba images) only the times are kept. The chmod also clears
	// the setgid bit that mkdir inherits from / in a rootless BuildKit step
	parents := filepath.Join(slot, filepath.Dir(m.src))
	if err := os.MkdirAll(parents, 0o755); err != nil {
		return err
	}
	for d := parents; d != slot; d = filepath.Dir(d) {
		if err := os.Chmod(d, 0o755); err != nil {
			return err
		}
	}
	root := *m.inv.root
	root.mode = fs.ModeDir | 0o755
	root.uid, root.gid = uint32(os.Geteuid()), uint32(os.Getegid())
	prefix := filepath.Join(slot, m.src)
	if err := m.mkdir(prefix, &root); err != nil {
		return err
	}
	made := map[string]bool{".": true}
	for _, a := range l.atoms {
		for _, n := range a.nodes {
			if err := m.place(prefix, made, n); err != nil {
				return err
			}
		}
	}
	return nil
}

// place moves a path into the layer, an originally empty directory is recreated
func (m *mover) place(prefix string, made map[string]bool, n *node) error {
	if n.mode.IsDir() {
		return m.ensureDir(prefix, made, n.rel)
	}
	if err := m.ensureDir(prefix, made, path.Dir(n.rel)); err != nil {
		return err
	}
	return os.Rename(filepath.Join(m.src, n.rel), filepath.Join(prefix, n.rel))
}

// ensureDir creates the directory rel and its missing parents below prefix,
// with the owner of the corresponding source directories
func (m *mover) ensureDir(prefix string, made map[string]bool, rel string) error {
	if made[rel] {
		return nil
	}
	if err := m.ensureDir(prefix, made, path.Dir(rel)); err != nil {
		return err
	}
	made[rel] = true
	return m.mkdir(filepath.Join(prefix, rel), m.inv.dirs[rel])
}

// mkdir creates a directory owned as meta. It stays writable until the move is
// complete: its final mode and times are set at the end
func (m *mover) mkdir(dir string, meta *node) error {
	if err := os.Mkdir(dir, 0o700); err != nil {
		return err
	}
	m.dirs = append(m.dirs, createdDir{dir, meta})
	return os.Lchown(dir, int(meta.uid), int(meta.gid))
}
