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
	"os"
	"syscall"
	"time"
	"unsafe"
)

// the same values on every Linux architecture, the syscall package does not export them
const (
	atFdcwd           = -0x64
	atSymlinkNofollow = 0x100
)

// lutimes sets the times of a path without following symlinks. The standard library
// has no such call (os.Chtimes and syscall.UtimesNano follow symlinks), so it invokes
// utimensat(2) with AT_SYMLINK_NOFOLLOW directly
func lutimes(path string, atime, mtime time.Time) error {
	p, err := syscall.BytePtrFromString(path)
	if err != nil {
		return err
	}
	ts := [2]syscall.Timespec{
		syscall.NsecToTimespec(atime.UnixNano()),
		syscall.NsecToTimespec(mtime.UnixNano()),
	}
	dirfd := atFdcwd
	_, _, errno := syscall.Syscall6(syscall.SYS_UTIMENSAT, uintptr(dirfd), uintptr(unsafe.Pointer(p)), uintptr(unsafe.Pointer(&ts[0])), atSymlinkNofollow, 0, 0)
	if errno != 0 {
		return &os.PathError{Op: "lutimes", Path: path, Err: errno}
	}
	return nil
}

func statAtime(st *syscall.Stat_t) time.Time {
	return time.Unix(int64(st.Atim.Sec), int64(st.Atim.Nsec))
}
