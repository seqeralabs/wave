/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.api;

import java.util.Objects;

/**
 * Model compression options for container image builds
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class BuildCompression {

    static public final BuildCompression gzip = new BuildCompression().withMode(Mode.gzip);
    static public final BuildCompression estargz = new BuildCompression().withMode(Mode.estargz);
    static public final BuildCompression zstd = new BuildCompression().withMode(Mode.zstd);

    public enum Mode {
        gzip,       // gzip compression
        estargz,    // estargz compression
        zstd,       // zstd compression
        ;
    }

    private Mode mode;
    private Integer level;
    private Boolean force;

    public BuildCompression withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public BuildCompression withLevel(Integer level) {
        this.level = level;
        return this;
    }

    public BuildCompression withForce(Boolean force) {
        this.force = force;
        return this;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildCompression that = (BuildCompression) o;
        return mode == that.mode && Objects.equals(level, that.level) && Objects.equals(force, that.force);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, level, force);
    }

    @Override
    public String toString() {
        return "BuildCompression{" +
                "mode=" + mode +
                ", level=" + level +
                ", force=" + force +
                '}';
    }
}
