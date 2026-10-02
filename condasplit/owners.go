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
	"encoding/json"
	"fmt"
	"os"
	"path"
	"path/filepath"
	"strings"
)

type ownership struct {
	owner    map[string]string // relative path -> package name
	packages int
	clobbers int
}

// condaRecord is the part of a conda-meta/<dist>.json file used to assign paths to packages
type condaRecord struct {
	Name  string   `json:"name"`
	Files []string `json:"files"`
}

// readOwners assigns every inventoried path to the package that installed it, reading
// the conda-meta records in file name order: the first claim of a path wins and any
// later claim is counted as a clobber
func readOwners(src string, inv *inventory) (*ownership, error) {
	dir := filepath.Join(src, "conda-meta")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("missing conda-meta directory in %s: %w", src, err)
	}
	known := make(map[string]bool, len(inv.nodes))
	for _, n := range inv.nodes {
		known[n.rel] = true
	}
	own := &ownership{owner: map[string]string{}}
	names := map[string]bool{}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		data, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil {
			return nil, err
		}
		var rec condaRecord
		if err := json.Unmarshal(data, &rec); err != nil {
			return nil, fmt.Errorf("cannot parse conda-meta/%s: %v", e.Name(), err)
		}
		if rec.Name == "" {
			rec.Name = strings.TrimSuffix(e.Name(), ".json")
		}
		names[rec.Name] = true
		// the record file itself belongs to its package
		for _, f := range append(rec.Files, "conda-meta/"+e.Name()) {
			f = path.Clean(f)
			if !known[f] {
				continue
			}
			if _, found := own.owner[f]; found {
				own.clobbers++
				continue
			}
			own.owner[f] = rec.Name
		}
	}
	own.packages = len(names)
	return own, nil
}
