/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class PixiOpts {

    final public static String DEFAULT_PIXI_IMAGE = "ghcr.io/prefix-dev/pixi:latest";
    final public static String DEFAULT_BASE_IMAGE = "ubuntu:24.04";
    final public static String DEFAULT_PACKAGES = "conda-forge::procps-ng";

    public String pixiImage;
    public List<String> commands;
    public String basePackages;
    public String baseImage;

    public PixiOpts() {
        this(Map.of());
    }
    public PixiOpts(Map<String,?> opts) {
        this.pixiImage = opts.containsKey("pixiImage") ? opts.get("pixiImage").toString(): DEFAULT_PIXI_IMAGE;
        this.baseImage = opts.containsKey("baseImage") ? opts.get("baseImage").toString(): DEFAULT_BASE_IMAGE;
        this.commands = opts.containsKey("commands") ? (List<String>)opts.get("commands") : null;
        this.basePackages = opts.containsKey("basePackages") ? (String)opts.get("basePackages") : DEFAULT_PACKAGES;
    }

    public PixiOpts withMambaImage(String value) {
        this.pixiImage = value;
        return this;
    }

    public PixiOpts withCommands(List<String> value) {
        this.commands = value;
        return this;
    }

    public PixiOpts withBasePackages(String value) {
        this.basePackages = value;
        return this;
    }

    @Override
    public String toString() {
        return String.format("PixiOpts(pixiImage=%s; basePackages=%s, commands=%s, baseImage=%s)",
                pixiImage,
                basePackages,
                commands != null ? String.join(",", commands) : "null",
                baseImage
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CondaOpts condaOpts = (CondaOpts) object;
        return Objects.equals(pixiImage, condaOpts.mambaImage)
                && Objects.equals(commands, condaOpts.commands)
                && Objects.equals(basePackages, condaOpts.basePackages)
                && Objects.equals(baseImage, condaOpts.baseImage)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pixiImage, commands, basePackages, baseImage);
    }

}
