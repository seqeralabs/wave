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
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"
)

func nlink(t *testing.T, path string) uint64 {
	t.Helper()
	return uint64(lstat(t, path).Sys().(*syscall.Stat_t).Nlink)
}

func owner(t *testing.T, path string) (uint32, uint32) {
	t.Helper()
	st := lstat(t, path).Sys().(*syscall.Stat_t)
	return st.Uid, st.Gid
}

func TestHardlinks(t *testing.T) {
	p := newPrefix(t)
	// within one package
	p.meta("a-1.0-0", "a", "bin/x", "lib/libx.so")
	p.file("lib/libx.so", 300*MB)
	p.hardlink("lib/libx.so", "bin/x")
	// across a package and an excluded path
	p.meta("b-1.0-0", "b", "lib/liby.so")
	p.file("pkgs/b-1.0-0/lib/liby.so", 200)
	p.hardlink("pkgs/b-1.0-0/lib/liby.so", "lib/liby.so")
	// across two packages: the owner of the first owned path wins, even though
	// the record of c sorts before the record of d
	p.meta("c-1.0-0", "c", "share/q")
	p.meta("d-1.0-0", "d", "lib/q")
	p.file("share/q", 300)
	p.hardlink("share/q", "lib/q")
	p.hardlink("share/q", "bin/q")

	cfg := p.config(32, "pkgs")
	cfg.ownLayer = 0
	out := p.layerize(cfg)

	sa := p.slotOf("lib/libx.so")
	if p.slotOf("bin/x") != sa || !os.SameFile(lstat(t, p.layered(sa, "bin/x")), lstat(t, p.layered(sa, "lib/libx.so"))) {
		t.Errorf("hardlink within a package is broken")
	}
	if n := nlink(t, p.layered(sa, "bin/x")); n != 2 {
		t.Errorf("expected link count 2, got %d", n)
	}

	sb := p.slotOf("lib/liby.so")
	if !os.SameFile(lstat(t, p.layered(sb, "lib/liby.so")), lstat(t, p.path("pkgs/b-1.0-0/lib/liby.so"))) {
		t.Errorf("the moved file is not the one hardlinked in the excluded path")
	}
	p.assertAbsent("pkgs/b-1.0-0/lib/liby.so")

	sd := p.slotOf("conda-meta/d-1.0-0.json")
	for _, rel := range []string{"bin/q", "lib/q", "share/q"} {
		if p.slotOf(rel) != sd {
			t.Errorf("%s is not in the layer of d", rel)
		}
	}
	if n := nlink(t, p.layered(sd, "share/q")); n != 3 {
		t.Errorf("expected link count 3, got %d", n)
	}
	// every path is counted as a file, but the size of a hardlink group only once
	if !strings.Contains(out, " files=10 size=300.0MB ") || strings.Contains(out, "WARN oversized") {
		t.Errorf("unexpected plan:\n%s", out)
	}
	for _, row := range planRows(t, out) {
		if strings.HasSuffix(row, "  a") && !strings.Contains(row, "  300.0MB      3  a") {
			t.Errorf("unexpected row for a: %s", row)
		}
		if strings.HasSuffix(row, "  d") && !strings.Contains(row, "      4  d") {
			t.Errorf("unexpected row for d: %s", row)
		}
	}
}

func TestSymlinksAndEmptyDirs(t *testing.T) {
	p := newPrefix(t)
	p.meta("a-1.0-0", "a", "bin/tool", "bin/alias", "lib/datalink", "share/data/file.txt")
	p.file("bin/tool", 10)
	p.symlink("bin/alias", "tool")
	p.file("share/data/file.txt", 20)
	p.symlink("lib/datalink", "../share/data")
	p.symlink("bin/dangling", "/does/not/exist")
	chmod(t, p.dir("var/empty"), 0o750)
	mtime := time.Date(2024, 5, 6, 7, 8, 9, 0, time.UTC)
	chtimes(t, p.path("var/empty"), mtime)

	out := p.layerize(p.config(32))
	// the symlink to a directory is not descended
	if !strings.Contains(out, " files=7 ") {
		t.Errorf("unexpected plan:\n%s", out)
	}
	sa := p.slotOf("bin/tool")
	if readlink(t, p.layered(sa, "bin/alias")) != "tool" || readlink(t, p.layered(sa, "lib/datalink")) != "../share/data" {
		t.Errorf("symlink targets not preserved")
	}
	if p.slotOf("lib/datalink") != sa || lstat(t, p.layered(sa, "lib/datalink")).Mode()&fs.ModeSymlink == 0 {
		t.Errorf("directory symlink not preserved")
	}

	// unowned symlinks and empty directories go to the leftovers layer
	rest := p.slotOf("bin/dangling")
	if rest == sa || readlink(t, p.layered(rest, "bin/dangling")) != "/does/not/exist" {
		t.Errorf("dangling symlink not in the leftovers layer")
	}
	if p.slotOf("var/empty") != rest {
		t.Errorf("empty directory not in the leftovers layer")
	}
	fi := lstat(t, p.layered(rest, "var/empty"))
	if !fi.IsDir() || fi.Mode().Perm() != 0o750 || !fi.ModTime().Equal(mtime) {
		t.Errorf("empty directory metadata not preserved: %v %v", fi.Mode(), fi.ModTime())
	}
	if !lstat(t, p.path("var/empty")).IsDir() {
		t.Errorf("empty directories stay in the prefix")
	}
}

func TestExclude(t *testing.T) {
	p := newPrefix(t)
	p.pkg("a", 1000)
	p.file("pkgs/a-1.0-0/info/index.json", 10)
	p.file("pkgs/urls.txt", 10)
	// a directory with only excluded children is recreated as an empty directory
	p.file("share/cache/tmp", 10)

	out := p.layerize(p.config(4, "pkgs", "share/cache/tmp"))
	if !strings.Contains(out, " files=3 ") {
		t.Errorf("unexpected plan:\n%s", out)
	}
	for _, rel := range []string{"pkgs/a-1.0-0/info/index.json", "pkgs/urls.txt", "share/cache/tmp"} {
		if !lstat(t, p.path(rel)).Mode().IsRegular() {
			t.Errorf("%s not left in place", rel)
		}
		p.assertAbsent(rel)
	}
	p.assertAbsent("pkgs")
	if !lstat(t, p.layered(p.slotOf("share/cache"), "share/cache")).IsDir() {
		t.Errorf("share/cache not recreated")
	}
}

func TestMetadata(t *testing.T) {
	p := newPrefix(t)
	p.meta("a-1.0-0", "a", "bin/exe", "lib/sub/data")
	p.meta("b-1.0-0", "b", "lib/b.so")
	p.file("bin/exe", 10)
	p.file("lib/sub/data", 10)
	p.file("lib/b.so", 10)
	times := map[string]time.Time{}
	modes := map[string]fs.FileMode{
		"bin/exe":      0o755 | fs.ModeSetuid,
		"lib/sub/data": 0o640,
		"lib/b.so":     0o644,
		"bin":          0o755,
		"lib":          0o755 | fs.ModeSetgid,
		"lib/sub":      0o700,
		// as in the mamba images
		".": 0o777,
	}
	for rel, mode := range modes {
		chmod(t, p.path(rel), mode)
	}
	// set the times last, deepest first, since changing a directory updates its mtime
	for i, rel := range []string{"bin/exe", "lib/sub/data", "lib/b.so", "lib/sub", "bin", "lib", "conda-meta", "."} {
		times[rel] = time.Date(2020, 1, 2, 3, 4, 5+i, 0, time.UTC)
		chtimes(t, p.path(rel), times[rel])
	}
	uid, gid := owner(t, p.path("bin/exe"))

	cfg := p.config(8)
	// on Linux mkdir inherits the setgid bit, as below / in a rootless BuildKit step
	if err := os.MkdirAll(p.out, 0o755); err != nil {
		t.Fatal(err)
	}
	chmod(t, p.out, 0o755|fs.ModeSetgid)
	cfg.ownLayer = 0
	p.layerize(cfg)

	sa, sb := p.slotOf("bin/exe"), p.slotOf("lib/b.so")
	if sa == sb {
		t.Fatalf("expected two layers")
	}
	check := func(slot, rel string) {
		t.Helper()
		fi := lstat(t, p.layered(slot, rel))
		mode := modes[rel]
		if rel == "." {
			// the prefix is 0755 like its parents, whatever its source mode
			mode = 0o755
		}
		if fi.Mode()&modeBits != mode {
			t.Errorf("%s: expected mode %v, got %v", rel, mode, fi.Mode())
		}
		if !fi.ModTime().Equal(times[rel]) {
			t.Errorf("%s: expected mtime %v, got %v", rel, times[rel], fi.ModTime())
		}
		if u, g := owner(t, p.layered(slot, rel)); u != uid || g != gid {
			t.Errorf("%s: expected owner %d:%d, got %d:%d", rel, uid, gid, u, g)
		}
	}
	for _, rel := range []string{"bin/exe", "lib/sub/data", "bin", "lib", "lib/sub", "."} {
		check(sa, rel)
	}
	// a directory has the same metadata in every layer that contains it
	for _, rel := range []string{"lib/b.so", "lib", "."} {
		check(sb, rel)
	}

	// the parents of the prefix are 0755, and root:root when running as root
	for dir := filepath.Dir(p.src); dir != "/"; dir = filepath.Dir(dir) {
		fi := lstat(t, filepath.Join(p.out, sa, dir))
		if fi.Mode() != fs.ModeDir|0o755 {
			t.Errorf("%s: expected mode 0755, got %v", dir, fi.Mode())
		}
		if u, g := owner(t, filepath.Join(p.out, sa, dir)); os.Geteuid() == 0 && (u != 0 || g != 0) {
			t.Errorf("%s: expected owner root, got %d:%d", dir, u, g)
		}
	}
	// no directory for the unused slots
	if entries, err := os.ReadDir(p.out); err != nil || len(entries) != 2 {
		t.Errorf("expected 2 layer directories: %v %v", entries, err)
	}
}

func TestOwnersAsRoot(t *testing.T) {
	requireRoot(t)
	p := newPrefix(t)
	p.meta("a-1.0-0", "a", "bin/exe", "bin/link", "lib/data")
	p.file("bin/exe", 10)
	p.symlink("bin/link", "exe")
	p.file("lib/data", 10)
	owners := map[string][2]int{"bin/exe": {1001, 1002}, "bin/link": {1003, 1004}, "lib/data": {1005, 1006}, "bin": {2001, 2002}, "lib": {2003, 2004}}
	for rel, o := range owners {
		if err := os.Lchown(p.path(rel), o[0], o[1]); err != nil {
			t.Fatal(err)
		}
	}
	// chown clears setuid, so set it afterwards
	chmod(t, p.path("bin/exe"), 0o755|fs.ModeSetuid|fs.ModeSetgid)
	// the owner and mode of the prefix and of its real parents never affect the layers
	for dir, o := range map[string][2]int{p.src: {3001, 3002}, filepath.Dir(p.src): {4001, 4002}} {
		if err := os.Lchown(dir, o[0], o[1]); err != nil {
			t.Fatal(err)
		}
	}
	chmod(t, p.src, 0o777)
	chmod(t, filepath.Dir(p.src), 0o700)

	p.layerize(p.config(4))
	slot := p.slotOf("bin/exe")
	for rel, o := range owners {
		if u, g := owner(t, p.layered(slot, rel)); int(u) != o[0] || int(g) != o[1] {
			t.Errorf("%s: expected owner %d:%d, got %d:%d", rel, o[0], o[1], u, g)
		}
	}
	if mode := lstat(t, p.layered(slot, "bin/exe")).Mode(); mode&modeBits != 0o755|fs.ModeSetuid|fs.ModeSetgid {
		t.Errorf("setuid and setgid not preserved: %v", mode)
	}
	for _, dir := range []string{p.src, filepath.Dir(p.src)} {
		d := filepath.Join(p.out, slot, dir)
		if u, g := owner(t, d); u != 0 || g != 0 || lstat(t, d).Mode()&modeBits != 0o755 {
			t.Errorf("%s is not 0755 root:root", dir)
		}
	}
}

func TestVerifyFails(t *testing.T) {
	p := newPrefix(t)
	p.dir("pkgs/cache")
	p.file("pkgs/cache/a.tar", 10)
	p.file("bin/stuck", 10)
	err := verify(p.src, map[string]bool{"pkgs": true})
	if err == nil || !strings.Contains(err.Error(), "verify failed, 1 paths left") || !strings.HasSuffix(err.Error(), ": bin/stuck") {
		t.Fatalf("expected verify error, got %v", err)
	}
}

func TestVerifyListsAtMost20Paths(t *testing.T) {
	p := newPrefix(t)
	p.meta("a-1.0-0", "a")
	for i := 0; i < 25; i++ {
		p.file(fmt.Sprintf("bin/f%02d", i), 1)
	}
	err := verify(p.src, nil)
	if err == nil {
		t.Fatal("expected verify error")
	}
	msg := err.Error()
	if !strings.Contains(msg, "26 paths left") || !strings.Contains(msg, "bin/f19 (+6 more)") || strings.Contains(msg, "bin/f20") {
		t.Errorf("unexpected message: %s", msg)
	}
}
