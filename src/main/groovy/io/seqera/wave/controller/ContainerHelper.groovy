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
import groovy.util.logging.Slf4j
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.SpackOpts
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.token.TokenData
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
@Slf4j
@CompileStatic
@PackageScope
class ContainerHelper {

    /**
     * Create a Containerfile from the specified packages specification
     *
     * @param spec
     *      A {@link PackagesSpec} object modelling the packages to be included in the resulting container
     * @param formatSingularity
     *      When {@code false} creates a Dockerfile, when {@code true} creates a Singularity file
     * @return
     *      The corresponding Containerfile
     */
    static String containerFileFromPackages(PackagesSpec spec, boolean formatSingularity) {
        if( spec.type == PackagesSpec.Type.CONDA ) {
            final lockFile = condaLockFile(spec.entries)
            if( !spec.condaOpts )
                spec.condaOpts = new CondaOpts()
            def result
            if ( lockFile ) {
                result = formatSingularity
                        ? condaPackagesToSingularityFile(lockFile, spec.channels, spec.condaOpts)
                        : condaPackagesToDockerFile(lockFile, spec.channels, spec.condaOpts)
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

    static String condaFileFromRequest(SubmitContainerTokenRequest req) {
        if( !req.packages )
            return decodeBase64OrFail(req.condaFile, 'condaFile')

        if( req.packages.type != PackagesSpec.Type.CONDA )
            return null

        if( req.packages.environment ) {
            // parse the attribute as a conda file path *and* append the base packages if any
            // note 'channel' is null, because they are expected to be provided in the conda file
            final decoded = decodeBase64OrFail(req.packages.environment, 'packages.envFile')
            return condaEnvironmentToCondaYaml(decoded, req.packages.channels)
        }

        if ( req.packages.entries && !condaLockFile(req.packages.entries)) {
            // create a minimal conda file with package spec from user input
            final String packages = req.packages.entries.join(' ')
            return condaPackagesToCondaYaml(packages, req.packages.channels)
        }

        return null;
    }

    static protected String condaLockFile(List<String> condaPackages) {
        if( !condaPackages )
            return null;
        final result = condaPackages .findAll(it->it.startsWith("http://") || it.startsWith("https://"))
        if( !result )
            return null;
        if( condaPackages.size()>1 ) {
            throw new IllegalArgumentException("No more than one Conda lock remote file can be specified at the same time");
        }
        return result[0]
    }

    static String spackFileFromRequest(SubmitContainerTokenRequest req) {
        if( !req.packages )
            return decodeBase64OrFail(req.spackFile,'spackFile')

        if( req.packages.type != PackagesSpec.Type.SPACK )
            return null

        if( req.packages.environment ) {
            final decoded = decodeBase64OrFail(req.packages.environment,'packages.envFile')
            return addPackagesToSpackYaml(decoded, req.packages.spackOpts)
        }

        if( req.packages.entries ) {
            final String packages = req.packages.entries.join(' ')
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

    static SubmitContainerTokenResponse makeResponseV1(ContainerRequestData data, TokenData token, String waveImage) {
        final target = waveImage
        final build = data.buildNew ? data.buildId : null
        return new SubmitContainerTokenResponse(token.value, target, token.expiration, data.containerImage, build, null, null)
    }

    static SubmitContainerTokenResponse makeResponseV2(ContainerRequestData data, TokenData token, String waveImage) {
        final target = data.freeze ? data.containerImage : waveImage
        final build = data.buildId
        final Boolean cached = !data.buildNew
        final expiration = !data.freeze ? token.expiration : null
        final tokenId = !data.freeze ? token.value : null
        return new SubmitContainerTokenResponse(tokenId, target, expiration, data.containerImage, build, cached, data.freeze)
    }

    static String patchPlatformEndpoint(String endpoint) {
        // api.stage-tower.net --> api.cloud.stage-seqera.io
        // api.tower.nf --> api.cloud.seqera.io
        final result = endpoint
                .replace('/api.stage-tower.net','/api.cloud.stage-seqera.io')
                .replace('/api.tower.nf','/api.cloud.seqera.io')
        if( result != endpoint )
            log.debug "Patched Platform endpoint: '$endpoint' with '$result'"
        return result
    }
}
