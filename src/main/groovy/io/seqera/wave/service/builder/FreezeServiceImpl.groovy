package io.seqera.wave.service.builder

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
            containerFile += "FROM $req.containerImage\n"
            containerFile = appendEntrypoint(containerFile, req, user)
            return appendConfigToDockerFile(containerFile, req.containerConfig)
        }
        // append the container config to the provided build container file
        else if( !req.containerImage && req.containerFile && req.containerConfig ) {
            def containerFile = new String(req.containerFile.decodeBase64()) + '\n'
            containerFile += "# wave generated container file\n"
            containerFile = appendEntrypoint(containerFile, req, user)
            return appendConfigToDockerFile(containerFile, req.containerConfig)
        }

        // nothing to do
        return null
    }

    protected String appendEntrypoint(String containerFile, SubmitContainerTokenRequest req, User user) {
        // get the container manifest
        final entry = inspectService.containerEntrypoint(containerFile, user?.id, req.towerWorkspaceId, req.towerAccessToken, req.towerEndpoint)
        if( entry ) {
            return containerFile + "ENV WAVE_ENTRY_CHAIN=\"${entry.join(' ')}\"\n"
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

    static protected String appendConfigToDockerFile(final String dockerFile, final ContainerConfig containerConfig) {
        assert dockerFile, "Argument dockerFile cannot empty"

        if( !containerConfig )
            return dockerFile

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

        if( !dockerFile.endsWith('\n') )
            result = '\n' + result
        return dockerFile + result
    }

    static protected void saveLayersToContext(ContainerConfig config, Path contextDir) {
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

    static protected void saveBuildContext(BuildContext buildContext, Path contextDir) {
        final target = contextDir.resolve("context.tar.gz")
        // copy the layer to the build context
        try (InputStream stream = ContentReaderFactory.of(buildContext.location).openStream()) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
