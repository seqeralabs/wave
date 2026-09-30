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
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"
)

// maxReported is the number of offending paths listed when the verify step fails
const maxReported = 20

// node is the lstat metadata of one path of the prefix
type node struct {
	rel   string // path relative to the prefix, "." for the prefix itself
	mode  fs.FileMode
	size  int64
	uid   uint32
	gid   uint32
	dev   uint64
	ino   uint64
	atime time.Time
	mtime time.Time
}

type inventory struct {
	root  *node            // the prefix directory itself
	dirs  map[string]*node // every directory below the prefix, by relative path
	nodes []*node          // every non-directory and every empty directory, sorted by path
}

func newNode(rel string, fi fs.FileInfo) (*node, error) {
	st, ok := fi.Sys().(*syscall.Stat_t)
	if !ok {
		return nil, fmt.Errorf("cannot read file metadata of %s", rel)
	}
	return &node{
		rel:   rel,
		mode:  fi.Mode(),
		size:  fi.Size(),
		uid:   st.Uid,
		gid:   st.Gid,
		dev:   uint64(st.Dev),
		ino:   st.Ino,
		atime: statAtime(st),
		mtime: fi.ModTime(),
	}, nil
}

// walk visits every path below src without following symlinks, so a symlink to a
// directory is reported as a symlink and not descended, and skips excluded subtrees
func walk(src string, excludes map[string]bool, fn func(rel string, d fs.DirEntry) error) error {
	prefix := src + "/"
	return filepath.WalkDir(src, func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if p == src {
			return nil
		}
		rel := strings.TrimPrefix(p, prefix)
		if excludes[rel] {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		return fn(rel, d)
	})
}

// scan records the metadata of every path of the prefix. A directory is empty
// when it has no children other than excluded ones: it is then recreated in a layer
func scan(src string, excludes map[string]bool) (*inventory, error) {
	fi, err := os.Lstat(src)
	if errors.Is(err, fs.ErrNotExist) {
		return nil, fmt.Errorf("--src directory does not exist: %s", src)
	}
	if err != nil {
		return nil, err
	}
	if !fi.IsDir() {
		return nil, fmt.Errorf("--src is not a directory: %s", src)
	}
	inv := &inventory{dirs: map[string]*node{}}
	if inv.root, err = newNode(".", fi); err != nil {
		return nil, err
	}
	children := map[string]int{}
	err = walk(src, excludes, func(rel string, d fs.DirEntry) error {
		children[path.Dir(rel)]++
		info, err := d.Info()
		if err != nil {
			return err
		}
		n, err := newNode(rel, info)
		if err != nil {
			return err
		}
		if d.IsDir() {
			inv.dirs[rel] = n
		} else {
			inv.nodes = append(inv.nodes, n)
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	for rel, n := range inv.dirs {
		if children[rel] == 0 {
			inv.nodes = append(inv.nodes, n)
		}
	}
	sort.Slice(inv.nodes, func(i, j int) bool { return inv.nodes[i].rel < inv.nodes[j].rel })
	return inv, nil
}

// verify checks that only directories and excluded paths are left in the prefix
func verify(src string, excludes map[string]bool) error {
	var left []string
	count := 0
	err := walk(src, excludes, func(rel string, d fs.DirEntry) error {
		if !d.IsDir() {
			count++
			if len(left) < maxReported {
				left = append(left, rel)
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	if count == 0 {
		return nil
	}
	msg := fmt.Sprintf("verify failed, %d paths left in %s: %s", count, src, strings.Join(left, " "))
	if count > len(left) {
		msg += fmt.Sprintf(" (+%d more)", count-len(left))
	}
	return errors.New(msg)
}
