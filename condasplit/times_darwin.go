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
	"syscall"
	"time"
)

// the tool runs only on Linux, macOS is supported just to run the unit tests on
// developer machines: there the times of a copied symlink are not preserved
func lutimes(path string, atime, mtime time.Time) error {
	return nil
}

func statAtime(st *syscall.Stat_t) time.Time {
	return time.Unix(st.Atimespec.Sec, st.Atimespec.Nsec)
}
