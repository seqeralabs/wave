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

const dockerfile = `# syntax=public.cr.seqera.io/wave/condasplit:v1
FROM mambaorg/micromamba:2-amazon2023 AS build
RUN micromamba install -y -n base bwa

FROM ubuntu:24.04 AS prod
COPY --link --from=build /layers/NN/ /
RUN apt-get update
`

func TestExpandLayers(t *testing.T) {
	got, err := expandLayers([]byte(dockerfile), []string{"02", "00", "01"})
	if err != nil {
		t.Fatal(err)
	}
	expected := `# syntax=public.cr.seqera.io/wave/condasplit:v1
FROM mambaorg/micromamba:2-amazon2023 AS build
RUN micromamba install -y -n base bwa

FROM ubuntu:24.04 AS prod
COPY --link --from=build /layers/00/ /
COPY --link --from=build /layers/01/ /
COPY --link --from=build /layers/02/ /
RUN apt-get update
`
	if string(got) != expected {
		t.Errorf("unexpected Dockerfile:\n%s", got)
	}
	if _, err := expandLayers([]byte("FROM foo\n"), []string{"00"}); err == nil {
		t.Error("expected an error when the COPY line is missing")
	}
}
