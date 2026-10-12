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

// condasplit moves the content of an installed conda prefix into a fixed
// number of layer directories, grouping files by conda package, so that each
// directory can be added to a container image as a separate layer.
package main

import (
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

const (
	defaultMaxLayerSize = 500_000_000
	defaultOwnLayerSize = 50_000_000
)

const usage = `Usage: condasplit --src DIR --out DIR --slots N
                  [--max-layer-size BYTES] [--own-layer-size BYTES] [--exclude RELPATH ...]

  --src DIR               absolute path of the installed prefix, e.g. /opt/conda (required)
  --out DIR               output root, one NN/ directory is created for each layer (required)
  --slots N               maximum number of layers, smaller layers are merged above it (required)
  --max-layer-size BYTES  cap per layer, as the sum of file sizes (default 500000000)
  --own-layer-size BYTES  packages at least this size get a layer to themselves (default 50000000)
  --exclude RELPATH       path relative to --src left in place and not layered (repeatable)
`

type config struct {
	src      string
	out      string
	slots    int
	maxLayer int64
	ownLayer int64
	excludes map[string]bool
}

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, stdout, stderr io.Writer) int {
	cfg, err := parseArgs(args)
	if errors.Is(err, flag.ErrHelp) {
		fmt.Fprint(stdout, usage)
		return 0
	}
	if err == nil {
		err = layerize(cfg, stdout)
	}
	if err != nil {
		// the error must fit on one line of the build log
		fmt.Fprintf(stderr, "condasplit: %s\n", strings.ReplaceAll(err.Error(), "\n", " "))
		return 1
	}
	return 0
}

func parseArgs(args []string) (config, error) {
	cfg := config{excludes: map[string]bool{}}
	fs := flag.NewFlagSet("condasplit", flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	fs.StringVar(&cfg.src, "src", "", "")
	fs.StringVar(&cfg.out, "out", "", "")
	fs.IntVar(&cfg.slots, "slots", 0, "")
	fs.Int64Var(&cfg.maxLayer, "max-layer-size", defaultMaxLayerSize, "")
	fs.Int64Var(&cfg.ownLayer, "own-layer-size", defaultOwnLayerSize, "")
	fs.Func("exclude", "", func(rel string) error {
		cfg.excludes[filepath.Clean(rel)] = true
		return nil
	})
	if err := fs.Parse(args); err != nil {
		return cfg, err
	}
	switch {
	case !filepath.IsAbs(cfg.src):
		return cfg, fmt.Errorf("--src must be an absolute path: %s", cfg.src)
	case cfg.out == "":
		return cfg, errors.New("missing required option --out")
	case cfg.slots < 1:
		return cfg, errors.New("missing or invalid option --slots, it must be at least 1")
	}
	cfg.src = filepath.Clean(cfg.src)
	return cfg, nil
}

// layerize runs the whole process: nothing is moved until the plan is complete,
// so any inventory or conda-meta error leaves the prefix untouched
func layerize(cfg config, stdout io.Writer) error {
	inv, err := scan(cfg.src, cfg.excludes)
	if err != nil {
		return err
	}
	own, err := readOwners(cfg.src, inv)
	if err != nil {
		return err
	}
	p := makePlan(inv, own, cfg)
	m := &mover{src: cfg.src, out: cfg.out, inv: inv}
	if err := m.move(p); err != nil {
		return err
	}
	if err := verify(cfg.src, cfg.excludes); err != nil {
		return err
	}
	_, err = io.WriteString(stdout, p.format(cfg))
	return err
}
