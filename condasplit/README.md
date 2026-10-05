# condasplit

A small static tool used by the `conda/micromamba:v2-fast` and `conda/pixi:v1-fast` build templates to ship a conda
environment as several image layers instead of one large layer.

It runs in the same `RUN` step that installs the environment. It moves every file of the
installed prefix (e.g. `/opt/conda` or `/opt/wave/.pixi/envs/default`) into at most `--slots` layer directories,
`<out>/00`, `<out>/01`, ..., each rooted at `/`, grouping files by conda package. The final
stage of these templates then adds each directory as a separate layer with
`COPY --link --from=build /layers/NN/ /`.

The tool image is `FROM scratch`. The build mounts it read-only for that one step
(`RUN --mount=type=bind,from=<image>,...`), so the tool never ends up in the built image
or in the build cache.

The same image is the BuildKit frontend of these templates, named by its first line
`# syntax=<image>`, and its entrypoint is `/condasplit-frontend` (see `frontend/`).
Each template has a single `COPY --link --from=build /layers/NN/ /` line: the frontend
builds the `build` stage, lists `/layers`, repeats that line for each directory and builds
the whole Dockerfile. It delegates both builds to BuildKit's built-in Dockerfile frontend,
and the install stage runs once because the second build finds it in the cache of the
first. The image then has one layer per layer directory.

## How files are grouped

1. The prefix is walked without following symlinks, skipping the `--exclude` paths.
2. Hardlinked files are grouped by `(dev, inode)` and never split across layers.
3. Paths are assigned to packages from the `files` list of each `conda-meta/<dist>.json`
   record, read in file name order. The first claim wins, later claims are counted as
   clobbers. Paths no package claims (e.g. `conda-meta/history`) are *unowned*.
4. A package larger than `--max-layer-size` is cut into chunks of at most that size.
   A single file (or hardlink group) larger than the cap gets a layer to itself and a
   warning.
5. Packages of at least `--own-layer-size` get their own layer(s). Smaller packages are
   packed together, first-fit decreasing, into layers of at most `--max-layer-size`.
   Unowned paths go to the trailing leftovers layer(s), also capped.
6. When more layers than `--slots` are needed, the two smallest layers are merged
   until they fit, with a warning.
7. Files are moved with `rename()`, so hardlinks, owners, modes and timestamps are kept.
   Created directories get the owner, mode and times of the source directory, except
   the prefix itself and its parents: they are 0755 root:root, as the `COPY` of v2
   creates them. Files of a lower image layer are copied up by overlayfs, which keeps
   their metadata but splits hardlinks between them.
8. The prefix is checked again: anything other than directories and excluded paths
   left behind fails the run.

The layer plan is printed to stdout, so it shows in the build log:

```
>> CONDA_LAYERS_START
slots=19/32 files=88231 size=4505.0MB packages=323 clobbers=28 unowned=0.0MB max-layer=500MB own-layer=50MB
00  454.5MB     29  mkl#0
01  425.8MB      4  gatk4
...
18    0.0MB      3  (unowned)
<< CONDA_LAYERS_END
```

Warnings are printed inside the markers and never fail the run:

```
WARN oversized file <path> <size>
WARN slots exceeded: merged <n> layers
WARN merged layer <NN> <size> exceeds max-layer <cap>
```

The exit code is `0` on success, including when warnings are printed. It is non-zero,
with a one-line message on stderr, for a missing or relative `--src`, a missing
`conda-meta/`, a malformed JSON record, an I/O error, or a failed final check.

## Usage

```
condasplit --src DIR --out DIR --slots N
             [--max-layer-size BYTES] [--own-layer-size BYTES] [--exclude RELPATH ...]
```

| Option | Default | Meaning |
|---|---|---|
| `--src` | (required) | Absolute path of the installed prefix, e.g. `/opt/conda`. The same path is used inside each layer |
| `--out` | (required) | Output root. One `NN/` directory is created for every slot `00` ... `N-1` |
| `--slots` | (required) | Maximum number of layers. Above it the smallest layers are merged |
| `--max-layer-size` | 500000000 | Cap per layer: the sum of file sizes before compression |
| `--own-layer-size` | 50000000 | Packages at least this size get a layer to themselves |
| `--exclude` | none | Path relative to `--src` left in place and not layered (repeatable) |

The `conda/micromamba:v2-fast` template runs:

```
/opt/wave-tools/condasplit --src /opt/conda --out /layers \
    --slots 32 --max-layer-size 500000000 --own-layer-size 50000000 --exclude pkgs
```

The `conda/pixi:v1-fast` template runs the same command on the pixi environment. It has no
`--exclude`, because the pixi package cache is outside the environment:

```
/opt/wave-tools/condasplit --src /opt/wave/.pixi/envs/default --out /layers \
    --slots 32 --max-layer-size 500000000 --own-layer-size 50000000
```

## Development

The tool uses only the Go standard library. The frontend is a separate Go module,
`frontend/`, so that its `github.com/moby/buildkit` dependency never reaches the tool;
keep that dependency at the version of the BuildKit used by Wave. Run the unit tests:

```bash
cd condasplit
go test ./...
(cd frontend && go test ./...)
```

Tests checking file owners need root and are skipped otherwise. To run all of them,
as the image build and the CI job do:

```bash
make test
```

Build a single-arch image into the local docker as `condasplit:dev` (never pushed) and
try it against a prefix:

```bash
make load
docker run --rm -v <prefix>:/opt/conda -v <out>:/layers --entrypoint /condasplit \
    condasplit:dev --src /opt/conda --out /layers --slots 32 --exclude pkgs
```

## Release

The image is multi-arch (`linux/amd64`, `linux/arm64`). The Dockerfile runs the unit tests
and cross-compiles a static binary (`CGO_ENABLED=0`) from the pinned `golang` image.

Each release gets a new immutable tag (`v1`, `v2`, ...), set in the `VERSION` file. Wave
pins the exact tag with the `wave.build.condasplit-image` setting, so a new tool version
changes the `-fast` container files and therefore the container ids. Never overwrite an existing tag.

**Stage** (default registry of the `Makefile`, only for tests and local development):

```bash
cd condasplit
make all
# pushes public.cr.stage-seqera.io/wave/condasplit:<VERSION>
```

**Production**: bump `VERSION`. The `build-condasplit.yml` GitHub workflow publishes
`public.cr.seqera.io/wave/condasplit:<VERSION>` on the next Wave release commit (`[release]`
on `master`). Then point the default of `wave.build.condasplit-image` to the new tag.

The tag is always the `VERSION` value, never a checksum: Wave writes it into the `-fast` container
files and the build logs, where `condasplit:v1` is readable and a checksum isn't. The checksum
goes into an `io.seqera.condasplit.source` image label instead: a SHA-256 of the tracked files of
this directory, the Markdown docs excluded. `make release` uses it to make sure a tag always holds
the same sources:

| The tag | Result |
|---|---|
| doesn't exist | builds and pushes the image |
| exists with the same checksum | skips the build, e.g. on a Wave release that doesn't change the tool |
| exists with a different or no checksum | fails, bump `VERSION` to publish the changed sources |

A registry error also fails the release, so it never overwrites a tag.
