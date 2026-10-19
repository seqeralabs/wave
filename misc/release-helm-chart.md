# Helm chart bump after a Wave release

After a Wave release is published, open a pull request on `seqeralabs/helm-charts` that bumps the Wave
subchart. The local clone of that repo depends on each person's setup: locate it first (e.g. `~/repos/helm-charts`),
check that `origin` points to `seqeralabs/helm-charts`, and start from an up-to-date `master` on a new branch
(e.g. `<user>/bump-wave-to-<VERSION>`).

1. In `charts/platform/charts/wave/Chart.yaml`, set `appVersion` to `"v<VERSION>"` (matching the Wave release
   tag) and bump the subchart `version` by one patch (e.g. `0.6.1` -> `0.6.2`).
2. In `charts/platform/Chart.yaml`, bump the parent `platform` chart `version` by one patch (e.g. `1.0.9` -> `1.0.10`).
   Leave the platform `appVersion` untouched.
3. Run `helm dependency update charts/platform` to refresh `charts/platform/Chart.lock` with the new Wave subchart
   version.
4. Add a new entry at the top of `charts/platform/charts/wave/CHANGELOG.md` ("Updated `appVersion` to `v<VERSION>`")
   and of `charts/platform/CHANGELOG.md` ("Bump wave subchart to `<NEW WAVE CHART VERSION>`, which updates its
   `appVersion` to `v<VERSION>`"), following the format of the existing entries.
5. Regenerate the READMEs of both charts (version badges, `--version` in the install examples and the current
   `appVersion` mention) through the pre-commit hook (`pre-commit run helm-docs`). Do not run `helm-docs` by hand:
   the `helm-charts` repo's `CLAUDE.md` warns it regenerates unrelated READMEs. See the previous "Bump wave app"
   commit in that repo for reference.
6. Commit the changes (e.g. `Bump wave app to <VERSION> (chart <NEW WAVE CHART VERSION>), platform chart to
   <NEW PLATFORM VERSION>`), push the branch and open a PR against `master` with `gh pr create`.
