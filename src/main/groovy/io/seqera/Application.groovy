package io.seqera

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.Micronaut
import io.seqera.config.RegistryBean
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
        log.info(RuntimeInfo.info('; '))

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
}
