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

// Command soci-indexer builds SOCI zTOCs for container image layers and publishes
// SOCI index artifacts on behalf of Wave. It has two sub-commands:
//
//	index  builds the zTOC of one layer and pushes it to the zTOC repository as an
//	       OCI artifact tagged by layer digest
//	push   uploads a list of blobs and raw manifests to a target repository, preserving
//	       the manifest bytes (and therefore their digests) exactly
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
)

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	var err error
	switch os.Args[1] {
	case "index":
		err = runIndex(os.Args[2:])
	case "push":
		err = runPush(os.Args[2:])
	default:
		usage()
		os.Exit(2)
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "soci-indexer: %v\n", err)
		os.Exit(1)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: soci-indexer index|push [options]")
}

func runIndex(args []string) error {
	fs := flag.NewFlagSet("index", flag.ExitOnError)
	layer := fs.String("layer", "", "path of the layer blob file")
	source := fs.String("source", "", "layer to download when -layer is not given e.g. docker.io/library/ubuntu@sha256:…")
	digest := fs.String("digest", "", "digest of the layer blob")
	mediaType := fs.String("media-type", "", "media type of the layer blob")
	span := fs.Int64("span", DefaultSpanSize, "zTOC span size in bytes")
	minSize := fs.Int64("min-size", DefaultMinLayerSize, "layers smaller than this are not indexed")
	repo := fs.String("repo", "", "repository where the zTOC artifact is pushed")
	insecure := fs.Bool("insecure", false, "allow plain HTTP registries")
	_ = fs.Parse(args)
	if (*layer == "" && *source == "") || *digest == "" || *mediaType == "" || *repo == "" {
		return fmt.Errorf("options -layer (or -source), -digest, -media-type and -repo are required")
	}
	result, err := IndexLayer(IndexRequest{
		LayerFile:      *layer,
		Source:         *source,
		LayerDigest:    *digest,
		LayerMediaType: *mediaType,
		SpanSize:       *span,
		MinLayerSize:   *minSize,
		Repository:     *repo,
		Insecure:       *insecure,
	})
	if err != nil {
		return err
	}
	return json.NewEncoder(os.Stdout).Encode(result)
}

func runPush(args []string) error {
	fs := flag.NewFlagSet("push", flag.ExitOnError)
	target := fs.String("target", "", "target repository")
	planFile := fs.String("plan", "", "path of the push plan JSON file")
	insecure := fs.Bool("insecure", false, "allow plain HTTP registries")
	_ = fs.Parse(args)
	if *target == "" || *planFile == "" {
		return fmt.Errorf("options -target and -plan are required")
	}
	data, err := os.ReadFile(*planFile)
	if err != nil {
		return err
	}
	var plan PushPlan
	if err := json.Unmarshal(data, &plan); err != nil {
		return fmt.Errorf("invalid push plan: %w", err)
	}
	return Push(*target, plan, *insecure)
}
