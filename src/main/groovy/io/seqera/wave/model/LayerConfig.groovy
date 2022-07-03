package io.seqera.wave.model

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer

/**
 * Model the settings to be injected into the pulled container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Deprecated
class LayerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir
    LayerMeta append

    /**
     * Convert layer layer config format to new {@link io.seqera.wave.api.ContainerConfig}
     *
     * @param path The location path of the layer config file
     * @return An instance of {@link io.seqera.wave.api.ContainerConfig}
     */
    @Memoized
    static ContainerConfig containerConfigAdapter(Path path) {
        log.debug "Layer config path: $path"
        final layerConfigPath = path.toAbsolutePath()
        if( !Files.exists(layerConfigPath) ) {
            throw new IllegalArgumentException("Specific config path does not exist: $layerConfigPath")
        }

        final layerConfig = createConfig(layerConfigPath)
        final layers = new ArrayList<ContainerLayer>(1)
        if( layerConfig.append )
            layers.add(layerConfig.append.toNewLayer())

        final ContainerConfig result = ContainerConfig
                .builder()
                .env(layerConfig.env)
                .cmd(layerConfig.cmd)
                .entrypoint(layerConfig.entrypoint)
                .workingDir(layerConfig.workingDir)
                .layers(layers)
                .build()
        return result
    }

    static protected LayerConfig createConfig(Path path) {
        final layerConfig = new JsonSlurper().parse(path) as LayerConfig
        if( !layerConfig.append?.getLocationPath() )
            throw new IllegalArgumentException("Missing layer tar path")
        if( !layerConfig.append?.gzipDigest )
            throw new IllegalArgumentException("Missing layer gzip digest")
        if( !layerConfig.append?.tarDigest )
            throw new IllegalArgumentException("Missing layer tar digest")

        if( !layerConfig.append.gzipDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer gzip digest should start with the 'sha256:' prefix -- offending value: $layerConfig.append.gzipDigest")
        if( !layerConfig.append.tarDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer tar digest should start with the 'sha256:' prefix -- offending value: $layerConfig.append.tarDigest")

        final base = path.parent.toAbsolutePath()
        final tarFile = layerConfig.append.withBase(base).getLocationPath()
        if( !Files.exists(tarFile) )
            throw new IllegalArgumentException("Missing layer tar file: $tarFile")

        log.debug "Layer info: path=$tarFile; gzip-checksum=$layerConfig.append.gzipDigest; gzip-size: $layerConfig.append.gzipSize; tar-checksum=$layerConfig.append.tarDigest"
        return layerConfig
    }
}
