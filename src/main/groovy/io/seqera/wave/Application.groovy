package io.seqera.wave

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.Micronaut
import io.seqera.wave.util.BuildInfo
import io.seqera.wave.util.RuntimeInfo
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License

/**
 * Registry app launcher
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Wave",
                version = '${api.version}',
                description = "Wave API",
                license = @License(name = "Apache 2.0", url = "https://XXXXXXXX")
        )
)
@CompileStatic
@Slf4j
class Application {

    static void main(String[] args) {
        log.info( "Starting ${BuildInfo.name} - version: ${BuildInfo.fullVersion} - ${RuntimeInfo.info('; ')}" )
        setupConfig()
        Micronaut.build(args)
                .banner(false)
                .eagerInitSingletons(true)
                .mainClass(Application.class)
                .start();
    }


    static void setupConfig() {
        // config file
        def configFile = 'config.yml'
        if( System.getenv('WAVE_CONFIG_FILE') ) {
            configFile = System.getenv('WAVE_CONFIG_FILE')
            log.info "Detected WAVE_CONFIG_FILE variable: ${configFile}"
        }
        System.setProperty('micronaut.config.files', "classpath:application.yml,file:$configFile")

        // detected layer path
        if( System.getenv('WAVE_LAYER_PATH') ) {
            def layerPath = System.getenv('WAVE_LAYER_PATH')
            log.info "Detected WAVE_LAYER_PATH variable: ${layerPath}"
            System.setProperty('wave.layerPath', layerPath)
        }

    }
}
