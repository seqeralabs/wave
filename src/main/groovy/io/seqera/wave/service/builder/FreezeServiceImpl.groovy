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

package io.seqera.wave.service.builder

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.Escape
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.RegHelper.layerDir
import static io.seqera.wave.util.RegHelper.layerName
/**
 * Implements helper methods to handle container build context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class FreezeServiceImpl implements FreezeService {

    @Inject
    private ContainerInspectService inspectService

    protected String createBuildFile(SubmitContainerTokenRequest req, PlatformId identity) {
        assert req.freeze, "Not a freeze container request"

        // create a build container file for the provider image,
        // and append the container config when provided
        if( req.containerImage && !req.containerFile ) {
            def containerFile = "# wave generated container file\n"
            containerFile += createContainerFile(req)
            containerFile = appendEntrypoint(containerFile, req, identity)
            return appendConfigToContainerFile(containerFile, req)
        }
        // append the container config to the provided build container file
        else if( !req.containerImage && req.containerFile && req.containerConfig ) {
            def containerFile = new String(req.containerFile.decodeBase64()) + '\n'
            containerFile += "# wave generated container file\n"
            containerFile = appendEntrypoint(containerFile, req, identity)
            return appendConfigToContainerFile(containerFile, req)
        }

        // nothing to do
        return null
    }

    protected String appendEntrypoint(String containerFile, SubmitContainerTokenRequest req, PlatformId identity) {
        // get the container manifest
        final entry = inspectService.containerEntrypoint(containerFile, identity)
        if( entry ) {
            if( req.formatSingularity() ) {
                return containerFile + "%environment\n  export WAVE_ENTRY_CHAIN=\"${entry.join(' ')}\"\n"
            } else {
                return containerFile + "ENV WAVE_ENTRY_CHAIN=\"${entry.join(' ')}\"\n"
            }
        }
        else
            return containerFile
    }

    @Override
    SubmitContainerTokenRequest freezeBuildRequest(final SubmitContainerTokenRequest req, PlatformId identity) {
        final containerFile = createBuildFile(req, identity)
        return containerFile
                ? req.copyWith(containerFile: containerFile.bytes.encodeBase64().toString())
                : req
    }

    static protected String createContainerFile(SubmitContainerTokenRequest req) {
        if( req.formatSingularity() ) {
            return """\
            BootStrap: docker
            From: ${req.containerImage}
            """.stripIndent()
        }
        else {
            return "FROM ${req.containerImage}\n"
        }
    }

    static protected String appendConfigToContainerFile(final String containerFile, SubmitContainerTokenRequest req) {
        assert containerFile, "Argument containerFile cannot empty"

        if( !req.containerConfig )
            return containerFile

        def result = req.formatSingularity()
                ? appendSingularityConfig0(req.containerConfig)
                : appendDockerConfig0(req.containerConfig)

        if( !containerFile.endsWith('\n') )
            result = '\n' + result
        return containerFile + result
    }

    /**
     * Instrument the singularity file to honour the {@link ContainerConfig} object
     * when possible
     *
     * https://docs.sylabs.io/guides/latest/user-guide/definition_files.html
     * 
     * @param containerConfig
     * @return Singularity file snippet to be appended to the original singularity file
     */
    static protected String appendSingularityConfig0(ContainerConfig containerConfig) {
        String result = ''
        // add layers
        final layers = containerConfig.layers
        if( layers ) {
            result += '%files\n'
            for(int i=0; i<layers.size(); i++) {
                result += "  {{wave_context_dir}}/${layerDir(layers[i])}/* /\n"
            }
        }

        // add ENV
        if( containerConfig.env ) {
            result += '%environment\n'
            result += "  export ${containerConfig.env.join(' ')}\n"
        }
        // add ENTRY
        if( containerConfig.entrypoint ) {
            result += '%runscript\n'
            result += "  ${Escape.cli(containerConfig.entrypoint)}\n"
        }

        // config work dir is not supported
        // config command is not supported

        return result
    }

    static protected String appendDockerConfig0(ContainerConfig containerConfig) {
        String result = ''
        // add layers
        final layers = containerConfig.layers
        for(int i=0; i<layers.size(); i++) {
            result += "ADD ${layerName(layers[i])} /\n"
        }
        // add work dir
        if( containerConfig.workingDir ) {
            result += "WORKDIR ${containerConfig.workingDir}\n"
        }
        // add ENV
        if( containerConfig.env ) {
            result += "ENV ${containerConfig.env.join(' ')}\n"
        }
        // add ENTRY
        if( containerConfig.entrypoint ) {
            result += "ENTRYPOINT ${containerConfig.entrypoint.collect(it->"\"$it\"")}\n"
        }
        // add CMD
        if( containerConfig.cmd ) {
            result += "CMD ${containerConfig.cmd.collect(it->"\"$it\"")}\n"
        }
        return result
    }

}
