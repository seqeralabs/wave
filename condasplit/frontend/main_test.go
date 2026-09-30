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

import "testing"

const dockerfile = `# syntax=public.cr.stage-seqera.io/wave/condasplit:v1
FROM mambaorg/micromamba:2-amazon2023 AS build
RUN micromamba install -y -n base bwa

FROM ubuntu:24.04 AS prod
COPY --link --from=build /layers/00/ /
COPY --link --from=build /layers/01/ /
COPY --link --from=build /layers/02/ /
COPY --link --from=build /layers/03/ /
RUN apt-get update
`

func TestSyntaxLine(t *testing.T) {
	got := string(syntaxLine.ReplaceAll([]byte(dockerfile), nil))
	if got != dockerfile[len("# syntax=public.cr.stage-seqera.io/wave/condasplit:v1\n"):] {
		t.Errorf("unexpected Dockerfile:\n%s", got)
	}
	// only a directive on the first line is removed
	other := "FROM foo\n# syntax=bar\n"
	if got := string(syntaxLine.ReplaceAll([]byte(other), nil)); got != other {
		t.Errorf("unexpected Dockerfile:\n%s", got)
	}
}

func TestDropEmptySlots(t *testing.T) {
	var asked []string
	got := string(dropEmptySlots([]byte(dockerfile), func(slot string) bool {
		asked = append(asked, slot)
		return slot == "01" || slot == "03"
	}))
	expected := `# syntax=public.cr.stage-seqera.io/wave/condasplit:v1
FROM mambaorg/micromamba:2-amazon2023 AS build
RUN micromamba install -y -n base bwa

FROM ubuntu:24.04 AS prod
COPY --link --from=build /layers/00/ /
COPY --link --from=build /layers/02/ /
RUN apt-get update
`
	if got != expected {
		t.Errorf("unexpected Dockerfile:\n%s", got)
	}
	if len(asked) != 4 || asked[0] != "00" || asked[3] != "03" {
		t.Errorf("unexpected slots: %v", asked)
	}
	// every slot is kept when none is empty
	if got := string(dropEmptySlots([]byte(dockerfile), func(string) bool { return false })); got != dockerfile {
		t.Errorf("unexpected Dockerfile:\n%s", got)
	}
}
