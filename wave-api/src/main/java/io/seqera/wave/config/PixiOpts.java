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
 * Configuration options for Pixi-based container builds.
 * <p>
 * Pixi is a package manager that supports conda packages. This class holds
 * the configuration needed to build containers using Pixi, including the
 * builder image, base image, packages, and custom commands.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class PixiOpts {

    /**
     * Default Pixi builder image used for installing packages.
     */
    final public static String DEFAULT_PIXI_IMAGE = "public.cr.seqera.io/wave/pixi:0.61.0-noble";

    /**
     * Default base image for the final container.
     */
    final public static String DEFAULT_BASE_IMAGE = "ubuntu:24.04";

    /**
     * Default packages to include in every Pixi-based container build.
     */
    final public static String DEFAULT_PACKAGES = "conda-forge::procps-ng";

    /**
     * The Pixi builder image used to install packages.
     */
    public String pixiImage;

    /**
     * Custom commands to execute during the container build.
     */
    public List<String> commands;

    /**
     * Base packages to include in addition to user-specified packages.
     */
    public String basePackages;

    /**
     * The base image for the final container.
     */
    public String baseImage;

    /**
     * Creates a new instance with default values.
     */
    public PixiOpts() {
        this(Map.of());
    }

    /**
     * Creates a new instance from a map of options.
     *
     * @param opts A map containing configuration options. Supported keys:
     *             {@code pixiImage}, {@code baseImage}, {@code commands}, {@code basePackages}
     */
    public PixiOpts(Map<String,?> opts) {
        this.pixiImage = opts.containsKey("pixiImage") ? opts.get("pixiImage").toString(): DEFAULT_PIXI_IMAGE;
        this.baseImage = opts.containsKey("baseImage") ? opts.get("baseImage").toString(): DEFAULT_BASE_IMAGE;
        this.commands = opts.containsKey("commands") ? (List<String>)opts.get("commands") : null;
        this.basePackages = opts.containsKey("basePackages") ? (String)opts.get("basePackages") : DEFAULT_PACKAGES;
    }

    public PixiOpts withPixiImage(String value) {
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

    public PixiOpts withBaseImage(String value) {
        this.baseImage = value;
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
        PixiOpts pixiOpts = (PixiOpts) object;
        return Objects.equals(pixiImage, pixiOpts.pixiImage)
                && Objects.equals(commands, pixiOpts.commands)
                && Objects.equals(basePackages, pixiOpts.basePackages)
                && Objects.equals(baseImage, pixiOpts.baseImage)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pixiImage, commands, basePackages, baseImage);
    }

}
