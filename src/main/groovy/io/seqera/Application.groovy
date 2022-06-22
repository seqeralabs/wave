package io.seqera

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.Micronaut
import io.seqera.config.RegistryBean
import io.seqera.util.BuildInfo
import io.seqera.util.RuntimeInfo
import jakarta.inject.Inject

/**
 * Registry app launcher
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class Application implements ApplicationEventListener<StartupEvent>{
    static void main(String[] args) {
        log.info( "Starting ${BuildInfo.name} - version: ${BuildInfo.fullVersion} - ${RuntimeInfo.info('; ')}" )
        setupConfig()
        Micronaut.build(args)
                .banner(false)
                .eagerInitSingletons(true)
                .mainClass(Application.class)
                .start();
    }

    @Inject
    ApplicationContext ctx

    @Override
    void onApplicationEvent(StartupEvent event) {
        ctx.getBeansOfType(RegistryBean).each{registryBean ->
            log.info "$registryBean.name configuration = $registryBean"
        }
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
            System.setProperty('towerreg.layerPath', layerPath)
        }

    }
}
