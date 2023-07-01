package io.seqera.wave.service.builder


import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.storage.reader.ContentReaderFactory
/**
 * Implements helper methods to handle container build context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class BuildHelper {

    static String createBuildFile(SubmitContainerTokenRequest req) {
        assert req.freeze, "Not a freeze container request"

        // create a build container file for the provider image,
        // and append the container config when provided
        if( req.containerImage && !req.containerFile ) {
            def containerFile = "# wave generated container file\n"
            containerFile += "FROM $req.containerImage\n"
            return appendConfigToDockerFile(containerFile, req.containerConfig)
        }
        // append the container config to the provided build container file
        else if( !req.containerImage && req.containerFile && req.containerConfig ) {
            def containerFile = req.containerFile + '\n'
            containerFile += "# wave generated container file\n"
            return appendConfigToDockerFile(containerFile, req.containerConfig)
        }

        // nothing to do
        return null
    }

    static SubmitContainerTokenRequest createBuildRequest(final SubmitContainerTokenRequest req) {
        final containerFile = createBuildFile(req)
        return containerFile
                ? req.copyWith(containerFile: containerFile.bytes.encodeBase64().toString())
                : req
    }

    static private String layerName(ContainerLayer layer) {
        return "layer-${layer.gzipDigest.replace(/sha256:/,'')}.tar.gz"
    }

    static String appendConfigToDockerFile(final String dockerFile, final ContainerConfig containerConfig) {
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

    static void saveLayersToContext(ContainerConfig config, Path contextDir) {
        final layers = config.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerName(it))
            // copy the layer to the build context
            try (InputStream stream = ContentReaderFactory.of(it.location).openStream()) {
                Files.copy(stream, target)
            }
        }
    }
}
