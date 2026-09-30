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
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"
	"syscall"
)

// modeBits are the mode bits applied with chmod, including setuid, setgid and sticky
const modeBits = fs.ModePerm | fs.ModeSetuid | fs.ModeSetgid | fs.ModeSticky

type mover struct {
	src    string
	out    string
	inv    *inventory
	rename renameFunc
	// directories created in the layers: their final mode and times are
	// applied once everything has been moved into them
	dirs []createdDir
}

type createdDir struct {
	path string
	meta *node
}

// move creates one directory for each slot and moves every atom of each layer into
// --out/NN/<src>/<relpath>. Unused slot directories are left empty
func (m *mover) move(p *plan, slots int) error {
	if err := os.MkdirAll(m.out, 0o755); err != nil {
		return err
	}
	for i := 0; i < slots; i++ {
		if err := mkdirMode(filepath.Join(m.out, slotName(i, slots)), 0o755); err != nil {
			return err
		}
	}
	for i, l := range p.layers {
		if err := m.moveLayer(filepath.Join(m.out, slotName(i, slots)), l); err != nil {
			return err
		}
	}
	// deepest directories first, so that a read-only parent never blocks its children
	for i := len(m.dirs) - 1; i >= 0; i-- {
		d := m.dirs[i]
		if err := os.Chmod(d.path, d.meta.mode&modeBits); err != nil {
			return err
		}
		// directories are never symlinks, so following symlinks is harmless here
		if err := os.Chtimes(d.path, d.meta.atime, d.meta.mtime); err != nil {
			return err
		}
	}
	return nil
}

func (m *mover) moveLayer(slot string, l *layer) error {
	// the prefix and its parents are 0755 root:root, as the COPY of v2 creates them
	cur := slot
	for _, part := range strings.Split(strings.TrimPrefix(path.Dir(m.src), "/"), "/") {
		if part == "" {
			continue
		}
		cur = filepath.Join(cur, part)
		if err := mkdirMode(cur, 0o755); err != nil {
			return err
		}
		// only root can give a directory away, as is the case in a container build
		if os.Geteuid() == 0 {
			if err := os.Lchown(cur, 0, 0); err != nil {
				return err
			}
		}
	}
	// whatever the mode and owner of the source prefix (0777 in the mamba images),
	// only its times are kept
	root := *m.inv.root
	root.mode = fs.ModeDir | 0o755
	if os.Geteuid() == 0 {
		root.uid, root.gid = 0, 0
	}
	prefix := filepath.Join(slot, m.src)
	if err := m.mkdir(prefix, &root); err != nil {
		return err
	}
	made := map[string]bool{".": true}
	for _, a := range l.atoms {
		if err := m.moveAtom(prefix, made, a); err != nil {
			return err
		}
	}
	return nil
}

func (m *mover) moveAtom(prefix string, made map[string]bool, a *atom) error {
	first := ""
	copied := false
	for _, n := range a.nodes {
		if n.mode.IsDir() {
			// an originally empty directory is recreated, not moved
			if err := m.ensureDir(prefix, made, n.rel); err != nil {
				return err
			}
			continue
		}
		if err := m.ensureDir(prefix, made, path.Dir(n.rel)); err != nil {
			return err
		}
		src := filepath.Join(m.src, n.rel)
		dst := filepath.Join(prefix, n.rel)
		// once the atom has been copied, renaming its other paths would move the
		// original inode away from the copy, so they take the fallback as well
		var err error = syscall.EXDEV
		if !copied {
			err = m.rename(src, dst)
		}
		if errors.Is(err, syscall.EXDEV) {
			// the path comes from another filesystem: copy it, or link it to the
			// path of the same atom already placed to keep the hardlink, then remove it
			if first != "" {
				err = os.Link(first, dst)
			} else {
				err = copyNode(src, dst, n)
				copied = true
			}
			if err == nil {
				err = os.Remove(src)
			}
		}
		if err != nil {
			return err
		}
		first = dst
	}
	return nil
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
	meta := m.inv.dirs[rel]
	if meta == nil {
		return fmt.Errorf("directory missing from the inventory: %s", rel)
	}
	if err := m.mkdir(filepath.Join(prefix, rel), meta); err != nil {
		return err
	}
	made[rel] = true
	return nil
}

// mkdir creates a directory owned as meta. It stays writable until the move is
// complete: its final mode and times are set at the end
func (m *mover) mkdir(dir string, meta *node) error {
	if err := os.Mkdir(dir, 0o700); err != nil {
		return err
	}
	if err := os.Lchown(dir, int(meta.uid), int(meta.gid)); err != nil {
		return err
	}
	m.dirs = append(m.dirs, createdDir{dir, meta})
	return nil
}

// mkdirMode creates a directory with exactly the given mode, whatever the umask
func mkdirMode(dir string, mode fs.FileMode) error {
	if err := os.Mkdir(dir, mode); err != nil {
		return err
	}
	return os.Chmod(dir, mode)
}

// copyNode copies a regular file or a symlink keeping its mode, owner and times
func copyNode(src, dst string, n *node) error {
	switch {
	case n.mode.IsRegular():
		if err := copyFile(src, dst); err != nil {
			return err
		}
	case n.mode&fs.ModeSymlink != 0:
		target, err := os.Readlink(src)
		if err != nil {
			return err
		}
		if err := os.Symlink(target, dst); err != nil {
			return err
		}
	default:
		return fmt.Errorf("cannot copy %s: unsupported file type %s", src, n.mode.Type())
	}
	if err := os.Lchown(dst, int(n.uid), int(n.gid)); err != nil {
		return err
	}
	if n.mode&fs.ModeSymlink != 0 {
		return lutimes(dst, n.atime, n.mtime)
	}
	// chmod after chown, because chown clears the setuid and setgid bits
	if err := os.Chmod(dst, n.mode&modeBits); err != nil {
		return err
	}
	return os.Chtimes(dst, n.atime, n.mtime)
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		return err
	}
	return out.Close()
}
