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
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.exception.BadRequestException

import static TemplateUtils.condaFileToDockerFile
import static TemplateUtils.condaFileToDockerFileUsingV2
import static TemplateUtils.condaFileToSingularityFile
import static TemplateUtils.condaFileToSingularityFileV2
import static TemplateUtils.condaPackagesToDockerFile
import static TemplateUtils.condaPackagesToDockerFileUsingV2
import static TemplateUtils.condaPackagesToSingularityFile
import static TemplateUtils.condaPackagesToSingularityFileV2

/**
 * Helper class for Conda/Micromamba container builds.
 * Supports both legacy v1 template and the newer v2 (MICROMAMBA_V2) template.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class CondaHelper {

    /**
     * Generate a container file (Dockerfile or Singularity) for Conda packages
     * using the legacy micromamba v1 template.
     *
     * @param spec The packages specification
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     */
    static String containerFile(PackagesSpec spec, boolean singularity) {
        final lockFileUri = tryGetLockFile(spec.entries)
        if( !spec.condaOpts )
            spec.condaOpts = new CondaOpts()

        if( lockFileUri ) {
            // Lock file URI detected: use templates that download from remote lock file
            return singularity
                    ? condaPackagesToSingularityFile(lockFileUri, spec.channels, spec.condaOpts)
                    : condaPackagesToDockerFile(lockFileUri, spec.channels, spec.condaOpts)
        }
        else {
            // No lock file: use templates that install from local conda.yml
            return singularity
                    ? condaFileToSingularityFile(spec.condaOpts)
                    : condaFileToDockerFile(spec.condaOpts)
        }
    }

    /**
     * Generate a container file (Dockerfile or Singularity) using the Micromamba v2 template.
     * Only supports CONDA package type. Supports both lock files and environment files.
     *
     * @param spec The packages specification (must be CONDA type)
     * @param containerImage Optional base container image override
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     * @throws BadRequestException if package type is not CONDA
     */
    static String containerFileV2(PackagesSpec spec, String containerImage, boolean singularity) {
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'conda/micromamba:v2' build template")
        }

        final lockFileUri = tryGetLockFile(spec.entries)
        final opts = spec.condaOpts ?: CondaOpts.v2()
        if( containerImage )
            opts.baseImage = containerImage

        if( lockFileUri ) {
            // use the lock file uri as special package name
            return singularity
                    ? condaPackagesToSingularityFileV2(lockFileUri, spec.channels, opts)
                    : condaPackagesToDockerFileUsingV2(lockFileUri, spec.channels, opts)
        }
        else {
            // No lock file: use templates that install from local conda.yml
            return singularity
                    ? condaFileToSingularityFileV2(opts)
                    : condaFileToDockerFileUsingV2(opts)
        }
    }

    /**
     * Detects if the list of package names contains a conda lock file URI.
     * A lock file is identified by an HTTP/HTTPS URL in the package list.
     * Only one lock file URI is allowed at a time.
     *
     * @param entries List of package names or URIs
     * @return The lock file URI if found, null otherwise
     * @throws IllegalArgumentException if more than one lock file URI is specified
     */
    static String tryGetLockFile(List<String> entries) {
        if( !entries )
            return null
        final result = entries.findAll(it -> it.startsWith("http://") || it.startsWith("https://"))
        if( !result )
            return null
        if( entries.size() > 1 ) {
            throw new IllegalArgumentException("No more than one Conda lock remote file can be specified at the same time")
        }
        return result[0]
    }
}
