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

package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.SpackOpts
import io.seqera.wave.exception.BadRequestException
import static io.seqera.wave.util.Checkers.isEmpty
import static io.seqera.wave.util.CondaHelper.condaLock
import static io.seqera.wave.util.DockerHelper.addPackagesToSpackYaml
import static io.seqera.wave.util.DockerHelper.condaEnvironmentToCondaYaml
import static io.seqera.wave.util.DockerHelper.condaFileToDockerFile
import static io.seqera.wave.util.DockerHelper.condaFileToSingularityFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToCondaYaml
import static io.seqera.wave.util.DockerHelper.condaPackagesToDockerFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToSingularityFile
import static io.seqera.wave.util.DockerHelper.spackFileToDockerFile
import static io.seqera.wave.util.DockerHelper.spackFileToSingularityFile
import static io.seqera.wave.util.DockerHelper.spackPackagesToSpackYaml
/**
 * Container helper methods
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@PackageScope
class ContainerHelper {

    static String createContainerFile(PackagesSpec spec, boolean formatSingularity) {
        if( spec.type == PackagesSpec.Type.CONDA ) {
            final String lock = condaLock(spec.packages)
            if( !spec.condaOpts )
                spec.condaOpts = new CondaOpts()
            def result
            if ( !isEmpty(lock) ) {
                result = formatSingularity
                        ? condaPackagesToSingularityFile(lock, spec.channels, spec.condaOpts)
                        : condaPackagesToDockerFile(lock, spec.channels, spec.condaOpts)
            } else {
                result = formatSingularity
                        ? condaFileToSingularityFile(spec.condaOpts)
                        : condaFileToDockerFile(spec.condaOpts)
            }
            return result
        }

        if( spec.type == PackagesSpec.Type.SPACK ) {
            if( !spec.spackOpts )
                spec.spackOpts = new SpackOpts()
            final result = formatSingularity
                        ? spackFileToSingularityFile(spec.spackOpts)
                        : spackFileToDockerFile(spec.spackOpts)
            return result
        }

        throw new IllegalArgumentException("Unexpected packages spec type: $spec.type")
    }

    static String condaFile0(SubmitContainerTokenRequest req) {
        if( !req.packages )
            return decodeBase64OrFail(req.condaFile, 'condaFile')

        if( req.packages.type != PackagesSpec.Type.CONDA )
            return null

        if( req.packages.envFile ) {
            // parse the attribute as a conda file path *and* append the base packages if any
            // note 'channel' is null, because they are expected to be provided in the conda file
            final decoded = decodeBase64OrFail(req.packages.envFile, 'packages.envFile')
            return condaEnvironmentToCondaYaml(decoded, req.packages.channels)
        }

        if ( req.packages.packages && !condaLock0(req.packages.packages)) {
            // create a minimal conda file with package spec from user input
            final String packages = req.packages.packages.join(' ')
            return condaPackagesToCondaYaml(packages, req.packages.channels)
        }

        return null;
    }

    static String condaLock0(List<String> condaPackages) {
        if( !condaPackages )
            return null;
        Optional<String> result = condaPackages
                .stream()
                .filter(it->it.startsWith("http://") || it.startsWith("https://"))
                .findFirst();
        if( !result.isPresent() )
            return null;
        if( condaPackages.size()!=1 ) {
            throw new IllegalArgumentException("No more than one Conda lock remote file can be specified at the same time");
        }
        return result.get();
    }

    static String spackFile0(SubmitContainerTokenRequest req) {
        if( !req.packages )
            return decodeBase64OrFail(req.spackFile,'spackFile')

        if( req.packages.type != PackagesSpec.Type.SPACK )
            return null

        if( req.packages.envFile ) {
            final decoded = decodeBase64OrFail(req.packages.envFile,'packages.envFile')
            return addPackagesToSpackYaml(decoded, req.packages.spackOpts)
        }

        if( req.packages.packages ) {
            final String packages = req.packages.packages.join(' ')
            return spackPackagesToSpackYaml(packages, req.packages.spackOpts)
        }

        return null
    }

    static String decodeBase64OrFail(String value, String field) {
        if( !value )
            return null
        try {
            final bytes = Base64.getDecoder().decode(value)
            final check = Base64.getEncoder().encodeToString(bytes)
            if( value!=check )
                throw new IllegalArgumentException("Not a valid base64 encoded string")
            return new String(bytes)
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid '$field' attribute - make sure it encoded as a base64 string", e)
        }
    }
}