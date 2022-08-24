package io.seqera.wave.util

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
@Slf4j
@CompileStatic
class ContainerConfigFactory {

    @Memoized
    ContainerConfig from(Path path) {
        log.debug "ContainerConfig from path: $path"
        final layerConfigPath = path.toAbsolutePath()
        if( !Files.exists(layerConfigPath) ) {
            throw new IllegalArgumentException("Specific config path does not exist: $layerConfigPath")
        }
        ContainerConfig ret = parse(path.text)
        ret.withBase(path.parent.toAbsolutePath())
        ret
    }

    @Memoized
    ContainerConfig from(String text) {
        log.debug "ContainerConfig from inputStream"
        parse(text)
    }


    protected ContainerConfig parse(String text){
        final json = new JsonSlurper().parseText(text) as Map<String, Object>
        final ContainerConfig containerConfig = new ContainerConfig()
        for( obj in json.entrySet()){
            if( containerConfig.properties.containsKey(obj.key)){
                containerConfig.setProperty(obj.key, obj.value)
            }
        }
        if( json.containsKey('append') ){
            final jsonAppend = json['append'] as Map<String, Object>
            final ContainerLayer layer = new ContainerLayer()
            for( obj in jsonAppend.entrySet()){
                if( layer.properties.containsKey(obj.key)){
                    layer.setProperty(obj.key, obj.value)
                }
            }
            containerConfig.layers.add layer
        }
        containerConfig.validate()
        log.debug "Layer info: $containerConfig"
        containerConfig
    }
}
