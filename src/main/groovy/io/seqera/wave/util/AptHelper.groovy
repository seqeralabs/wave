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

package io.seqera.wave.util

import groovy.transform.CompileStatic
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.config.AptOpts
import io.seqera.wave.exception.BadRequestException

/**
 * Helper class to create Dockerfile for APT/Debian package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class AptHelper {

    /**
     * Generate a container file (Dockerfile or Singularity) for APT packages.
     * This is the main entry point for APT_DEBIAN_V1 template builds.
     *
     * @param spec The packages specification (must be APT type)
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     * @throws BadRequestException if package type is not APT
     */
    static String containerFile(PackagesSpec spec, boolean singularity) {
        if( spec.type != PackagesSpec.Type.APT ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'apt/debian:v1' build template")
        }

        if( !spec.aptOpts )
            spec.aptOpts = new AptOpts()

        // Get packages from entries or environment
        List<String> packageList
        if( spec.entries ) {
            packageList = spec.entries
        }
        else if( spec.environment ) {
            final decoded = ContainerHelper.decodeBase64OrFail(spec.environment, 'packages.environment')
            packageList = parseEnvironmentFile(decoded)
        }
        else {
            throw new BadRequestException("APT packages require either 'entries' or 'environment' field")
        }

        if( packageList.isEmpty() ) {
            throw new BadRequestException("APT package list cannot be empty")
        }

        final String packages = packageList.join(' ')
        return singularity
                ? aptPackagesToSingularityFile(packages, spec.aptOpts)
                : aptPackagesToDockerFile(packages, spec.aptOpts)
    }

    /**
     * Parse environment file content into list of packages.
     * Format: one package per line, comments start with #, empty lines ignored.
     *
     * @param content The environment file content (newline-separated packages)
     * @return List of package names
     */
    static List<String> parseEnvironmentFile(String content) {
        if( !content )
            return []

        return content.split('\n')
                .collect { it.trim() }
                .findAll { it && !it.startsWith('#') }
    }

    /**
     * Generate Dockerfile for APT packages
     */
    static String aptPackagesToDockerFile(String packages, AptOpts opts) {
        return aptPackagesTemplate0(
                '/templates/apt-debian-v1/dockerfile-apt-packages.txt',
                packages,
                opts)
    }

    /**
     * Generate Singularity file for APT packages
     */
    static String aptPackagesToSingularityFile(String packages, AptOpts opts) {
        return aptPackagesTemplate0(
                '/templates/apt-debian-v1/singularityfile-apt-packages.txt',
                packages,
                opts)
    }

    protected static String aptPackagesTemplate0(String template, String packages, AptOpts opts) {
        final boolean singularity = template.contains('/singularityfile')
        final String baseImage = opts.baseImage ?: AptOpts.DEFAULT_BASE_IMAGE
        final String basePackages = opts.basePackages ?: AptOpts.DEFAULT_BASE_PACKAGES

        final Map<String, String> binding = [:]
        binding.put('base_image', baseImage)
        binding.put('base_packages', basePackages)
        binding.put('target', packages)

        final String result = renderTemplate0(template, binding)
        return addCommands(result, opts.commands, singularity)
    }

    private static String renderTemplate0(String templatePath, Map<String, String> binding) {
        final URL template = AptHelper.class.getResource(templatePath)
        if( template == null )
            throw new IllegalStateException("Unable to load template '${templatePath}' from classpath")
        try {
            final InputStream reader = template.openStream()
            return new TemplateRenderer().render(reader, binding)
        }
        catch( IOException e ) {
            throw new IllegalStateException("Unable to read classpath template '${templatePath}'", e)
        }
    }

    private static String addCommands(String result, List<String> commands, boolean singularity) {
        if( commands == null || commands.isEmpty() )
            return result
        if( singularity )
            result += '%post\n'
        for( String cmd : commands ) {
            if( singularity ) {
                result += '    ' + cmd + '\n'
            }
            else {
                result += 'RUN ' + cmd + '\n'
            }
        }
        return result
    }
}
