/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.builder

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.tower.User
import io.seqera.wave.util.Escape
import io.seqera.wave.util.TarUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
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

    protected String createBuildFile(SubmitContainerTokenRequest req, User user) {
        assert req.freeze, "Not a freeze container request"

        // create a build container file for the provider image,
        // and append the container config when provided
        if( req.containerImage && !req.containerFile ) {
            def containerFile = "# wave generated container file\n"
            containerFile += createContainerFile(req)
            containerFile = appendEntrypoint(containerFile, req, user)
            return appendConfigToContainerFile(containerFile, req)
        }
        // append the container config to the provided build container file
        else if( !req.containerImage && req.containerFile && req.containerConfig ) {
            def containerFile = new String(req.containerFile.decodeBase64()) + '\n'
            containerFile += "# wave generated container file\n"
            containerFile = appendEntrypoint(containerFile, req, user)
            return appendConfigToContainerFile(containerFile, req)
        }

        // nothing to do
        return null
    }

    protected String appendEntrypoint(String containerFile, SubmitContainerTokenRequest req, User user) {
        // get the container manifest
        final entry = inspectService.containerEntrypoint(containerFile, user?.id, req.towerWorkspaceId, req.towerAccessToken, req.towerEndpoint)
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
    SubmitContainerTokenRequest freezeBuildRequest(final SubmitContainerTokenRequest req, User user) {
        final containerFile = createBuildFile(req, user)
        return containerFile
                ? req.copyWith(containerFile: containerFile.bytes.encodeBase64().toString())
                : req
    }

    static private String layerName(ContainerLayer layer) {
        return "layer-${layer.gzipDigest.replace(/sha256:/,'')}.tar.gz"
    }

    static private String layerDir(ContainerLayer layer) {
        return layerName(layer).replace(/.tar.gz/,'')
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

    static protected void saveLayersToContext(BuildRequest req, Path contextDir) {
        if(req.formatDocker()) {
            saveLayersToDockerContext0(req.containerConfig, contextDir)
        }
        else if(req.formatSingularity()) {
            saveLayersToSingularityContext0(req.containerConfig, contextDir)
        }
        else
            throw new IllegalArgumentException("Unknown container format: $req.format")
    }

    static protected void saveLayersToDockerContext0(ContainerConfig config, Path contextDir) {
        final layers = config.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerName(it))
            // copy the layer to the build context
            try (InputStream stream = ContentReaderFactory.of(it.location).openStream()) {
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    static protected void saveLayersToSingularityContext0(ContainerConfig config, Path contextDir) {
        final layers = config.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerDir(it))
            try { Files.createDirectory(target) }
            catch (FileAlreadyExistsException e) { /* ignore */ }
            // copy the layer to the build context
            try (InputStream stream = ContentReaderFactory.of(it.location).openStream()) {
                TarUtils.untarGzip(stream, target)
            }
        }
    }

    static protected void saveBuildContext(BuildContext buildContext, Path contextDir) {
        // copy the layer to the build context
        try (InputStream stream = ContentReaderFactory.of(buildContext.location).openStream()) {
            TarUtils.untarGzip(stream, contextDir)
        }
    }
}
