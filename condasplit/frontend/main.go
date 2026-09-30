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
// Wave writes the Dockerfile before the environment is known, so the template has one
// COPY line for the layer directories: this frontend builds the install stage first,
// then builds the image with that line repeated for each directory condasplit created.
// Both builds are delegated to the built-in Dockerfile frontend, and the install stage
// runs once because the second build finds it in the cache of the first one
package main

import (
	"context"
	"fmt"
	"maps"
	"os"
	"regexp"
	"slices"
	"strings"

	"github.com/moby/buildkit/client/llb"
	"github.com/moby/buildkit/frontend/dockerui"
	"github.com/moby/buildkit/frontend/gateway/client"
	"github.com/moby/buildkit/frontend/gateway/grpcclient"
	"github.com/moby/buildkit/solver/pb"
	"github.com/moby/buildkit/util/appcontext"
)

var (
	// the directive selecting this frontend, it must be the first line
	syntaxLine = regexp.MustCompile(`\A#\s*syntax=.*\n`)
	// the COPY line of the layer directories in the conda/micromamba:v3 template
	layersLine = regexp.MustCompile(`(?m)^COPY --link --from=build /layers/NN/ /\n`)
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
	// the build stage of the template runs condasplit
	res, err := forward(ctx, c, src.Filename, dockerfile, "build")
	if err != nil {
		return nil, err
	}
	// Wave builds one platform at a time
	ref, err := res.SingleRef()
	if err != nil {
		return nil, err
	}
	entries, err := ref.ReadDir(ctx, client.ReadDirRequest{Path: "/layers"})
	if err != nil {
		return nil, err
	}
	var dirs []string
	for _, e := range entries {
		dirs = append(dirs, e.Path)
	}
	if dockerfile, err = expandLayers(dockerfile, dirs); err != nil {
		return nil, err
	}
	return forward(ctx, c, src.Filename, dockerfile, "")
}

// expandLayers repeats the COPY line of the layer directories for each of them, in order
func expandLayers(dockerfile []byte, dirs []string) ([]byte, error) {
	if !layersLine.Match(dockerfile) {
		return nil, fmt.Errorf("missing line: COPY --link --from=build /layers/NN/ /")
	}
	var lines strings.Builder
	for _, dir := range slices.Sorted(slices.Values(dirs)) {
		fmt.Fprintf(&lines, "COPY --link --from=build /layers/%s/ /\n", dir)
	}
	return layersLine.ReplaceAllLiteral(dockerfile, []byte(lines.String())), nil
}

// forward builds the Dockerfile content with the built-in Dockerfile frontend,
// only the given target when not empty
func forward(ctx context.Context, c client.Client, filename string, dockerfile []byte, target string) (*client.Result, error) {
	def, err := llb.Scratch().File(llb.Mkfile(filename, 0o644, dockerfile)).Marshal(ctx)
	if err != nil {
		return nil, err
	}
	opts := maps.Clone(c.BuildOpts().Opts)
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
