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

// condasplit-frontend is the BuildKit frontend of the conda/micromamba:v3 build template.
// The template declares a fixed set of slot layers because Wave writes the Dockerfile
// before the environment is known: this frontend builds the install stage first, then
// builds the image without the COPY lines of the slots condasplit left empty, so that
// the image has no empty layers. Both builds are delegated to the built-in Dockerfile
// frontend, and the install stage runs once because the second build finds it in the
// cache of the first one
package main

import (
	"context"
	"fmt"
	"maps"
	"os"
	"regexp"

	"github.com/moby/buildkit/client/llb"
	"github.com/moby/buildkit/frontend/dockerui"
	"github.com/moby/buildkit/frontend/gateway/client"
	"github.com/moby/buildkit/frontend/gateway/grpcclient"
	"github.com/moby/buildkit/solver/pb"
	"github.com/moby/buildkit/util/appcontext"
)

// installStage is the build stage of the template that runs condasplit
const installStage = "build"

var (
	// the directive selecting this frontend, it must be the first line
	syntaxLine = regexp.MustCompile(`\A#\s*syntax=.*\n`)
	// the COPY line of a slot in the conda/micromamba:v3 template
	slotLine = regexp.MustCompile(`(?m)^COPY --link --from=build /layers/(\d+)/ /\n`)
)

func main() {
	if err := grpcclient.RunFromEnvironment(appcontext.Context(), build); err != nil {
		fmt.Fprintf(os.Stderr, "condasplit-frontend: %+v\n", err)
		os.Exit(1)
	}
}

func build(ctx context.Context, c client.Client) (*client.Result, error) {
	bc, err := dockerui.NewClient(c)
	if err != nil {
		return nil, err
	}
	src, err := bc.ReadEntrypoint(ctx, "Dockerfile")
	if err != nil {
		return nil, err
	}
	// without the directive the built-in frontend does not forward the build back here
	dockerfile := syntaxLine.ReplaceAll(src.Data, nil)
	res, err := forward(ctx, c, src.Filename, dockerfile, installStage)
	if err != nil {
		return nil, err
	}
	// one result for each platform of the build
	refs := res.Refs
	if res.Ref != nil {
		refs = map[string]client.Reference{"": res.Ref}
	}
	if len(refs) == 0 {
		return nil, fmt.Errorf("the %s stage has no result", installStage)
	}
	// a slot is dropped only when it is empty for every platform
	dockerfile = dropEmptySlots(dockerfile, func(slot string) bool {
		for _, ref := range refs {
			entries, err := ref.ReadDir(ctx, client.ReadDirRequest{Path: "/layers/" + slot})
			if err != nil || len(entries) > 0 {
				return false
			}
		}
		return true
	})
	return forward(ctx, c, src.Filename, dockerfile, "")
}

// dropEmptySlots removes the COPY lines of the slots for which empty returns true
func dropEmptySlots(dockerfile []byte, empty func(slot string) bool) []byte {
	return slotLine.ReplaceAllFunc(dockerfile, func(line []byte) []byte {
		if empty(string(slotLine.FindSubmatch(line)[1])) {
			return nil
		}
		return line
	})
}

// forward builds the Dockerfile content with the built-in Dockerfile frontend,
// only the given target when not empty
func forward(ctx context.Context, c client.Client, filename string, dockerfile []byte, target string) (*client.Result, error) {
	def, err := llb.Scratch().File(llb.Mkfile(filename, 0o644, dockerfile)).Marshal(ctx)
	if err != nil {
		return nil, err
	}
	opts := maps.Clone(c.BuildOpts().Opts)
	if opts == nil {
		opts = map[string]string{}
	}
	// set by the built-in frontend when it forwarded the build to this one
	delete(opts, "cmdline")
	delete(opts, "source")
	if target != "" {
		opts["target"] = target
	}
	return c.Solve(ctx, client.SolveRequest{
		Frontend:       "dockerfile.v0",
		FrontendOpt:    opts,
		FrontendInputs: map[string]*pb.Definition{"dockerfile": def.ToPB()},
	})
}
